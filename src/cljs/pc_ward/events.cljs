(ns pc-ward.events
  (:require
    [re-frame.core :as re-frame]
    [day8.re-frame.http-fx]
    [ajax.core :as ajax]
    [pc-ward.db :as db]
    [pc-ward.util :as util]
    [clojure.string :as string]
    [day8.re-frame.tracing :refer-macros [fn-traced]]
    ))


(defonce timeouts
         (atom {}))

(re-frame/reg-fx :dispatch-debounce
        (fn [[id event-vec n]]
          (js/clearTimeout (@timeouts id))
          (swap! timeouts assoc id
                 (js/setTimeout (fn []
                                  (re-frame/dispatch event-vec)
                                  (swap! timeouts dissoc id))
                                n))))

(re-frame/reg-fx :stop-debounce
        (fn [id]
          (js/clearTimeout (@timeouts id))
          (swap! timeouts dissoc id)))


(re-frame/reg-event-db
  ::initialize-db
  []
  (fn [_ _]
    db/default-db))

(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (re-frame/dispatch [:timer now])))                      ;; <-- dispatch used

;; call the dispatching function every second
(defonce do-timer (js/setInterval dispatch-timer-event 1000))

(re-frame/reg-event-db                                      ;; usage:  (rf/dispatch [:timer a-js-Date])
  :timer
  (fn [db [_ new-time]]                                     ;; <-- notice how we de-structure the event vector
    (assoc db :current-time new-time)))


