(ns pc-ward.views
  (:require
    [re-frame.core :as rf]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [pc-ward.subs :as subs]
    [pc-ward.clinical :as clin]
    ))

;; concievably our UI could allow selection from a list of "providers".
(def default-user-namespace "https://fhir.nhs.uk/Id/cymru-user-id")


(defn patientcare-title []
  [:section.section [:div.container [:h1.title "PatientCare"] [:p.subtitle "Ward"]]])

(defn login-panel []
  (let [error (rf/subscribe [:user/login-error])
        username (reagent/atom "")
        password (reagent/atom "")
        submitting (rf/subscribe [:show-foreground-spinner])
        doLogin #(rf/dispatch [:user/user-login-start default-user-namespace (string/trim @username) @password])]
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
                             :disabled    @submitting
                             :auto-focus  true
                             :on-key-down #(if (= 13 (.-which %)) (do (.focus (.getElementById js/document "login-pw"))))
                             :on-blur     #(reset! username (-> % .-target .-value))}]]]

            ;; password field - if user presses enter, automatically submit
            [:div.field [:label.label {:for "login-pw"} "Password"]
             [:div.control
              [:input.input {:id          "login-pw" :type "password" :placeholder "enter password" :required true
                             :disabled    @submitting
                             :on-key-down #(if (= 13 (.-which %)) (do
                                                                    (reset! password (-> % .-target .-value))
                                                                    (doLogin)))
                             :on-blur     #(reset! password (-> % .-target .-value))}]]]


            [:button.button {:class    ["is-primary" (when @submitting "is-loading")]
                             :disabled @submitting
                             :on-click doLogin} " Login "]
            ]
           (if-not (string/blank? @error) [:div.notification.is-danger [:p @error]])]]]]])))

