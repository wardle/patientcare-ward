(ns pc-ward.views
  (:require
    [re-frame.core :as rf]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [cljs-time.core :as time-core]
    [cljs-time.predicates]
    [cljs-time.extend]                                      ;; this import is needed so that equality for date/time works properly....
    [clojure.spec.alpha :as s]
    [pc-ward.config :as config]
    [pc-ward.subs :as subs]
    [pc-ward.clinical :as clin]
    [pc-ward.concierge :as concierge]
    [pc-ward.news-chart :as news]
    [clojure.string :as str])
  )

(defn set-hash! [loc]
  (set! (.-hash js/window.location) loc))

(defn patientcare-title []
  [:section.section [:div.container [:h1.title "PatientCare"] [:p.subtitle "Ward"]]])

(defn login-panel []
  (let [error (rf/subscribe [:user/error :login])
        username (reagent/atom "")
        password (reagent/atom "")
        submitting (rf/subscribe [:show-foreground-spinner])
        doLogin #(rf/dispatch [:user/user-login-start config/default-user-namespace (string/trim @username) @password])]
    (fn []
      (set-hash! "/login")
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
              [:input.input {:id          "login-pw" :type "password" :placeholder "Enter password" :required true
                             :disabled    @submitting
                             :on-key-down #(if (= 13 (.-which %))
                                             (do (reset! password (-> % .-target .-value)) (doLogin)))
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
        [:a.navbar-item {:href "#/"} [:h1 "PatientCare: " [:strong "Ward"]]]
        [:a.navbar-burger.burger {:role     "button" :aria-label "menu" :aria-expanded @show-nav-menu
                                  :class    (if @show-nav-menu :is-active "")
                                  :on-click #(swap! show-nav-menu not)}
         [:span {:aria-hidden "true"}]
         [:span {:aria-hidden "true"}]
         [:span {:aria-hidden "true"}]]]
       [:div.navbar-menu (when @show-nav-menu {:class :is-active})
        [:div.navbar-start
         [:a.navbar-item {:href "#/"} "Home"]
         (comment
           [:div.navbar-item.has-dropdown.is-hoverable
            [:a.navbar-link [:span "Messages\u00A0"] [:span.tag.is-danger.is-rounded 123]]
            [:div.navbar-dropdown
             [:a.navbar-item "Unread messages"]
             [:a.navbar-item "Archive"]
             [:hr.navbar-divider]
             [:a.navbar-item "Send a message..."]]]
           [:a.navbar-item "Teams"])

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
           [:a.navbar-item {:disabled true} "Report an issue"]]]
         [:a.navbar-item {:on-click #(rf/dispatch [:user/logout])} "Logout"]]]])))


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
            [:span (get-in @concept [:preferred-description :term])
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
           (let [preferred-id (get-in @concept [:preferred-description :id])]
             (doall (->> (sort-by :term (:descriptions @concept))
                         (filter #(case @active-panel
                                    :active-synonyms (:active %)
                                    :inactive-synonyms (not (:active %))
                                    :preferred-synonyms (= preferred-id (:id %))))
                         (filter #(not= (:type-id %) config/sct-fully-specified-name)) ;; exclude fully specified names
                         (map #(vector
                                 :p.panel-block.is-size-7 {:key (:id %)}
                                 (if (and (not= @active-panel :preferred-synonyms) (= preferred-id (:id %)))
                                   [:strong (:term %)]
                                   (:term %))))))))

         [:div.panel-block
          [:button.button.is-link.is-outlined.is-fullwidth "Save"]]]))))


(defn kp->path
  "Converts a key path such as [:patient.last-name] to a path [:patient :last-name]"
  [kp]
  (if (sequential? kp)
    kp
    (let [segments (str/split (subs (str kp) 1) #"\.")]
      (mapv keyword segments))))


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
        (rf/dispatch [:snomed/get-concept (:concept-id (first @results))]))
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
                   :on-change #(let [val (int (-> % (.-target) (.-value)))
                                     item (nth @results val)]
                                 (rf/dispatch [:snomed/get-concept (:concept-id item)])
                                 (reset! selected-index val))}
          (doall (map-indexed (fn [index item] [:option {:key index :value index} (:term item)]) @results))]]


        ;; if we have a selected result, show it
        (if (and (> (count @results) 0) (not (nil? @selected-index)))
          [snomed-show-concept (:concept-id (nth @results @selected-index))])]])))