(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

;; :user/attempt-login is an event triggered by a login attempt
;; this simply changes the name at the moment - it is fake and does no network calling
;; the handler is (db event) -> db; the event is a vector of values so we destructure
;; ignoring the first value because its the message id, which we already know
(re-frame/reg-event-db
  :user/fake-login
  [(when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [db [_ namespace username password]]
    (if (or (string/blank? username) (string/blank? password) (not= password "password"))
      (do
        (assoc db :login-error "Invalid username or password"))
      (do
        (js/console.log "Attempting login for user" username)
        (-> db
            (dissoc :login-error)
            (assoc :name username :authenticated-user username))))))

;; user/user-login-start kicks off a login event, cascading to return an error, or
;; a login action, via an intermediary request for a service login if we don't have an active token
(re-frame/reg-event-fx
  :user/user-login-start
  [(when ^boolean goog.DEBUG re-frame.core/debug)]
  (fn [{db :db} [_ namespace username password]]            ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "attempting login " username)
    (if (or (string/blank? username) (string/blank? password))
      {:db (assoc db :login-error "Invalid username or password")}
      {:db         (assoc db :show-foreground-spinner true)
       :dispatch-n [[:user/foreground-spinner true]
                    [:user/service-login-start [:user/user-login-do namespace username password]]
                    [:user/foreground-spinner false]]}
      )))

(re-frame/reg-event-fx
  :user/user-login-do
  (fn [{db :db} [_ namespace username password]]
    (js/console.log "doing login " username)
    {:http-xhrio {:method          :post
                  :uri             "http://localhost:8080/v1/login"
                  :timeout         5000
                  :format          (ajax/json-request-format)
                  :headers         {:Authorization (str "Bearer " (:concierge-service-token db))}
                  :params          {
                                    :user     {
                                               :system "https://fhir.nhs.uk/Id/cymru-user-id"
                                               :value  username
                                               }
                                    :password password
                                    }
                  :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                  :on-success      [:user/user-login-success namespace username]
                  :on-failure      [:user/user-login-failure]}}))


(re-frame/reg-event-fx
  :snomed/search-later
  (fn [_ [_ id params]]
    {:dispatch-debounce [id [:snomed/search id params] 200]}))


(re-frame/reg-event-fx
  :snomed/search
  (fn [{db :db} [_ id {s :s is-a :is-a max-hits :max-hits}]]
    (let [clear-results {:db (-> db
                                 (update-in [:snomed id] dissoc :error)
                                 (update-in [:snomed id] dissoc :results)
                                 )}]
      (if (clojure.string/blank? s)
        clear-results
        (assoc clear-results :http-xhrio {:method          :get
                                          :uri             "http://localhost:8090/v1/snomed/search"
                                          :timeout         5000
                                          :format          (ajax/json-request-format)
                                          :params          {:s            s
                                                            :is_a         is-a
                                                            :maximum_hits max-hits}
                                          :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                                          :on-success      [:snomed/search-success id]
                                          :on-failure      [:snomed/search-failure id]})))))

(re-frame/reg-event-db
  :snomed/search-success
  (fn [db [_ id response]]
    (js/console.log "search result for " id ":" (:items response))
    (-> db
        (update-in [:snomed id] dissoc :error)
        (assoc-in [:snomed id :results] (:items response)))))

(re-frame/reg-event-db
  :snomed/search-failure
  (fn [db [id response]]
    (-> db
        (update-in [:snomed id] dissoc :results)
        (assoc-in [:snomed id :error] (:status-text response)))))

(re-frame/reg-event-db
  :user/foreground-spinner
  (fn [db [_ on]]
    (assoc db :show-foreground-spinner on)))

(re-frame/reg-event-fx
  :user/user-login-success
  (fn [{db :db} [_ namespace username response]]
    (js/console.log "User login success: response: " + response)
    {:db       (-> db
                   (dissoc :login-error)
                   (assoc :authenticated-user-token (:token response)))
     :dispatch [:concierge/resolve-identifier namespace username :user/set-authenticated-user :user/set-authenticated-user-failed]}))

(re-frame/reg-event-db
  :user/set-authenticated-user
  (fn [db [_ response]]
    (js/console.log "Fetched user... response: " response)
    (assoc db :authenticated-user response)
    ))

(re-frame/reg-event-db
  :user/set-authenticated-user-failed
  (fn [db [_ response]]
    (js/console.log "Fetched user... response: " response)
    (-> db
        (dissoc :name :authenticated-user-token)
        (assoc :login-error (:status-text response)))))

(re-frame/reg-event-db
  :user/user-login-failure
  (fn [db [_ response]]
    (js/console.log "User login failure: response: " + response)
    (assoc db :login-error (:status-text response))))




(re-frame/reg-event-fx
  :concierge/resolve-identifier
  (fn [{db :db} [_ system value on-success on-failure]]
    {:http-xhrio {:method          :get
                  :uri             (str "http://localhost:8080/v1/identifier/" value)
                  :timeout         5000
                  :format          (ajax/json-request-format)
                  :headers         {:Authorization (str "Bearer " (:authenticated-user-token db))}
                  :params          {:system system}
                  :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                  :on-success      [on-success]
                  :on-failure      [on-failure]}}))






(defn create-service-token-map
  [db next-event]
  {:db         (assoc db :show-background-spinner true)     ;; causes the twirly-waiting-dialog to show??
   :http-xhrio {:method          :post
                :uri             "http://localhost:8080/v1/login"
                :timeout         5000                       ;; optional see API docs
                :format          (ajax/json-request-format)
                :params          {
                                  :user     {
                                             :system "https://concierge.eldrix.com/Id/service-user"
                                             :value  "patientcare"
                                             }
                                  :password "Tly65k8XRdCCBnJy7KJRrWDgc2V9lO5j0lGLjQzrwFwkryuCJZaAZT5xtxiIU7CR"
                                  }
                :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                :on-success      [:user/service-login-success next-event]
                :on-failure      [:user/service-login-failure]}})

(defn refresh-service-token-map
  [db next-event]
  {:db         (assoc db :show-background-spinner true)     ;; causes the twirly-waiting-dialog to show??
   :http-xhrio {:method          :get
                :uri             "http://localhost:8080/v1/refresh"
                :headers         {:Authorization (str "Bearer " (:concierge-service-token db))}
                :timeout         5000                       ;; optional see API docs
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                :on-success      [:user/service-login-success next-event]
                :on-failure      [:user/service-login-refresh-failure]}})


;; :user/service-login-start is an event raised to obtain a new service token, or refresh
;; an existing token. In essence, this event can either be periodically triggered, or
;; triggered just-in-time, in order to ensure that we have a valid service token.
;; this event takes an optional additional event to trigger once it is complete
;; see https://github.com/day8/re-frame/blob/master/docs/Talking-To-Servers.md
;; user/service-login-start kicks off a service-user login request, optionally
;; invoking the "next-event" once successful
;; TODO: probably switch to an interceptor to annotate any events that need a service account token
(re-frame/reg-event-fx
  :user/service-login-start
  [(when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ next-event]]                             ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "service token needs refreshing? " (util/jwt-expires-in-seconds? (:concierge-service-token db) 120))
    (let [expires (util/jwt-expires-seconds (:concierge-service-token db))]
      (cond
        (> expires 120) (if-not (empty? next-event) {:dispatch next-event}) ; if we've more than 120 seconds on the clock, just keep using old token
        (> expires 30) (refresh-service-token-map db next-event) ; token is due to expire, so refresh token
        :else (create-service-token-map db next-event)))))  ; grab a new token using our service credentials

;; user/service-login-success simply processes the successful service login response
;; and stores the token received from concierge
(re-frame/reg-event-fx
  :user/service-login-success
  [(when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ next-event response]]
    (js/console.log "got token: " (:token response))
    {:db       (assoc db :concierge-service-token (:token response))
     :dispatch next-event
     }))

;; in the event of service account login token refresh failure,
;; try a new login
(re-frame/reg-event-fx
  :user/service-login-refresh-failure
  (fn [{db :db} [_ response]]
    {:db       (dissoc db :concierge-service-token)
     :dispatch :user/service-login-start}))

(re-frame/reg-event-db
  :user/service-login-failure
  (fn [{db :db} [_ response]]
    (js/console.log "bad http result : response " + response)
    {:db (-> db
             (assoc :error (:status-text response))
             (assoc :login-error (:status-text response)))}
    ))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
  :user/logout
  (fn-traced [db [_ user]]
             (js/console.log "Logging out user" user)
             (-> db
                 (dissoc :authenticated-user-token)
                 (dissoc :authenticated-user)
                 )
             ))