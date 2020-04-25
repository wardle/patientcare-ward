(ns pc-ward.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path after dispatch reg-fx]]
    [day8.re-frame.http-fx]                                 ;; required for its side-effects in registering a re-frame "effect"
    [ajax.core :as ajax]
    [cljs.spec.alpha :as s]
    [pc-ward.db :as db]
    [pc-ward.config :as config]
    [pc-ward.concierge :as concierge]
    [pc-ward.util :as util]
    [clojure.string :as string]))



(defn check-and-throw
  "Throws an exception if `db` doesn't match the Spec `a-spec`."
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

;; now we create an interceptor using `after`
(def check-spec-interceptor (after (partial check-and-throw :pc-ward.db/db)))


;; initialisation of "database"
(reg-event-db
  ::initialize-db
  [check-spec-interceptor]
  (fn [_ _]
    db/default-db))

;; debounce implementation
(defonce timeouts (atom {}))

(reg-fx :dispatch-debounce
        (fn [[id event-vec n]]
          (js/clearTimeout (@timeouts id))
          (swap! timeouts assoc id
                 (js/setTimeout (fn []
                                  (dispatch event-vec)
                                  (swap! timeouts dissoc id))
                                n))))

(reg-fx :stop-debounce
        (fn [id]
          (js/clearTimeout (@timeouts id))
          (swap! timeouts dissoc id)))

;; our 1s timer
(defn dispatch-timer-1s-event []
  (let [now (js/Date.)]
    (dispatch [:timer-one-second now])))                    ;; <-- dispatch used

;; our 5 min timer
(defn dispatch-timer-5m-event [] (dispatch [:timer-five-minutes]))

;; call our timer events at the required intervals
(defonce do-timer (do
                    (js/setInterval dispatch-timer-1s-event 1000)
                    (js/setInterval dispatch-timer-5m-event 300000)))


(reg-event-db                                               ;; usage:  (rf/dispatch [:timer a-js-Date])
  :timer-one-second
  (fn [db [_ new-time]]                                     ;; <-- notice how we de-structure the event vector
    (assoc db :current-time new-time)))

;; every five minutes, we check our authentication tokens are valid, and refresh if necessary
;; we cannot refresh a user login token ourselves, so give up and logout with a session expired notice
(reg-event-fx                                               ;; usage:  (rf/dispatch [:timer a-js-Date])
  :timer-five-minutes
  (fn [{db :db} [_]]                                        ;; <-- notice how we de-structure the event vector
    (cond
      ;; no service token
      (string/blank? (:concierge-service-token db)) {:dispatch [:user/service-login-start []]}
      ;; service token expired or expiring soon
      (util/jwt-expires-in-seconds? (:concierge-service-token db) 3600) {:dispatch [:user/service-login-start []]}
      ;; no authenticated user -> explicitly do nothing... without this, the next condition will be thrown
      (nil? (:authenticated-user db)) {}
      ;; user token expired - we have to force end to our session
      (util/jwt-expires-in-seconds? (get-in db [:authenticated-user :token]) 0)
      (do (js/console.log "session expired") {:dispatch [:user/session-expired]})
      ;; user token expiring soon - we still have chance refresh our token without needing to ask for credentials again
      (util/jwt-expires-in-seconds? (get-in db [:authenticated-user :token]) 3600)
      {:dispatch [:user/refresh-user-token (get-in db [:authenticated-user :namespace]) (get-in db [:authenticated-user :username])]})))

(reg-event-db
  :user/session-expired
  [check-spec-interceptor]
  (fn [db [_]]
    (-> db
        (assoc-in [:errors :login] "Your session expired. Please login again")
        (dissoc :authenticated-user))))

;; user/user-login-start kicks off a login event, cascading to return an error, or
;; a login action, via an intermediary request for a service login if we don't have an active token
(reg-event-fx
  :user/user-login-start
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]
  (fn [{db :db} [_ namespace username password]]            ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "attempting login " username)
    (if (or (string/blank? username) (string/blank? password))
      {:db (assoc-in db [:errors :login] "Unauthorised: invalid credentials")}
      {:db         (update-in db [:errors] dissoc :login)
       :dispatch-n [[:user/foreground-spinner true]
                    [:user/service-login-start [:user/user-login-do namespace username password]]
                    [:user/foreground-spinner false]]})))


(reg-event-fx
  :user/user-login-do
  [check-spec-interceptor]
  (fn [{db :db} [_ namespace username password]]
    (js/console.log "doing login " username)
    {:http-xhrio {:method          :post
                  :uri             (str config/concierge-server-address "/v1/login")
                  :timeout         5000
                  :format          (ajax/json-request-format)
                  :headers         {:Authorization (str "Bearer " (:concierge-service-token db))}
                  :params          {
                                    :user     {
                                               :system "https://fhir.nhs.uk/Id/cymru-user-id"
                                               :value  username}

                                    :password password}

                  :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                  :on-success      [:user/user-login-success namespace username]
                  :on-failure      [:user/user-login-failure]}}))


