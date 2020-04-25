(ns pc-ward.views
  (:require
    [re-frame.core :as rf]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [cljs-time.core :as time-core]
    [cljs-time.predicates]
    [cljs-time.extend]                                      ;; this import is needed so that equality for date/time works properly....

    [pc-ward.config :as config]
    [pc-ward.subs :as subs]
    [pc-ward.clinical :as clin]
    [pc-ward.news-chart :as news]))




(defn patientcare-title []
  [:section.section [:div.container [:h1.title "PatientCare"] [:p.subtitle "Ward"]]])

(defn login-panel []
  (let [error (rf/subscribe [:user/error :login])
        username (reagent/atom "")
        password (reagent/atom "")
        submitting (rf/subscribe [:show-foreground-spinner])
        doLogin #(rf/dispatch [:user/user-login-start config/default-user-namespace (string/trim @username) @password])]
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
                             :on-change   #(reset! username (-> % .-target .-value))}]]]

            ;; password field - if user presses enter, automatically submit
            [:div.field [:label.label {:for "login-pw"} "Password"]
             [:div.control
              [:input.input {:id          "login-pw" :type "password" :placeholder "enter password" :required true
                             :disabled    @submitting
                             :on-key-down #(if (= 13 (.-which %)) (do
                                                                    (reset! password (-> % .-target .-value))
                                                                    (doLogin)))
                             :on-change   #(reset! password (-> % .-target .-value))}]]]


            [:button.button {:class    ["is-primary" (when @submitting "is-loading")]
                             :disabled @submitting
                             :on-click doLogin} " Login "]]

           (if-not (string/blank? @error) [:div.notification.is-danger [:p @error]])]]]]])))

(defn logout-button [] [:button.button {:on-click #(rf/dispatch [:user/logout])} "Logout"])


(defn clock
  []
  (-> @(rf/subscribe [:current-time])
      .toTimeString
      (string/split " ")
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
           [:a.navbar-item "View radiology images"]]]]


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


(defn snomed-show-concept
  "Display read-only information about a concept in a panel"
  [concept-id save-func]
  (let [active-panel (reagent/atom :active-synonyms)]       ;; outside of the function will be called once
    (fn [concept-id]
      (let [concept (rf/subscribe [:snomed/concept concept-id]) ;; inside of the function will be called multiple times (on re-render)
            loading (nil? @concept)
            has-inactive (some #(not %) (map #(:active %) (:descriptions @concept)))]
        [:nav.panel.is-success
         [:p.panel-heading
          (if loading
            "Loading..."
            [:span (get-in @concept [:preferred_description :term])
             (if-not (get-in @concept [:concept :active]) [:span.tag.is-danger "Inactive"])])]

         (if (and (not has-inactive) (= @active-panel :inactive-synonyms)) (reset! active-panel :active-synonyms))
         [:p.panel-tabs
          [:a {:class    (if (= @active-panel :active-synonyms) "is-active" "")
               :on-click #(reset! active-panel :active-synonyms)} "Active synonyms"]
          (if has-inactive [:a {:class    (if (= @active-panel :inactive-synonyms) "is-active" "")
                                :on-click #(reset! active-panel :inactive-synonyms)} "Inactive"])
          [:a {:class    (if (= @active-panel :preferred-synonyms) "is-active" "")
               :on-click #(reset! active-panel :preferred-synonyms)} "Preferred"]]

         (if loading
           [:p.panel-block.is-size-7 "Loading..."]
           (let [preferred-id (get-in @concept [:preferred_description :id])]
             (doall (->> (sort-by :term (:descriptions @concept))
                         (filter #(case @active-panel
                                    :active-synonyms (:active %)
                                    :inactive-synonyms (not (:active %))
                                    :preferred-synonyms (= preferred-id (:id %))))
                         (filter #(not= (:type_id %) config/sct-fully-specified-name)) ;; exclude fully specified names
                         (map #(vector
                                 :p.panel-block.is-size-7 {:key (:id %)}
                                 (if (and (not= @active-panel :preferred-synonyms) (= preferred-id (:id %)))
                                   [:strong (:term %)]
                                   (:term %))))))))

         [:div.panel-block
          [:button.button.is-link.is-outlined.is-fullwidth "Save"]]]))))


(defn snomed-autocomplete
  "Display a SNOMED CT autocompletion box"
  [v kp {id     :id                                         ;; id (e.g. ::new-diagnosis)
         name   :name                                       ;; name for this, eg. diagnosis
         common :common                                     ;; list of common concepts, if present will be shown in pop-up list
         is-a   :is-a}]                                     ;; vector containing is-a constraints

  {:pre [(vector? kp)]}
  (let [
        search (reagent/atom "")
        selected-index (reagent/atom nil)
        results (rf/subscribe [:snomed/results id])]
    (fn [v kp props]
      (when (and (= 0 @selected-index) (> (count @results) 0)) ;; handle special case of typing in search to ensure we start fetching first result
        (rf/dispatch [:snomed/get-concept (:concept_id (first @results))]))
      [:div
       [:div.field
        [:label.label name]
        [:div.control
         [:input.input {:type      "text" :placeholder name :value @search
                        :on-change #(do
                                      (reset! selected-index 0)
                                      (reset! search (-> % .-target .-value))
                                      (rf/dispatch [:snomed/search-later id {:s        (-> % .-target .-value)
                                                                             :is-a     [370159000 64572001]
                                                                             :max-hits 500}]))}]]]
       [:div.field
        [:div.select.is-multiple.is-fullwidth {:style {:height (str 10 "em")}}
         [:select {:multiple  true :size 4
                   :value     (vector @selected-index)
                   :on-set    #(let [val (int (-> % (.-target) (.-value)))
                                     item (nth @results val)]
                                 (rf/dispatch [:snomed/get-concept (:concept_id item)]))
                   :on-change #(let [val (int (-> % (.-target) (.-value)))
                                     item (nth @results val)]
                                 (rf/dispatch [:snomed/get-concept (:concept_id item)])
                                 (reset! selected-index val))}
          (doall (map-indexed (fn [index item] [:option {:key index :value index} (:term item)]) @results))]]


        ;; if we have a selected result, show it
        (if (and (> (count @results) 0) (not (nil? @selected-index)))
          [snomed-show-concept (:concept_id (nth @results @selected-index))])]])))





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
         [:input {:type "radio" :name "answer"}] " On oxygen"]]]]]))




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
          [:a.button.is-danger {:on-click stop-timer} "Stop timer"]]]))))






