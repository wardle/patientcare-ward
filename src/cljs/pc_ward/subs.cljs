(ns pc-ward.subs
  (:require
    [re-frame.core :refer [reg-sub subscribe]]
    [clojure.string :as string]))


(defn error-message
  "Returns the error message from an error"
  [error]
  (cond
    (nil? error) nil
    (string? error) error
    :else (let [status (:status-text error)
                detail (get-in error [:response :error])]
            (str (:status-text error) (when-not (string/blank? detail) (str ": " detail))))))


(reg-sub
  ::name
  (fn [db]
    (:name db)))

(reg-sub
  ::active-panel
  (fn [db _]
    (:active-panel db)))

(reg-sub
  :show-foreground-spinner
  (fn [db _]
    (:show-foreground-spinner db)))

(reg-sub
  :patient/search-error
  (fn [db _]
    (error-message (get-in db [:errors :patient-search]))))


(reg-sub
  :user/authenticated-user
  (fn [db _]
    (get-in db [:authenticated-user :user])))

(reg-sub
  :user/full-name
  ;; first function is our subscription
  (fn [query-v _]
    (subscribe [:user/authenticated-user]))
  (fn [authenticated-user query_v _]
    (let [human-name (first (:names authenticated-user))
          title (clojure.string/join " " (:prefixes human-name))
          first-names (:given human-name)
          last-names (:family human-name)]
      (str title " " first-names " " last-names)
      )))

(reg-sub
  :user/login-error
  (fn [db _]
    (error-message (:login-error db))))

(reg-sub
  :current-time
  (fn [db _]                                                ;; db is current app state. 2nd unused param is query vector
    (:current-time db)))

(reg-sub
  :snomed/concept
  (fn [db [_ concept-id]]
    (get-in db [:snomed :concepts concept-id])))

(reg-sub
  :snomed/error
  (fn [db _]
    (error-message (get-in db [:snomed :error]))))

(reg-sub
  :snomed/results
  (fn [db [_ id]]
    (get-in db [:snomed id :results])))

(reg-sub
  :patient/search-results
  (fn [db [_]]
    (:patient-search-results db)))