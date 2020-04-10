(ns pc-ward.views
  (:require
    [re-frame.core :as rf]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [pc-ward.subs :as subs]
    ))

;; concievably our UI could allow selection from a list of "providers".
(def default-user-namespace "https://fhir.nhs.uk/Id/cymru-user-id")


(defn patientcare-title []
  [:section.section [:div.container [:h1.title "PatientCare"] [:p.subtitle "Ward"]]])

(defn login-panel []
  (let [error (rf/subscribe [:user/login-error])
        username (reagent/atom "")
        password (reagent/atom "")
        doLogin #(rf/dispatch [:user/user-login-start default-user-namespace (string/trim @username) @password])
        ]
    (fn []
      [:section.hero.is-full-height
       [:div.hero-body
        [:div.container
         [:div.columns.is-centered
          [:div.column.is-5-tablet.is-4-desktop.is-3-widescreen
           [patientcare-title]
           [:div.box
            ;; username field - if user presses enter, automatically switch to password field
            [:div.field [:label.label {:for "login-un"} "Username"]
             [:div.control
              [:input.input {:id          "login-un" :type "text" :placeholder "e.g. ma090906" :required true
                             :on-key-down #(if (= 13 (.-which %)) (do (.focus (.getElementById js/document "login-pw"))))
                             :on-blur     #(reset! username (-> % .-target .-value))}]]]

            ;; password field - if user presses enter, automatically submit
            [:div.field [:label.label {:for "login-pw"} "Password"]
             [:div.control
              [:input.input {:id          "login-pw" :type "password" :placeholder "enter password" :required true
                             :on-key-down #(if (= 13 (.-which %)) (do
                                                                    (reset! password (-> % .-target .-value))
                                                                    (doLogin)))
                             :on-blur     #(reset! password (-> % .-target .-value))}]]]

            [:button.button {:class    "is-primary"
                             :on-click doLogin} " Login "]

            ]
           (if-not (string/blank? @error) [:div.notification.is-danger [:p "Error:" @error]])]]]]])))

(defn logout-button [] [:button.button {:on-click #(rf/dispatch [:user/logout])} "Logout"])

(defn home-panel []
  [:div
   [:h1 "This is the main page."]
   [logout-button]
   [:div
    [:a {:href "#/about"}
     "go to About Page"]]])

;; about

(defn about-panel []
  [:div
   [:h1 "This is the About Page."]
   [logout-button]

   [:div
    [:a {:href "#/"}
     "go to Home Page"]]])


(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :about-panel [about-panel]
    [:div]))

(defn show-panel [panel-name]
  [panels panel-name])

;;
(defn main-panel []
  (let [authenticated-user (rf/subscribe [:user/authenticated-user])
        active-panel (rf/subscribe [::subs/active-panel])]
    (fn []
      (if (nil? @authenticated-user)
        [login-panel]
        [show-panel @active-panel]))))



(comment

  #(case (.-which %)
     13 ("It's thirteen")                                   ; enter
     20 ("It's twenty")                                     ; esc
     nil)

  )