(def news-data [
                {:date-time      (time-core/date-time 2020 4 9 9 3 27) :respiratory-rate 12 :pulse-rate 110
                 :blood-pressure {:systolic 138 :diastolic 82}}
                {:date-time      (time-core/date-time 2020 4 9 15 3 27) :respiratory-rate 14 :pulse-rate 90
                 :blood-pressure {:systolic 142 :diastolic 70}}

                {:date-time (time-core/date-time 2020 4 12 9 3 27) :respiratory-rate 10 :pulse-rate 82 :temperature 39.5}

                {:date-time (time-core/date-time 2020 4 14 9 3 27) :respiratory-rate 22, :spO2 94 :consciousness :clin/alert}
                {:date-time (time-core/date-time 2020 4 14 17 3 27) :respiratory-rate 22 :spO2 98 :conscioussness :clin/alert}
                {:date-time (time-core/date-time 2020 4 15 10 2 45) :respiratory-rate 18 :pulse-rate 85}
                {:date-time      (time-core/date-time 2020 4 17 10 2 45) :respiratory-rate 12 :spO2 97
                 :blood-pressure {:systolic 138 :diastolic 82}}
                {:date-time (time-core/date-time 2020 4 18 7 2 45) :respiratory-rate 30}
                {:date-time (time-core/date-time 2020 4 18 9 2 45) :respiratory-rate 28 :pulse-rate 90}
                {:date-time (time-core/date-time 2020 4 18 12 2 45) :respiratory-rate 23 :pulse-rate 110}
                {:date-time (time-core/date-time 2020 4 18 19 2 45) :respiratory-rate 8 :consciousness :clin/alert}
                {:date-time (time-core/date-time 2020 4 8 22 2 45) :respiratory-rate 14 :pulse-rate 120 :spO2 92}
                {:date-time (time-core/date-time 2020 4 8 10 2 45) :respiratory-rate 12 :pulse-rate 140}
                {:date-time      (time-core/date-time 2020 4 7 9 2 45) :respiratory-rate 22 :pulse-rate 90 :spO2 88 :consciousness :clin/confused
                 :blood-pressure {:systolic 201 :diastolic 110}}
                {:date-time (time-core/date-time 2020 4 6 11 2 45) :respiratory-rate 12}
                {:date-time (time-core/date-time 2020 4 5 11 2 45) :respiratory-rate 12 :temperature 37.2}
                {:date-time (time-core/date-time 2020 4 4 11 2 45) :respiratory-rate 12 :consciousness :clin/unresponsive}
                {:date-time (time-core/date-time 2020 4 19 9 2 45) :respiratory-rate 12 :consciousness :clin/alert :pulse-rate 95 :temperature 37.2}])