(reg-event-fx
  :user/refresh-user-token
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ namespace username]]                     ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "Refreshing user token")
    {:db         (assoc db :show-background-spinner true)   ;; causes the twirly-waiting-dialog to show??
     :http-xhrio {:method          :get
                  :uri             (str config/concierge-server-address "/v1/refresh")
                  :headers         {:Authorization (str "Bearer " (get-in db [:authenticated-user :token]))}
                  :timeout         5000                     ;; optional see API docs
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                  :on-success      [:user/user-login-success namespace username]
                  :on-failure      [:user/session-expired]}}))

(reg-event-fx
  :user/user-login-success
  [check-spec-interceptor]
  (fn [{db :db} [_ namespace username response]]
    (js/console.log "User login success: response: " + response)
    {:db       (-> db
                   (assoc-in [:authenticated-user :token] (:token response))
                   (assoc-in [:authenticated-user :namespace] namespace)
                   (assoc-in [:authenticated-user :username] username))
     :dispatch [:concierge/resolve-identifier namespace username :user/set-authenticated-user :user/set-authenticated-user-failed]}))


(reg-event-db
  :user/set-authenticated-user
  [check-spec-interceptor]
  (fn [db [_ response]]
    (js/console.log "Fetched user... response: " response)
    (if (s/valid? :pc-ward.concierge/practitioner response)
      (assoc-in db [:authenticated-user :practitioner] response)
      (do
        (js/console.log "Incorrect spec for practitioner: " (s/explain-str ::pc-ward.concierge/practitioner response))
        (dispatch [:user/logout])
        ))))


(reg-event-db
  :user/set-authenticated-user-failed
  [check-spec-interceptor]
  (fn [db [_ response]]
    (js/console.log "Fetched user... response: " response)
    (-> db
        (dissoc :authenticated-user)
        (assoc-in [:errors :login] response))))

(reg-event-db
  :user/user-login-failure
  [check-spec-interceptor]
  (fn [db [_ response]]
    (js/console.log "User login failure: response: " + response)
    (assoc-in db [:errors :login] response)))



(reg-event-fx
  :snomed/get-concept
  [check-spec-interceptor]
  (fn [{db :db} [_ concept-id]]
    (let [cached (get-in db [:snomed :concepts concept-id] :not-found)]
      (if (= cached :not-found)
        {:http-xhrio {:method          :get
                      :uri             (str config/terminology-server-address "/v1/snomed/concepts/" concept-id "/extended")
                      :timeout         5000
                      :format          (ajax/json-request-format)
                      :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                      :on-success      [:snomed/get-concept-success concept-id]
                      :on-failure      [:snomed/get-concept-failure concept-id]}}
        {}))))

(reg-event-db
  :snomed/get-concept-success
  [check-spec-interceptor]
  (fn [db [_ concept-id response]]
    (assoc-in db [:snomed :concepts concept-id] response)))


(reg-event-db
  :snomed/get-concept-failure
  [check-spec-interceptor]
  (fn [db [_ concept-id response]]
    (assoc-in db [:snomed concept-id :error] response)))


(reg-event-fx
  :snomed/search-later
  (fn [_ [_ id params]]
    {:dispatch-debounce [id [:snomed/search id params] 200]}))

(reg-event-fx
  :snomed/search
  [check-spec-interceptor]
  (fn [{db :db} [_ id {s :s is-a :is-a max-hits :max-hits}]]
    (let [clear-results {:db (-> db
                                 (update-in [:snomed id] dissoc :error)
                                 (update-in [:snomed id] dissoc :results))}]

      (if (clojure.string/blank? s)
        clear-results
        (assoc clear-results :http-xhrio {:method          :get
                                          :uri             (str config/terminology-server-address "/v1/snomed/search")
                                          :timeout         5000
                                          :format          (ajax/json-request-format)
                                          :params          {:s            s
                                                            :is_a         is-a
                                                            :maximum_hits max-hits}
                                          :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                                          :on-success      [:snomed/search-success id]
                                          :on-failure      [:snomed/search-failure id]})))))

(reg-event-db
  :snomed/search-success
  [check-spec-interceptor]
  (fn [db [_ id response]]
    (-> db
        (update-in [:snomed id] dissoc :error)
        (assoc-in [:snomed id :results] (:items response)))))