(defn form-number-field
  [v kp & {:keys [placeholder size errors]}]
  {:pre [(vector? kp)]}
  [:div.control
   [:input.input
    (merge {:type      "number" :placeholder placeholder
            :value     (get-in @v kp)
            :on-change #(swap! v assoc-in kp (-> % .-target .-valueAsNumber))}
           (when-not (nil? size) {:size size})
           (if (contains? errors kp) {:class :is-danger})
           )]])


(defn form-select
  "Renders a HTML SELECT component
  :v where to store results (e.g. a map)
  :kp keypath under which to store results (e.g. [:fiO2])
  :options a sequence of options
  :display-key function to derive display from each option
  :value-key function to derive a key for each option"
  [& {:keys [v kp options display-key value-key no-selection-string errors]}]
  (let [value-map (apply merge (map #(hash-map (clj->js (value-key %)) %) options))]
    (when (and (nil? (get-in @v kp)) (not no-selection-string))
      (swap! v assoc-in kp (first options)))
    [:div.control
     [:div.select {:class (if (contains? errors kp) :is-danger)}
      [:select {:on-change #(swap! v assoc-in kp (get value-map (-> % .-target .-value)))}
       (when no-selection-string
         [:option {:value ""} no-selection-string])
       (doall (for [option options :let [value (value-key option)]]
                [:option {:key value :value value :selected (= (get-in @v kp) value)} (display-key option)]))]]]))

(defn oxygen-saturations
  "Component to record o2 saturation and air/oxygen/device, with results pushed as map to atom (v) via keypath (kp)"
  [v kp & {:keys [errors]}]
  {:pre [(vector? kp)]}
  [:div.field
   [:label.label "% Oxygen saturation"]
   [:div.field.is-grouped.is-grouped-multiline
    [:div.field.has-addons
     [:div.control
      [:input.input {:class     (if (contains? errors kp) :is-danger)
                     :type      "number" :placeholder "O2" :size 8
                     :value     (get-in @v (conj kp :o2-saturations :result))
                     :on-change #(swap! v assoc-in (conj kp :o2-saturations :result) (-> % .-target .-value))}]
      ]
     [:p.control [:a.button.is-static "%"]]]
    [:div.control
     [form-select :v v :kp (conj kp :o2-saturations :device) :options news/ventilation
      :display-key #(str (:abbreviation %) ": " (str/capitalize (str/replace (name (:value %)) "-" " ")))
      :value-key :value]]
    (when (some #{:fiO2} (get-in @v (conj kp :o2-saturations :device :properties)))
      [:<> [:div.field.has-addons [:div.control
                                   [form-number-field v [kp :o2-saturations :fiO2] :placeholder "FiO2" :size 8]]
            [:p.control [:a.button.is-static "%"]]]])
    (when (some #{:flow-rate} (get-in @v (conj kp :o2-saturations :device :properties)))
      [:<> [:div.field.has-addons [:div.control
                                   [form-number-field v [kp :o2-saturations :flow-rate] :placeholder "Flow rate" :size 8]]
            [:p.control
             [:a.button.is-static "L/min"]]]])]])



(defn respiratory-rate
  "Specialised component to record respiratory rate, with result pushed to atom (v) using key path (kp) (a vector of keys)"
  [v kp & {:keys [errors]}]
  {:pre [(vector? kp)]}
  (reagent/with-let [current-time (rf/subscribe [:current-time])
        timer (reagent/atom {:start nil :status :not-running :breaths 0})
        stop-timer #(let [duration (/ (- @current-time (:start @timer)) 1000) ;; milliseconds -> seconds
                          result (* (/ (:breaths @timer) duration) 60)]
                      (swap! v assoc-in kp (int result))
                      (reset! timer {:start nil :status :not-running :breaths 0}))
        start-timer #(reset! timer {:start @current-time :status :running :breaths 0})]
    [:div.field
     [:label.label "Respiratory rate "]
     (cond
       ;; timer isn't running, so show a text field and a button to start the timer
       (= (:status @timer) :not-running)
       [:div.field.has-addons
        [:div.control
         [:input.input {:class      (if (contains? errors kp) :is-error)
                        :type       "number" :placeholder " Respiratory rate " :value (get-in @v kp)
                        :auto-focus true
                        :on-change  #(swap! v assoc-in kp (-> % .-target .-valueAsNumber))}]
         ]
        [:p.control
         [:a.button.is-static "/min"]]
        [:div.control
         [:a.button.is-info {:on-click start-timer} "  Start timer  "]]]

       ;; timer is running, so show the countdown from 60 seconds and count the number of breaths recorded
       (= (:status @timer) :running)
       [:div.field.has-addons
        [:div.control.has-icons-left.has-icons-right.is-loading
         [:input.input {:type "text" :disabled true :value (str (:breaths @timer) " breaths in " (int (/ (- @current-time (:start @timer)) 1000)) "s")}]
         [:span.icon.is-small.is-left [:i.fas.fa-lungs]] (comment [:span.icon.is-small.is-right [:i.fas.fa-check]])]
        [:div.control
         [:a.button.is-info {:on-click #(swap! timer update-in [:breaths] inc)} "Count breath"]
         [:a.button.is-danger {:on-click stop-timer} "Stop timer"]]])
     [:p.help "Count the number of breaths in one minute"]]))

(def empty-news {:respiratory-rate nil
                 :pulse            nil
                 :temperature      nil
                 :spO2             nil
                 :ventilation      nil
                 :consciousness    nil})

(defn form-early-warning-score
  [save-func]
  (let [results (reagent/atom {})
        dt (reagent/atom (cljs-time.format/unparse (:date-hour-minute cljs-time.format/formatters) (cljs-time.core/now)))
        errors (reagent/atom nil)
        ignore-missing (reagent/atom false)
        ]
    (fn []
      [:div.columns
       [:div.column.is-7
        [respiratory-rate results [:respiratory-rate] :errors @errors :validate ::news/respiratory-rate]
        [oxygen-saturations results [:spO2] :errors @errors]
        ;;  [snomed-autocomplete results [:diagnosis] "Diagnosis" "errrr"]
        [:div.field [:label.label "Pulse rate"]
         [:div.field.has-addons
          [form-number-field results [:pulse] :size 8 :errors @errors]
          [:p.control [:a.button.is-static "beats/min"]]]]
        [:div.field [:label.label "Blood pressure"]
         [:div.field.has-addons
          [form-number-field results [:blood-pressure :systolic] :placeholder "Systolic" :size 8 :errors @errors]
          [:p.control [:a.button.is-static "/"]]
          [form-number-field results [:blood-pressure :diastolic] :placeholder "Diastolic" :size 8 :errors @errors]
          [:p.control [:a.button.is-static "mmHg"]]]]
        [:div.field [:label.label "Temperature"]
         [:div.field.has-addons
          [form-number-field results [:temperature] :size 8 :errors @errors]
          [:p.control
           [:a.button.is-static "ºC"]]]]
        [:div.field [:label.label "Consciousness"]
         [form-select :v results :kp [:consciousness] :options news/consciousness
          :display-key :display-name :value-key :value :no-selection-string "Not assessed" :errors @errors]
         [:p.help (get-in @results [:consciousness :description])]]]
       [:div.column.is-5
        (when-not (empty? @errors)
          [:article.message.is-danger
           [:div.message-header
            [:p "Validation error"]
            [:button.delete {:aria-label "delete"
                             :on-click   #(reset! errors nil)}]]
           [:div.message-body [:p "You have entered incorrect information. Please check and try again."]
            [:div.content [:ul (for [err @errors] [:li (apply #(str/capitalize (str/replace (str %) "-" " ")) (mapv name err))])]
             [:p [:label.checkbox
                  [:input {:type      "checkbox" :checked @ignore-missing
                           :on-change #(swap! ignore-missing not)}] " Ignore missing information"]]
             [:pre (s/explain-str ::news/news (if @ignore-missing @results (merge empty-news @results)))]]]])
        [:div.field
         [:label.label "Date / time of observation"]

         [:div.control
          [:input.input {:type      "datetime-local" :max (cljs-time.format/unparse (:date-hour-minute cljs-time.format/formatters) (cljs-time.core/now))
                         :value     @dt
                         :on-change #(let [val (-> % .-target .-valueAsNumber) ;; get unix timestamp from native datetime field
                                           date-time (cljs-time.coerce/from-long val)]
                                       (reset! dt (cljs-time.format/unparse (:date-hour-minute cljs-time.format/formatters) date-time)))
                         }]]]
        [:div.field
         [:div.buttons
          [:button.button.is-primary.is-large
           {:on-click #(let [e1 (s/explain-data ::news/news (if @ignore-missing @results (merge empty-news @results)))
                             e2 (map :path (:cljs.spec.alpha/problems e1))
                             e3 (conj e2 (if (time-core/after? (cljs-time.format/parse (:date-hour-minute cljs-time.format/formatters) @dt) (time-core/now)) [:date-time] []))
                             e4 (remove empty? e3)
                             has-errors (seq e4)]
                         (if has-errors (do (print "validation errors: " e4)
                                            (reset! errors (set e4)))
                                        (do (js/console.log "wooohoo no validation errors!")
                                            (reset! errors [])
                                            (print @results))))} "Save"]]]]])))

(defn show-patient-name
  "Nicely displays a patient name"
  [patient]
  [:<>
   (when (concierge/patient-deceased? patient) "Deceased")
   (clojure.string/join " " [(:title patient) (:firstnames patient) (:lastname patient)])])


(defn format-nhs-number
  "Formats an NHS number into the standard (3) (3) (4) pattern"
  [nnn]
  (apply str (remove nil? (interleave nnn [nil nil " " nil nil " ", nil nil nil nil]))))


(defn patient-banner
  "Show a patient banner, options can include :wide to include patient address"
  [patient opts]
  (fn []
    (let [nnn (first (concierge/identifiers-for-system patient (:nhs-number concierge/systems)))
          address (first (concierge/active-addresses (:addresses patient)))
          cav-crn (first (concierge/identifiers-for-system patient (:cardiff-pas concierge/systems)))]
      [:div.columns
       [:div.column.is-narrow [:strong [show-patient-name patient]]]
       (if (concierge/patient-deceased? patient)
         [:div.column.is-narrow [:span.tag "Deceased"]]
         [:div.column.is-narrow
          (concierge/format-date (concierge/parse-date (:birth-date patient))) ": "
          (concierge/format-patient-age patient)])
       (when-not (nil? nnn) [:div.column.is-narrow [:strong (format-nhs-number nnn)]])
       (when-not (nil? cav-crn) [:div.column.is-narrow cav-crn])
       (when (:wide opts)
         [:div.column.has-text-right (str/join ", " [(:address-1 address) (:address-2 address) (:address-3 address) (:postcode address)])])

       ])))



(def news-data [
                {:date-time        (time-core/date-time 2020 4 9 9 3 27)
                 :respiratory-rate 12 :pulse-rate 110 :blood-pressure {:systolic 82 :diastolic 54}}
                {:date-time        (time-core/date-time 2020 4 9 15 3 27)
                 :respiratory-rate 14 :pulse-rate 90 :blood-pressure {:systolic 142 :diastolic 70}}
                {:date-time        (time-core/date-time 2020 4 12 9 3 27)
                 :respiratory-rate 10 :pulse-rate 82 :temperature 39.5
                 :spO2             98 :air-or-oxygen :air
                 :blood-pressure   {:systolic 120 :diastolic 90}
                 :consciousness    :clin/alert}

                {:date-time        (time-core/date-time 2020 4 12 11 3 27)
                 :respiratory-rate 10 :pulse-rate 82 :temperature 39.5
                 :spO2             70 :air-or-oxygen :air
                 :blood-pressure   {:systolic 90 :diastolic 60}
                 :consciousness    :clin/confused}

                {:date-time (time-core/date-time 2020 4 14 9 3 27) :respiratory-rate 22, :spO2 94 :consciousness :clin/alert}
                {:date-time (time-core/date-time 2020 4 14 17 3 27) :respiratory-rate 22 :spO2 98 :conscioussness :clin/alert}
                {:date-time (time-core/date-time 2020 4 15 10 2 45) :respiratory-rate 18 :pulse-rate 85}
                {:date-time      (time-core/date-time 2020 4 17 10 2 45) :respiratory-rate 12 :spO2 97
                 :blood-pressure {:systolic 138 :diastolic 82}}
                {:date-time (time-core/date-time 2020 4 18 7 2 45) :respiratory-rate 30}
                {:date-time (time-core/date-time 2020 4 18 9 2 45) :respiratory-rate 28 :pulse-rate 90}
                {:date-time (time-core/date-time 2020 4 18 12 2 45) :respiratory-rate 23 :pulse-rate 110}
                {:date-time (time-core/date-time 2020 4 18 19 2 45) :respiratory-rate 8 :consciousness :clin/alert}
                {:date-time (time-core/date-time 2020 4 8 22 2 45) :respiratory-rate 14 :pulse-rate 120 :spO2 95 :air-or-oxygen :O2}
                {:date-time (time-core/date-time 2020 4 8 10 2 45) :respiratory-rate 12 :pulse-rate 140}
                {:date-time (time-core/date-time 2020 4 7 9 2 45) :respiratory-rate 22 :pulse-rate 90 :spO2 88 :consciousness :clin/confused :blood-pressure {:systolic 201 :diastolic 110}}
                {:date-time (time-core/date-time 2020 4 6 11 2 45) :respiratory-rate 12}
                {:date-time (time-core/date-time 2020 4 5 11 2 45) :respiratory-rate 12 :temperature 37.2}
                {:date-time (time-core/date-time 2020 4 4 11 2 45) :respiratory-rate 12 :consciousness :clin/unresponsive}
                {:date-time (time-core/date-time 2020 4 19 9 2 45) :respiratory-rate 12 :consciousness :clin/alert :pulse-rate 95 :temperature 37.2}
                {:date-time (time-core/date-time 2020 5 6 9 11 12) :respiratory-rate 12 :consciousness :clin/unresponsive}
                {:date-time     (time-core/date-time 2020 5 8 18 11 12) :respiratory-rate 25 :consciousness :clin/unresponsive :spO2 78
                 :air-or-oxygen :air :pulse-rate 130 :blood-pressure {:systolic 200 :diastolic 80} :temperature 40}
                ])


(defn render-data
  "Simple rendering of the NEWS data as a table. In time, this will evolve to a generic
  view encounters within an episode / across multiple episodes."
  [patient]
  (fn []
    (let [sorted-data (sort-by :date-time #(time-core/after? %1 %2) news-data)
          scored-data (news/score-all-news sorted-data false)] ;; TODO: deal with hypercapnia scoring]
      [:<>
       [:table.table.is-striped.is-full-width
        [:thead
         [:tr [:th " Date / time" [:span.icon.is-small.is-left [:i.fas.fa-angle-down]]]
          [:th "RR"] [:th "% O" [:sub "2"] ""] [:th "P"] [:th "BP"] [:th "Con"] [:th "T ºC"]
          [:th "NEWS"]]]
        [:tbody
         (doall (for [{x :results :as w} scored-data]
                  [:tr [:td (concierge/format-date-time (:date-time w))]
                   [:td (:respiratory-rate x)]
                   [:td (:spO2 x)]
                   [:td (:pulse-rate x)]
                   [:td (when (:blood-pressure x) (str (get-in x [:blood-pressure :systolic]) "/" (get-in x [:blood-pressure :diastolic])))]
                   [:td (string/capitalize (name (get x :consciousness "")))]
                   [:td (:temperature x)]
                   [:td (:news-score w)]]))]]])))


;; (doall (map #(let [x (+ 56 (calculate-x-for-scale start-date (:date-time %) scale))
;                        y (+ 2.5 (* 5 ((:indexed-by chart) (get % value-key) (:categories chart))))]
;                    (vector
;                      :circle {:cx (+ 3.5 x) :cy (+ start-y y) :r "0.2" :stroke "black" :fill "black" :key (:date-time %)})
;                    ) sorted-data))




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





;;  (comment
;        [:div.card
;         [:header.card-header
;          [:p.card-header-title
;           (clojure.string/join " " [(:title patient) (:firstnames patient) (:lastname patient)])]
;          [:div.level
;           [:div.level-left]
;           [:div.level-right
;            [:div.level
;             [:div.level-left (first (concierge/identifiers-for-system patient (:cardiff-pas concierge/systems)))]
;             (when-not (nil? nnn) [:div.column (format-nhs-number nnn)])
;             [:div.level-right (string/join " " (rest (concierge/identifiers-for-system patient (:cardiff-pas concierge/systems))))]]]
;           ]
;          (when (concierge/patient-deceased? patient) [:span.card-header-icon " (Deceased)"])]
;


;;       ])))
;  )


(defn show-patient
  [patient confirm-func cancel-func]
  (let [nnn (first (concierge/identifiers-for-system patient (:nhs-number concierge/systems)))]
    (fn [patient confirm-func cancel-func]
      [:div.card
       [:header.card-header
        [:p.card-header-title
         [show-patient-name patient]]]

       [:div.card-content
        [:div.columns
         [:div.column

          [:table.table
           [:tbody
            (when-not (nil? nnn)
              [:tr [:th "NHS number: "] [:td (format-nhs-number nnn)]])
            [:tr [:th "Date of birth:"] [:td (concierge/format-date (concierge/parse-date (:birth-date patient)))]]
            (let [address (first (concierge/active-addresses (:addresses patient) (time-core/now)))]
              [:tr
               [:th "Address:"]
               [:td (:address-1 address) [:br] (:address-2 address) [:br] (:address-3 address) [:br] (:postcode address) [:br] (:country address)]]
              )
            ]]
          ]
         [:div.column
          [:table.table
           [:tbody
            [:tr [:th "Hospital number:"] [:td (first (concierge/identifiers-for-system patient (:cardiff-pas concierge/systems)))]]
            (cond
              (not (concierge/patient-deceased? patient)) [:tr [:th "Current age:"] [:td (concierge/format-patient-age patient)]]
              (string/blank? (:deceased-date patient)) [:tr [:th "Deceased:"] [:td "Yes"]]
              :else [:tr [:th "Date of death:"] [:td (concierge/format-date (concierge/parse-date (:deceased-date patient)))]]
              )
            (if (> (count (:telephones patient)) 0)
              (do [:tr [:th "Telephone:"]
                   [:td (for [tel (:telephones patient)]
                          [:<> (:number tel) [:br]])]]))    ;; TODO: add mechanism to get description for phone no
            (if (> (count (:emails patient)) 0)
              (do [:tr [:th "Email:"]
                   [:td (for [email (:emails patient)]
                          [:<> email [:br]])]]))]]]]]

       [:footer.card-footer
        (if-not (nil? confirm-func) [:a.card-footer-item.is-link {:on-click confirm-func} "View record"])
        (if-not (nil? cancel-func) [:a.card-footer-item {:on-click cancel-func} "Cancel"])]
       ])))


(defn home-panel []
  (let [
        search (reagent/atom "")
        search-results (rf/subscribe [:patient/search-results])
        error (rf/subscribe [:patient/search-error])]

    (fn []
      (set-hash! "/home")
      [:<>
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
                 [show-patient @search-results
                  #(rf/dispatch [:patient/show @search-results])
                  #(do (reset! search "")
                       (.focus (.getElementById js/document "search-field-id"))
                       (rf/dispatch [:patient/clear-search]))])))]]]]])))





(defn togglable-panel
  "Creates a togglable panel with the specified title and whether to be open or closed by default, wrapping the specified content"
  [title show content]
  (let [show-panel (reagent/atom show)]
    (fn []
      [:div.card
       [:header.card-header
        [:p.card-header-title {:on-click #(swap! show-panel not)} title]
        [:a.card-header-icon {:aria-label "more options"}
         [:span.icon {:on-click #(swap! show-panel not)}
          (if @show-panel [:i.fas.fa-angle-down {:aria-hidden "true"}] [:i.fas.fa-angle-right {:aria-hidden "true"}])]]]
       (when @show-panel
         [:div.card-content
          [:div.content content]])])))


(defn patient-panel []
  (let [patient (rf/subscribe [:patient/current])
        selected-tab (reagent/atom :add)
        scale (reagent/atom :consecutive)
        default-start-date (news/default-start-date @scale 28 news-data)
        drawing-start-date (reagent/atom default-start-date)
        hypercapnic? (reagent/atom true)]
    (fn []
      (set-hash! "/patient")
      [:<>
       [nav-bar]
       [:section.section
        [:div.container
         [:div.box
          [patient-banner @patient #{:wide}]
          [:hr]
          [:div.tabs.is-boxed
           [:ul
            [:li {:class (if (= :data @selected-tab) "is-active")} [:a {:on-click #(reset! selected-tab :data)} "NEWS"]]
            [:li {:class (if (= :chart @selected-tab) "is-active")} [:a {:on-click #(reset! selected-tab :chart)} "Chart"]]
            [:li {:class (if (= :add @selected-tab) "is-active")} [:a {:on-click #(reset! selected-tab :add)} "Add"]]
            ]

           ]
          (cond
            (= :data @selected-tab)
            [render-data @patient]
            (= :chart @selected-tab)
            [:<>
             [:nav.level
              [:div.level-left
               [:div.buttons.is-centered
                [:div.tabs.is-toggle
                 [:ul
                  [:li [:a {:on-click #(swap! drawing-start-date (fn [old] (time-core/minus old (time-core/days 7))))} " << "]]
                  [:li [:a {:on-click #(swap! drawing-start-date (fn [old] (time-core/minus old (time-core/days 1))))} " < "]]
                  [:li (if (= @scale :days) {:class "is-active"})
                   [:a {:on-click #(reset! scale :days)}
                    [:span "By day"]]]
                  [:li (if (= @scale :consecutive) {:class "is-active"})
                   [:a {:on-click #(reset! scale :consecutive)}
                    [:span "Consecutive"]]]
                  [:li [:a {:on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 1))))} " > "]]
                  [:li [:a {:on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 7))))} " >> "]]
                  [:li [:a {:on-click #(reset! drawing-start-date default-start-date)} "Today "]]
                  ]]
                ]]
              [:div.level-right
               [:label.checkbox
                [:input {:type "checkbox" :checked @hypercapnic? :on-click #(swap! hypercapnic? not)}] "Hypercapnic respiratory failure"]]]
             [:div
              [news/render-news-chart {:scale @scale} @drawing-start-date news-data @hypercapnic?]
              ]]
            (= :add @selected-tab)
            [form-early-warning-score #()]
            :else [:div])

          (comment
            [:div.columns
             [:div.column.is-3
              [:aside.menu
               [:p.menu-label "Overview"]
               [:ul.menu-list
                [:li [:a.is-active "Record observations"]]
                [:li [:a "NEWS chart"]]]
               [:p.menu-label "Active episodes"]
               [:ul.menu-list
                [:li [:a "Inpatient - ward C6"]]
                [:li [:a "Helen Durham neuro-inflammatory unit"]]
                [:li [:a "General cardiology"]]]
               ]

              ]
             [:div.column

              [togglable-panel "National Early Warning Score" false
               [form-early-warning-score #()]
               ]
              [:hr]
              [togglable-panel "Glasgow coma scale" false
               [:div "Yo ho ho and a bottle of rum"]]
              [:hr]
              [togglable-panel "Problems / diagnoses" false
               [:div "Yo ho ho and a bottle of rum"]]
              [:hr]
              [togglable-panel "Notes" false
               [:div "Yo ho ho and a bottle of rum"]]

              [:hr]
              [togglable-panel "Standing orders" false
               [:div "Yo ho ho and a bottle of rum"]]
              ]

             ]
            )
          ]]]

       ])))






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
    :login-panel [login-panel]
    :about-panel [about-panel]
    :patient-panel [patient-panel]
    [:div]))


(defn show-panel [panel-name]
  [panels panel-name])

(defn main-page []
  "The main page; ensures we have an authenticated user and shows a login page if not"
  (let [authenticated-user (rf/subscribe [:user/authenticated-user])
        active-panel (rf/subscribe [::subs/active-panel])]
    (fn [] (if (nil? @authenticated-user)
             (do
               [rf/dispatch [:pc-ward.events/set-active-panel :login]]
               [login-panel])
             [show-panel @active-panel]))))