(defn form-early-warning-score
  [save-func]
  (let [results (reagent/atom nil)
        drawing-start-date (reagent/atom (time-core/date-time 2020 3 24))]
    (fn [value]
      [:div

       [snomed-autocomplete results [:diagnosis] "Diagnosis" "errrr"]
       [:div
        [:button.button {:on-click #(swap! drawing-start-date (fn [old] (time-core/minus old (time-core/days 7))))} " << "]
        [:button.button {:on-click #(swap! drawing-start-date (fn [old] (time-core/minus old (time-core/days 1))))} " < "]
        [:button.button {:on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 1))))} " > "]
        [:button.button {:on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 7))))} " >> "]]

       [news/test-drawing @drawing-start-date]

       [respiratory-rate results [:resp-rate]]
       [oxygen-saturations results [:o2-sats]]

       [form-textfield results [:pulse] "Pulse rate" "Beats per minute"]
       [form-textfield results [:bp] "Blood pressure" "Write as 120/80"]
       [form-textfield results [:temperature] "Temperature" "e.g. 37.4C"]

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


       [:p "Results: "]
       [:p "RR: " (:resp-rate @results) " score: " (clin/calc-news-respiratory (:resp-rate @results))]
       [:p "o2 sats:" (get-in @results [:o2-sats :o2-saturations]) " score: " (clin/calc-news-o2-sats-scale-1 (get-in @results [:o2-sats :o2-saturations]))]
       [:p "pulse: " (:pulse @results) " score: " (clin/calc-news-pulse (:pulse @results))]
       [:p "temperature: " (:temperature @results) " score: " (clin/calc-news-temperature (:temperature @results))]])))




(defn welcome []
  [:article.message.is-warning
   [:div.message-header
    [:h2 "Welcome to PatientCare: Ward"]]
   [:div.message-body
    [:div.content
     [:p "This an inpatient ward application, designed in response to the SARS-CoV-2 global pandemic. "]
     [:p "It is intended to support inpatient patient management, primarily via the recording and
           presentation of electronic observations, calculation of escalation scores and provision of checklists."]
     [:p "This is a demonstrator application that is a proof-of-concept, developed in a week, supporting the following functionality:"]
     [:ul
      [:li "User login using NHS Wales' credentials via NADEX"]
      [:li "Patient lookup via Cardiff and Vale patient administrative system (PMS) and the national NHS Wales' EMPI"]
      [:li "Clinical diagnostic and procedure coding using SNOMED CT"]
      [:li "Recording and charting of observations including the National Early Warning Score 2"]
      [:li "Integration with local and national document repositories"]
      [:li "Support for desktop computers and mobile devices"]]
     [:p [:strong "To get started, type in a hospital number in the search box"]]]]])


(defn show-patient
  [patient confirm-func cancel-func]

  [:div.card
   [:header.card-header
    [:p.card-header-title (clojure.string/join " " [(:title patient) (:firstnames patient) (:lastname patient)])]
    [:p.card-header-title.is-pulled-right 123456789]]

   [:div.card-content
    [:div.content "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus nec iaculis mauris."
     [:a {:href "#"} "@bulmaio"] "." [:a {:href "#"} "#css"] [:a {:href "#"} "#responsive"]
     [:br]]]

   [:footer.card-footer
    [:a.card-footer-item.is-link {:href "#"} "View record"]
    [:a.card-footer-item {:on-click cancel-func} "Cancel"]]])




(defn home-panel []
  (let [
        search (reagent/atom "")
        search-results (rf/subscribe [:patient/search-results])
        error (rf/subscribe [:patient/search-error])]

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
              [:input.input {:type        "text"
                             :id          "search-field-id"
                             :auto-focus  true
                             :placeholder "Search for patient"
                             :value       @search
                             :on-change   #(reset! search (-> % .-target .-value))
                             :on-key-down #(if (= 13 (.-which %)) (do
                                                                    (reset! search (-> % .-target .-value))
                                                                    (rf/dispatch [:patient/search @search])))}]
              [:span.icon.is-left
               [:i.fas.fa-search {:aria-hidden "true"}]]]]

            (comment
              [:div.panel-block
               [:div.control
                [:label.radio
                 [:input {:type "radio" :name "foobar" :checked "true"}] " My patients"]
                [:label.radio
                 [:input {:type "radio" :name "foobar"}] " All patients"]]])

            [:div.panel-block
             [:button.button.is-outlined.is-fullwidth
              {:on-click #(rf/dispatch [:patient/search @search])} "Search"]]]


           (comment
             [:article.panel.is-link
              [:p.panel-heading "My teams"]

              [:p.panel-tabs
               [:a "Clinical"]
               [:a "Research"]
               [:a.is-active "All"]]

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
               [:button.button.is-link.is-outlined.is-fullwidth "Manage..."]]])]



          [:div.column

           (if-not (nil? @error)
             (do
               (js/console.log "Error from @error " @error)
               [:article.message.is-warning
                [:div.message-header
                 [:p "Unable to open patient record"]]
                [:div.message-body @error]])
             (do
               (if (nil? @search-results)
                 [welcome]
                 [show-patient @search-results #() #(do (reset! search "")
                                                        (.focus (.getElementById js/document "search-field-id"))
                                                        (rf/dispatch [:patient/clear-search]))])))]]]]])))











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
     nil))

