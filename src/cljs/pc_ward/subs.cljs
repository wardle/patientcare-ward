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
  :user/login-error
  (fn [db _]
    (:login-error db)))