(defn logout-button [] [:button.button {:on-click #(rf/dispatch [:user/logout])} "Logout"])


(defn clock
  []
  (-> @(rf/subscribe [:current-time])
      .toTimeString
      (clojure.string/split " ")
      first))


(defn nav-bar []
  (let [show-nav-menu (reagent/atom false)
        full-name (rf/subscribe [:user/full-name])]
    (fn []
      [:nav.navbar.is-black.is-fixed-top {:role "navigation" :aria-label "main navigation"}
       [:div.navbar-brand
        [:a.navbar-item [:h1 "PatientCare: " [:strong "Ward"]]]
        [:a.navbar-burger.burger {:role     "button" :aria-label "menu" :aria-expanded @show-nav-menu
                                  :class    (if @show-nav-menu :is-active "")
                                  :on-click #(swap! show-nav-menu not)}
         [:span {:aria-hidden "true"}]
         [:span {:aria-hidden "true"}]
         [:span {:aria-hidden "true"}]]]
       [:div.navbar-menu (when @show-nav-menu {:class :is-active})
        [:div.navbar-start
         [:a.navbar-item "Home"]
         [:div.navbar-item.has-dropdown.is-hoverable
          [:a.navbar-link [:span "Messages\u00A0"] [:span.tag.is-danger.is-rounded 123]]
          [:div.navbar-dropdown
           [:a.navbar-item "Unread messages"]
           [:a.navbar-item "Archive"]
           [:hr.navbar-divider]
           [:a.navbar-item "Send a message..."]]]
         [:a.navbar-item "Teams"]

         [:div.navbar-item.has-dropdown.is-hoverable
          [:a.navbar-link [:span "Links"]]
          [:div.navbar-dropdown
           [:a.navbar-item "Cardiff and Vale homepage"]
           [:a.navbar-item "Cardiff Clinical Portal"]
           [:a.navbar-item "Welsh Clinical Portal (Cardiff)"]
           [:a.navbar-item "Welsh Clinical Portal (Cwm Taf Morgannwg)"]
           [:hr.navbar-divider]
           [:a.navbar-item "View radiology images"]]]

         ]
        [:div.navbar-end
         [:a.navbar-item [clock]]
         [:div.navbar-item.has-dropdown.is-hoverable
          [:a.navbar-link @full-name]
          [:div.navbar-dropdown
           [:a.navbar-item "Profile"]
           [:a.navbar-item "Teams"]
           [:hr.navbar-divider]
           [:a.navbar-item {:disabled true} "Report an issue"]]
          [:div.buttons
           [:a.button.is-light {:on-click #(rf/dispatch [:user/logout])} "Logout"]]]]]])))


(defn snomed-autocomplete
  [v kp name help]
  {:pre [(vector? kp)]}
  (let [
        results (rf/subscribe [:snomed/results ::new-diagnosis])
        ]
    (fn [v kp name help]
      [:div
       [:div.field
        [:label.label name]
        [:div.control
         [:input.input {:type       "text" :placeholder name
                        :on-dispose #(rf/dispatch [:snomed-search ::new-diagnosis {:s ""}])
                        :on-change  #(rf/dispatch [:snomed/search-later ::new-diagnosis {:s        (-> % .-target .-value)
                                                                                         :is-a     64572001
                                                                                         :max-hits 500}])}]]
        ]

       [:div.select.is-multiple
        [:select {:multiple true}
         {:name "0.15.1.0.1.0.0.11.3.7.502066157.1.1.3.0.RSEditToOneSnomedConceptTypeAhead.0.3.0.1.9.1" :size 8}

         (doall (map-indexed (fn [index item] [:option {:value index} (:term item)]) @results))

         ]

        ]
       ])))



(defn form-textfield
  [v kp name help]
  {:pre [(vector? kp)]}
  (fn [v kp name help]
    [:div.field
     [:label.label name]
     [:div.control
      [:input.input {:type      "text" :placeholder name
                     :value     (get-in @v kp)
                     :on-change #(swap! v assoc-in kp (-> % .-target .-value))}]]
     (if-not (clojure.string/blank? help) [:p.help help])]))


(defn oxygen-saturations
  "Component to record o2 saturations and air/oxygen/device, with results pushed as map to atom (v) via keypath (kp)"
  [v kp]
  {:pre [(vector? kp)]}
  (fn []
    [:div.field.is-horizontal
     [:div.field-label.is-normal
      [:label.label "Oxygen saturations"]]
     [:div.field-body
      [:div.field
       [:p.control.is-expanded.has-icons-left
        [:input.input {:type      "text" :placeholder "O2 sats"
                       :value     (get-in @v (conj kp :o2-saturations))
                       :on-change #(swap! v assoc-in (conj kp :o2-saturations) (-> % .-target .-value))}]
        [:span.icon.is-small.is-left
         [:i.fas.fa-lungs]]]]
      [:div.field
       [:div.control
        [:label.radio
         [:input {:type "radio" :name "answer"}] " On air"]
        [:label.radio
         [:input {:type "radio" :name "answer"}] " On oxygen"]]
       ]]]
    )
  )

(defn respiratory-rate
  "Component to record respiratory rate, with result pushed to atom (v) using key path (kp) (a vector of keys)"
  [v kp]
  {:pre [(vector? kp)]}
  (let [current-time (rf/subscribe [:current-time])
        timer (reagent/atom {:start nil :status :not-running :breaths 0})
        stop-timer #(let [duration (/ (- @current-time (:start @timer)) 1000) ;; milliseconds -> seconds
                          result (* (/ (:breaths @timer) duration) 60)]
                      (swap! v assoc-in kp (int result))
                      (reset! timer {:start nil :status :not-running :breaths 0}))
        start-timer #(reset! timer {:start @current-time :status :running :breaths 0})]
    (fn [value]
      (cond
        ;; timer isn't running, so show a text field and a button to start the timer
        (= (:status @timer) :not-running)
        [:div.field.has-addons [:label.label "Respiratory rate " [:p.help "Breaths per minute"]]
         [:div.control.has-icons-left.has-icons-right
          [:input.input {:type      " text " :placeholder " Respiratory rate " :value (get-in @v kp)
                         :on-change #(swap! v assoc-in kp (-> % .-target .-value))}]
          [:span.icon.is-small.is-left [:i.fas.fa-lungs]] (comment [:span.icon.is-small.is-right [:i.fas.fa-check]])]
         [:div.control
          [:a.button.is-info {:on-click start-timer} "  Start timer  "]]]

        ;; timer is running, so show the countdown from 60 seconds and count the number of breaths recorded
        (= (:status @timer) :running)
        [:div.field.has-addons [:label.label "Respiratory rate " [:p.help "Breaths per minute"]]
         [:div.control.has-icons-left.has-icons-right.is-loading
          [:input.input {:type " text " :disabled true :value (str (:breaths @timer) " breaths in " (int (/ (- @current-time (:start @timer)) 1000)) "s")}]
          [:span.icon.is-small.is-left [:i.fas.fa-lungs]] (comment [:span.icon.is-small.is-right [:i.fas.fa-check]])]
         [:div.control
          [:a.button.is-info {:on-click #(swap! timer update-in [:breaths] inc)} "Count breath"]
          [:a.button.is-danger {:on-click stop-timer} "Stop timer"]]]
        )
      ))
  )







(defn form-early-warning-score
  [save-func]
  (let [results (reagent/atom nil)]
    (fn [value]
      [:div

       [respiratory-rate results [:resp-rate]]
       [oxygen-saturations results [:o2-sats]]

       [form-textfield results [:pulse] "Pulse rate" "Beats per minute"]
       [form-textfield results [:bp] "Blood pressure" "Write as 120/80"]
       [form-textfield results [:temperature] "Temperature" "e.g. 37.4C"]

       [snomed-autocomplete results [:diagnosis] "Diagnosis" "errrr"]

       [:p "Results: "]
       [:p "RR: " (:resp-rate @results) " score: " (clin/calc-news-respiratory (:resp-rate @results))]
       [:p "o2 sats:" (get-in @results [:o2-sats :o2-saturations]) " score: " (clin/calc-news-o2-sats-scale-1 (get-in @results [:o2-sats :o2-saturations]))]
       [:p "pulse: " (:pulse @results) " score: " (clin/calc-news-pulse (:pulse @results))]
       [:p "temperature: " (:temperature @results) " score: " (clin/calc-news-temperature (:temperature @results))]

       ]
      )))

(defn home-panel []
  (let [
        result-early-warning-score (reagent/atom {})
        ]
    (fn []
      [:div
       [nav-bar]

       [:section.section
        [:div.container
         [:div.columns
          [:div.column.is-3

           ; patient search box
           [:article.panel
            [:div.panel-block
             [:p.control.has-icons-left
              [:input.input {:type "text" :auto-focus true :placeholder "Search for patient"}]
              [:span.icon.is-left
               [:i.fas.fa-search {:aria-hidden "true"}]]]]

            [:div.panel-block
             [:div.control
              [:label.radio
               [:input {:type "radio" :name "foobar" :checked "true"}] " My patients"]
              [:label.radio
               [:input {:type "radio" :name "foobar"}] " All patients"]]]
            [:div.panel-block
             [:button.button.is-outlined.is-fullwidth "Search"]]

            ]

           [:article.panel.is-link
            [:p.panel-heading "My teams"]

            [:p.panel-tabs
             [:a "Clinical"]
             [:a "Research"]
             [:a.is-active "All"]
             ]
            [:a.panel-block
             [:span.panel-icon
              [:i.fas.fa-clinic-medical {:aria-hidden "true"}]] "COVID team 23"]
            [:a.panel-block
             [:span.panel-icon
              [:i.fas.fa-clinic-medical {:aria-hidden "true"}]] "UHW / medical"]
            [:a.panel-block
             [:span.panel-icon
              [:i.fas.fa-clinic-medical {:aria-hidden "true"}]] "UHW / neurology"]
            [:a.panel-block
             [:span.panel-icon
              [:i.fas.fa-clinic-medical {:aria-hidden "true"}]] "Helen Durham Neuro-inflammatory unit"]
            [:a.panel-block
             [:span.panel-icon
              [:i.fas.fa-university {:aria-hidden "true"}]] "MS Epidemiology study"]
            [:div.panel-block
             [:button.button.is-link.is-outlined.is-fullwidth "Manage..."]]
            ]

           ]
          [:div.column

           [:article.message.is-warning
            [:div.message-header
             [:h2 "Welcome to PatientCare: Ward"]]
            [:div.message-body
             [:p "This an inpatient ward application, designed in response to the SARS-CoV-2 global pandemic. "]
             [:p "It is designed to support inpatient patient management, primarily via the recording and
           presentation of electronic observations, calculation of escalation scores and provision of checklists"]
             ]]

           [:article-message.is-danger
            [:div.message-header [:p "Observations"]]
            [:div.message-body

             [form-early-warning-score nil]



             [:div.field.is-horizontal
              [:div.field-label.is-normal
               [:label.label "Blood pressure"]]
              [:div.field-body
               [:div.field
                [:p.control.is-expanded.has-icons-left
                 [:input.input {:type "text" :placeholder "Systolic"}]
                 [:span.icon.is-small.is-left
                  [:i.fas.fa-heart]]]]
               [:div.field
                [:p.control.is-expanded.has-icons-left.has-icons-right
                 [:input.input.is-success {:type "text" :placeholder "Diastolic"}]
                 [:span.icon.is-small.is-left
                  [:i.fas.fa-heart]]
                 [:span.icon.is-small.is-right
                  [:i.fas.fa-check]]]]]]
             ]]]
          ]
         ]
        ]]
      )))

;; about

(defn about-panel []
  [:div
   [:h1 " This is the About Page. "]
   [logout-button]

   [:div
    [:a {:href " #/"}
     " go to Home Page "]]])


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
     13 (" It's thirteen ")                                 ; enter
     20 (" It's twenty ")                                   ; esc
     nil)

  )