(reg-event-db
  :snomed/search-failure
  [check-spec-interceptor]
  (fn [db [_ id response]]
    (-> db
        (update-in [:snomed id] dissoc :results)
        (assoc-in [:snomed id :error] response))))

(reg-event-db
  :user/foreground-spinner
  (fn [db [_ on]]
    (assoc db :show-foreground-spinner on)))

(reg-event-fx
  :patient/search
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]
  (fn [{db :db} [_ search]]                                 ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "searching for patient: " search)
    {:dispatch-n [[:user/foreground-spinner true]
                  [:user/service-login-start [:concierge/resolve-identifier "https://fhir.cardiff.wales.nhs.uk/Id/pas-identifier" search :patient/search-success :patient/search-failed]]
                  [:user/foreground-spinner false]]}))


(reg-event-db
  :patient/clear-search
  [check-spec-interceptor]
  (fn [db [_]]
    (-> db
        (dissoc :patient-search-results)
        (update-in [:errors] dissoc :patient-search))))

(reg-event-db
  :patient/search-success
  [check-spec-interceptor]
  (fn [db [_ response]]
    (js/console.log "Found patient: " response)
    (-> db
        (assoc :patient-search-results response)
        (update-in [:errors] dissoc :patient-search))))


(reg-event-db
  :patient/search-failed
  [check-spec-interceptor]
  (fn [db [_ response]]
    (js/console.log "Patient search: error: " response)
    (-> db
        (assoc-in [:errors :patient-search] response)
        (dissoc :patient-search-results))))


(reg-event-fx
  :concierge/resolve-identifier
  [check-spec-interceptor]
  (fn [{db :db} [_ system value on-success on-failure]]
    {:http-xhrio {:method          :get
                  :uri             (str config/concierge-server-address "/v1/identifier/" value)
                  :timeout         5000
                  :format          (ajax/json-request-format)
                  :headers         {:Authorization (str "Bearer " (get-in db [:authenticated-user :token]))}
                  :params          {:system system}
                  :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                  :on-success      [on-success]
                  :on-failure      [on-failure]}}))

(defn create-service-token-map
  [db next-event]
  {:db         (assoc db :show-background-spinner true)     ;; causes the twirly-waiting-dialog to show??
   :http-xhrio {:method          :post
                :uri             (str config/concierge-server-address "/v1/login")
                :timeout         5000                       ;; optional see API docs
                :format          (ajax/json-request-format)
                :params          {
                                  :user     {
                                             :system "https://concierge.eldrix.com/Id/service-user"
                                             :value  "patientcare"}

                                  :password config/patientcare-service-secret}

                :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                :on-success      [:user/service-login-success next-event]
                :on-failure      [:user/service-login-failure]}})

(defn refresh-service-token-map
  [db next-event]
  {:db         (assoc db :show-background-spinner true)     ;; causes the twirly-waiting-dialog to show??
   :http-xhrio {:method          :get
                :uri             (str config/concierge-server-address "/v1/refresh")
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
(reg-event-fx
  :user/service-login-start
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ next-event]]                             ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (comment (js/console.log "service token needs refreshing? " (util/jwt-expires-in-seconds? (:concierge-service-token db) 120)))
    (let [expires (util/jwt-expires-seconds (:concierge-service-token db))]
      (cond
        (> expires 120) (if-not (empty? next-event) {:dispatch next-event}) ; if we've more than 120 seconds on the clock, just keep using old token
        (> expires 30) (refresh-service-token-map db next-event) ; token is due to expire, so refresh token
        :else (create-service-token-map db next-event)))))  ; grab a new token using our service credentials

;; user/service-login-success simply processes the successful service login response
;; and stores the token received from concierge
(reg-event-fx
  :user/service-login-success
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ next-event response]]
    (js/console.log "got token: " (:token response))
    {:db       (assoc db :concierge-service-token (:token response))
     :dispatch next-event}))


;; in the event of service account login token refresh failure,
;; try a new login
(reg-event-fx
  :user/service-login-refresh-failure
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]
  (fn [{db :db} [_ response]]
    {:db       (dissoc db :concierge-service-token)
     :dispatch [:user/service-login-start []]}))

(reg-event-db
  :user/service-login-failure
  [check-spec-interceptor
   (when ^boolean goog.DEBUG re-frame.core/debug)]
  (fn [{db :db} [_ response]]
    (js/console.log "service login failure: response " + response)
    (assoc-in db [:errors :login] response)))

(reg-event-db
  ::set-active-panel
  [check-spec-interceptor]
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(reg-event-db
  :user/logout
  [check-spec-interceptor]
  (fn [db [_ user]]
    (js/console.log "Logging out user" user)
    (-> db
        (dissoc :authenticated-user)
        (dissoc :patient-search-results)
        (dissoc :errors))))

