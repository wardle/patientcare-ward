(ns pc-ward.subs
  (:require
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::name
  (fn [db]
    (:name db)))

(re-frame/reg-sub
  ::active-panel
  (fn [db _]
    (:active-panel db)))

(re-frame/reg-sub
  :show-foreground-spinner
  (fn [db _]
    (:show-foreground-spinner db)))

(re-frame/reg-sub
  :user/authenticated-user
  (fn [db _]
    (:authenticated-user db)))

(re-frame/reg-sub
  :user/full-name
  ;; first function is our subscription
  (fn [query-v _]
    (re-frame/subscribe [:user/authenticated-user]))
  (fn [authenticated-user query_v _]
    (let [human-name (first (:names authenticated-user))
          title (clojure.string/join " " (:prefixes human-name))
          first-names (:given human-name)
          last-names (:family human-name)]
      (str title " " first-names " " last-names)
      )))

(re-frame/reg-sub
  :user/login-error
  (fn [db _]
    (:login-error db)))

(re-frame/reg-sub
  :current-time
    (fn [db _]     ;; db is current app state. 2nd unused param is query vector
      (:current-time db)))

(re-frame/reg-sub
  :snomed/error
  (fn [db _]
    (get-in db [:snomed :error])))

(re-frame/reg-sub
  :snomed/results
  (fn [db [_ id]]
    (get-in db [:snomed id :results])))
