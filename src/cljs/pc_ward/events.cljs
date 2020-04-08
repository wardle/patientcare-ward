(ns pc-ward.events
  (:require
    [re-frame.core :as re-frame]
    [pc-ward.db :as db]
    [clojure.string :as string]

    [day8.re-frame.tracing :refer-macros [fn-traced]]
    ))

(re-frame/reg-event-db
  ::initialize-db
  (fn-traced [_ _]
             db/default-db))

(re-frame/reg-event-db
  ::set-active-panel
  (fn-traced [db [_ active-panel]]
             (assoc db :active-panel active-panel)))


;; :user/attempt-login is an event triggered by a login attempt
;; this simply changes the name at the moment
(re-frame/reg-event-db
  :user/attempt-login
  (fn-traced [db [_ namespace username password]]
             (if (or (string/blank? username) (string/blank? password))
               (do
                 (assoc db :login-error "Invalid username or password"))
               (do
                 (js/console.log "Attempting login for user" username)
                 (-> db
                     (dissoc :login-error)
                     (assoc :name username :authenticated-user username))))))




(re-frame/reg-event-db
  :user/logout
  (fn-traced [db [_ user]]
             (js/console.log "Logging out user" user)
             (dissoc db :authenticated-user)))