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

(re-frame/reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(comment

  )

;; :user/get-service-token is an event raised to obtain a new service token, or refresh
;; an existing token. In essence, this event can either be periodically triggered, or
;; triggered just-in-time, in order to ensure that we have a valid service token.
;; this event takes an optional additional event to trigger once it is complete




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




;; see https://github.com/day8/re-frame/blob/master/docs/Talking-To-Servers.md
;; user/service-login-start kicks off a service-user login request, optionally
;; invoking the "next-event" once successful
(re-frame/reg-event-fx
  :user/service-login-start
  [(when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [{db :db} [_ _]]    ;; note first argument is cofx, so we extract db (:db cofx) using clojure's destructuring
    (js/console.log "service token needs refreshing? " (util/jwt-expires-in-seconds? (:concierge-service-token db ) 120))
    (if (util/jwt-expires-in-seconds? (:concierge-service-token db) 120)
      {:db         (assoc db :show-twirly true)             ;; causes the twirly-waiting-dialog to show??
       :http-xhrio {:method          :post
                    :uri             "http://localhost:8080/v1/login"
                    :timeout         5000                   ;; optional see API docs
                    :format          (ajax/json-request-format)
                    :params          {
                                      :user     {
                                                 :system "https://concierge.eldrix.com/Id/service-user"
                                                 :value  "patientcare"
                                                 }
                                      :password "Tly65k8XRdCCBnJy7KJRrWDgc2V9lO5j0lGLjQzrwFwkryuCJZaAZT5xtxiIU7CR"
                                      }
                    :response-format (ajax/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                    :on-success      [:user/service-login-store-token]
                    :on-failure      [:bad-http-result]}})))

;; user/service-login-store-token simply processes the successful service login response
;; and stores the token
(re-frame/reg-event-db
  :user/service-login-store-token
  [(when ^boolean goog.DEBUG re-frame.core/debug)]          ;; this is an interceptor
  (fn [db [_ response]]
    (js/console.log "got token: " (:token response))
    (assoc db :concierge-service-token (:token response))))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(re-frame/reg-event-fx
  :user/bad-http-result
  (fn [db [_ response]]
    (js/console.log "bad http result : response " + response)
    {:db (assoc db :error (:status-text response))}
    ))


(re-frame/reg-event-db
  :user/logout
  (fn-traced [db [_ user]]
             (js/console.log "Logging out user" user)
             (dissoc db :authenticated-user)))