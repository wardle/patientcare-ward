(ns pc-ward.views
  (:require
    [re-frame.core :as rf]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [cljs-time.core :as time-core]
    [cljs-time.format :as time-format]
    [pc-ward.subs :as subs]
    [pc-ward.clinical :as clin]
    ))

;; conceivably our UI could allow selection from a list of "providers".
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


(defn snomed-show-concept
  "Display read-only information about a concept in a panel"
  [concept-id]
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
                         (filter #(not= (:type_id %) "900000000000003001")) ;; exclude fully specified names
                         (map #(vector
                                 :p.panel-block.is-size-7 {:key (:id %)}
                                 (if (and (not= @active-panel :preferred-synonyms) (= preferred-id (:id %)))
                                   [:strong (:term %)]
                                   (:term %))
                                 ))))))
         [:div.panel-block
          [:button.button.is-link.is-outlined.is-fullwidth "Save"]]]
        ))))

(defn snomed-autocomplete
  [v kp {name   :name                                       ;; name for this, eg. diagnosis
         common :common                                     ;; list of common concepts, if present will be shown in pop-up list
         is-a   :is-a                                       ;; vector containing is-a constraints
         }]
  {:pre [(vector? kp)]}
  (let [
        search (reagent/atom "")
        selected-index (reagent/atom nil)
        results (rf/subscribe [:snomed/results ::new-diagnosis])]
    (fn [v kp props]
      (if (and (= 0 @selected-index) (> (count @results) 0)) ;; handle special case of typing in search to ensure we start fetching first result
        (do
          (rf/dispatch [:snomed/get-concept (:concept_id (first @results))])))
      [:div
       [:div.field
        [:label.label name]
        [:div.control
         [:input.input {:type      "text" :placeholder name :value @search
                        :on-change #(do
                                      (reset! selected-index 0)
                                      (reset! search (-> % .-target .-value))
                                      (rf/dispatch [:snomed/search-later ::new-diagnosis {:s        (-> % .-target .-value)
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
          (doall (map-indexed (fn [index item] [:option {:key index :value index} (:term item)]) @results))
          ]]

        ;; if we have a selected result, show it
        (if (and (> (count @results) 0) (not (nil? @selected-index)))
          [snomed-show-concept (:concept_id (nth @results @selected-index))])

        ]])))



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



;; colours from the RCP NEWS2 chart
(def news-colour-score-3 "#E89078")
(def news-colour-score-2 "#F4C487")
(def news-colour-score-1 "#FFF0A8")
(def news-colour-dark-blue "#36609D")
(def news-colour-light-blue "#ACB3D1")
(def news-colour-abcde "#7487B6")


(def rr-data [
              {:date-time (time-core/date-time 2020 4 9 9 3 27) :respiratory-rate 12}

              {:date-time (time-core/date-time 2020 4 12 9 3 27) :respiratory-rate 10}

              {:date-time (time-core/date-time 2020 4 14 9 3 27) :respiratory-rate 22}
              {:date-time (time-core/date-time 2020 4 14 17 3 27) :respiratory-rate 22}
              {:date-time (time-core/date-time 2020 4 15 10 2 45) :respiratory-rate 18}
              {:date-time (time-core/date-time 2020 4 17 10 2 45) :respiratory-rate 12}
              {:date-time (time-core/date-time 2020 4 18 7 2 45) :respiratory-rate 30}
              {:date-time (time-core/date-time 2020 4 18 9 2 45) :respiratory-rate 28}
              {:date-time (time-core/date-time 2020 4 18 12 2 45) :respiratory-rate 23}
              {:date-time (time-core/date-time 2020 4 18 19 2 45) :respiratory-rate 8}
              {:date-time (time-core/date-time 2020 4 21 22 2 45) :respiratory-rate 14}
              {:date-time (time-core/date-time 2020 4 22 10 2 45) :respiratory-rate 12}
              {:date-time (time-core/date-time 2020 4 23 9 2 45) :respiratory-rate 22}
              {:date-time (time-core/date-time 2020 4 30 11 2 45) :respiratory-rate 12}
              {:date-time (time-core/date-time 2020 5 7 11 2 45) :respiratory-rate 12}

              ])

(def respiratory-rate-ranges
  [[25 nil]
   [21 24]
   [18 20]
   [15 17]
   [12 14]
   [9 11]
   [nil 8]])

(defn in-range?
  "Is the number (n) within the range defined, inclusive, handling special case of missing start or end"
  [n start end]
  (cond (and (>= n start) (nil? end)) true
        :else (<= start n end)))                            ;; <= handles a nil 'start' but not a nil 'end'

(defn range-to-label
  "Turns a range (vector of two) into a label"
  [[start end]]
  (cond (nil? end) (str "   ≥ " start)
        (nil? start) (str "   ≤ " end)
        :else (str start " - " end)))

(defn respiratory-rate-labels
  "Generate SVG labels for respiratory rate categories"
  [start-y]
  (->> respiratory-rate-ranges
       (map-indexed (fn [index item]
                      (vector :text {:key item :x 44 :y (+ start-y 4 (* index 5)) :fill "black" :font-size 4 :text-anchor "middle"}
                              (range-to-label item))))))

(defn calculate-respiratory-rate-category
  "Calculate the category for a given respiratory rate - returning index."
  [rr]
  (first (keep-indexed (fn [index item] (if (in-range? rr (nth item 0) (nth item 1)) index nil)) respiratory-rate-ranges)))



(defn test-drawing [start-date]
  (let [width-in-boxes 28                                   ;; number of boxes to show
        box-width 7                                         ;; the viewbox is based on the paper NEWS chart in millimetres, so our internal scale is same as "millimetres"
        box-height 5
        left-column-panel-width 32                          ;; the dark blue left column
        left-column-label-width 24                          ;; the labels     ;; 32+24 = 56 which is a multiple of 7
        left-column-width (+ left-column-panel-width left-column-label-width)
        width (* width-in-boxes box-width)
        dt-start-y 0
        rr-start-y 15
        end-date (time-core/plus start-date (time-core/days width-in-boxes))
        scale :days                                         ;; :12-hourly :6-hourly :4-hourly :hourly       ;; could offer to switch between
        calculate-x-for-days (fn [start-date date] (+ left-column-width (* 7 (time-core/in-days (time-core/interval start-date date)))))
        custom-formatter (time-format/formatter "dd MMM yyyy")
        day-formatter (time-format/formatter "dd")
        month-formatter (time-format/formatter "MM")
        dates (->> (range 0 width-in-boxes)                 ;; each box represents another day
                   (map #(time-core/plus start-date (time-core/days %)))
                   (map #(vector (time-format/unparse day-formatter %) (time-format/unparse month-formatter %))))
        sorted-rr-data (->> rr-data
                            (filter #(and (time-core/after? (:date-time %1) start-date) (time-core/before? (:date-time %1) end-date)))
                            (sort-by :date-time #(time-core/before? %1 %2))
                            )
        ]

    [:svg {:width "100%" :viewBox "0 0 255 391" :xmlns "http://www.w3.org/2000/svg"}

     [:defs
      [:pattern#grid-score-3 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width "7" :height "5" :fill news-colour-score-3}]
       [:path {:d "M 100 0 L 0 0 0 100" :fill "none" :stroke "black" :stroke-width "0.5"}]]
      [:pattern#grid-score-2 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width "7" :height "5" :fill news-colour-score-2}]
       [:path {:d "M 100 0 L 0 0 0 100" :fill "none" :stroke "black" :stroke-width "0.5"}]]
      [:pattern#grid-score-1 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width "7" :height "5" :fill news-colour-score-1}]
       [:path {:d "M 100 0 L 0 0 0 100" :fill "none" :stroke "black" :stroke-width "0.5"}]]
      [:pattern#grid-score-0 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width "7" :height "5" :fill "none"}]
       [:path {:d "M 100 0 L 0 0 0 100" :fill "none" :stroke "black" :stroke-width "0.5"}]]
      [:pattern#grid-score-0-tall {:width "7" :height "10" :patternUnits "userSpaceOnUse"}
       [:rect {:width "7" :height "10" :fill "none"}]
       [:path {:d "M 100 0 L 0 0 0 100" :fill "none" :stroke "black" :stroke-width "0.5"}]]
      ]

     ;; date / time
     [:rect {:x 0 :y (+ dt-start-y 0) :width left-column-panel-width :height 10 :stroke "black" :fill "none" :stroke-width 0.1}]
     [:text {:x 5 :y (+ dt-start-y 6) :fill "black" :font-size 4} (time-format/unparse custom-formatter start-date)]
     [:rect {:x left-column-panel-width :y (+ dt-start-y 0) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ dt-start-y 5) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:text {:x "40" :y (+ dt-start-y 4) :fill "black" :font-size "4"} "Date"]
     [:text {:x "40" :y (+ dt-start-y 9) :fill "black" :font-size "4"} "Month"]
     [:rect {:x left-column-width :y (+ dt-start-y 0) :width width :height "10" :fill "url(#grid-score-0"}]
     (doall (map-indexed (fn [index item]
                           [:text {:x (+ left-column-width 2 (* index 7)) :y (+ dt-start-y 4) :fill "black" :font-size "3" :key item} (nth item 0)]
                           ) dates))
     (doall (map-indexed (fn [index item]
                           [:text {:x (+ left-column-width 2 (* index 7)) :y (+ dt-start-y 9) :fill "black" :font-size "3" :key item} (nth item 1)]) dates))


     ;; respiratory rate  - 5 rows
     [:rect {:x 0 :y (+ rr-start-y 0) :width left-column-panel-width :height 35 :fill news-colour-dark-blue}]
     [:text {:x 5 :y (+ rr-start-y 14) :fill news-colour-abcde :font-size "12"} "A+B"]
     [:text {:x 5 :y (+ rr-start-y 20) :fill "white" :font-size "4" :font-weight "bold"} "Respirations"]
     [:text {:x 5 :y (+ rr-start-y 25) :fill "white" :font-size "3"} "Breaths/min"]

     [:rect {:x left-column-panel-width :y (+ rr-start-y 0) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 5) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 10) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 15) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 20) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 25) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ rr-start-y 30) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     ;; RR labels
     (respiratory-rate-labels rr-start-y)

     [:rect {:x left-column-width :y (+ rr-start-y 0) :width width :height "5" :fill "url(#grid-score-3)"}]
     [:rect {:x left-column-width :y (+ rr-start-y 5) :width width :height "5" :fill "url(#grid-score-2"}]
     [:rect {:x left-column-width :y (+ rr-start-y 10) :width width :height "15" :fill "url(#grid-score-0"}]
     [:rect {:x left-column-width :y (+ rr-start-y 25) :width width :height "5" :fill "url(#grid-score-1"}]
     [:rect {:x left-column-width :y (+ rr-start-y 30) :width width :height "5" :fill "url(#grid-score-3"}]

     ;;
     ;; draw results from data
     (doall (map #(
                    let [x (calculate-x-for-days start-date (:date-time %))
                         y (+ 2.5 (* 5 (calculate-respiratory-rate-category (:respiratory-rate %))))
                         ]
                    (vector
                      :circle {:cx (+ 3.5 x) :cy (+ rr-start-y y) :r "0.2" :stroke "black" :fill "black" :key (:date-time %)})
                    ) sorted-rr-data))


     [:polyline {:points (doall (flatten (map #(
                                                 let [x (calculate-x-for-days start-date (:date-time %))
                                                      y (+ 2.5 (* 5 (calculate-respiratory-rate-category (:respiratory-rate %))))
                                                      ]
                                                 (vector (+ 3.5 x) (+ rr-start-y y))
                                                 ) sorted-rr-data)))
                 :fill   "none" :stroke "black" :stroke-width 0.2 :stroke-dasharray "1 1"
                 }]


     ;;  [:rect {:x 0 :y 0 :width "32" :height "45" :fill news-colour-dark-blue}]
     ;; [:line {:x1 "0" :y1 "1" :x2 "100%" :y2 "1" :stroke "black" :stroke-width "2"}]
     ;;   [:line {:x1 "0" :y1 "35" :x2 "100%" :y2 "35" :stroke "black" :stroke-width "2"}]



     (comment
       ;; spO2 scale -1
       [:rect {:x 0 :y "40" :width "100%" :height "5" :fill "url(#grid-score-0"}]
       [:rect {:x 0 :y "45" :width "100%" :height "5" :fill "url(#grid-score-1"}]
       [:rect {:x 0 :y "50" :width "100%" :height "5" :fill "url(#grid-score-2"}]
       [:rect {:x 0 :y "55" :width "100%" :height "5" :fill "url(#grid-score-3"}]

       ;; spO2 scale -2
       [:rect {:x 0 :y "65" :width "100%" :height "5" :fill "url(#grid-score-3"}]
       [:rect {:x 0 :y "70" :width "100%" :height "5" :fill "url(#grid-score-2"}]
       [:rect {:x 0 :y "75" :width "100%" :height "5" :fill "url(#grid-score-1"}]
       [:rect {:x 0 :y "80" :width "100%" :height "10" :fill "url(#grid-score-0"}]
       [:rect {:x 0 :y "90" :width "100%" :height "5" :fill "url(#grid-score-1"}]
       [:rect {:x 0 :y "95" :width "100%" :height "5" :fill "url(#grid-score-2"}]
       [:rect {:x 0 :y "100" :width "100%" :height "5" :fill "url(#grid-score-3"}]

       ;; air or oxygen
       [:rect {:x 0 :y "110" :width "100%" :height "5" :fill "url(#grid-score-0"}]
       [:rect {:x 0 :y "115" :width "100%" :height "5" :fill "url(#grid-score-1"}]
       [:rect {:x 0 :y "120" :width "100%" :height "10" :fill "url(#grid-score-0-tall"}]

       ;; blood pressure
       [:rect {:x 0 :y "135" :width "100%" :height "5" :fill "url(#grid-score-3"}]
       [:rect {:x 0 :y "140" :width "100%" :height "30" :fill "url(#grid-score-0"}]
       [:rect {:x 0 :y "170" :width "100%" :height "5" :fill "url(#grid-score-1"}]
       [:rect {:x 0 :y "175" :width "100%" :height "5" :fill "url(#grid-score-2"}]
       [:rect {:x 0 :y "180" :width "100%" :height "25" :fill "url(#grid-score-3"}]
       )
     ]
    )
  )



(defn form-early-warning-score
  [save-func]
  (let [results (reagent/atom nil)
        drawing-start-date (reagent/atom (time-core/date-time 2020 4 4))]
    (fn [value]
      [:div

      ;; [snomed-autocomplete results [:diagnosis] "Diagnosis" "errrr"]

       [:button.button {:class    "is-primary"
                         :on-click #(swap-vals! drawing-start-date (fn [old] (time-core/minus old (time-core/days 7))))} " << "]
       [:button.button {:class    "is-primary"
                        :on-click #(swap-vals! drawing-start-date (fn [old] (time-core/minus old (time-core/days 1))))} " < "]

      [:button.button {:class    "is-primary"
                         :on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 1))))} " > "]
       [:button.button {:class    "is-primary"
                        :on-click #(swap! drawing-start-date (fn [old] (time-core/plus old (time-core/days 7))))} " >> "]

       [test-drawing @drawing-start-date]

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

           (comment
             [:article.message.is-warning
              [:div.message-header
               [:h2 "Welcome to PatientCare: Ward"]]
              [:div.message-body
               [:p "This an inpatient ward application, designed in response to the SARS-CoV-2 global pandemic. "]
               [:p "It is designed to support inpatient patient management, primarily via the recording and
           presentation of electronic observations, calculation of escalation scores and provision of checklists"]
               ]]
             )
           [:article-message.is-danger
            [:div.message-header [:p "Add diagnosis"]]
            [:div.message-body

             [form-early-warning-score nil]

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
        [show-panel @active-panel]
        ))))


(comment

  #(case (.-which %)
     13 (" It's thirteen ")                                 ; enter
     20 (" It's twenty ")                                   ; esc
     nil)

  )