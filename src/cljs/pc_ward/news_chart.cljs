(ns pc-ward.news-chart
  (:require
    [cljs-time.core :as time-core]
    [cljs-time.format :as time-format]
    ))



;; colours from the RCP NEWS2 chart
(def colour-score-3 "#E89078")
(def colour-score-2 "#F4C487")
(def colour-score-1 "#FFF0A8")
(def colour-dark-blue "#36609D")
(def colour-light-blue "#ACB3D1")
(def colour-abcde "#7487B6")


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

;; this is the default categoriser
(defn index-by-numeric-range
  "Calculates an 'index' for a category by taking a numeric value and identifying the range from a vector of vectors, containing ranges."
  [v ranges]
  (first (keep-indexed (fn [index item] (if (in-range? v (nth item 0) (nth item 1)) index nil)) ranges)))

(defn index-by-category
  "Calculates an 'index' for a category based on simple equivalence"
  [v categories]
  (first (keep-indexed (fn [index item] (if (= v item) index nil)) categories))
  )

(defn draw-labels
  "Generate SVG labels for categories"
  [x start-y categories]
  (map-indexed (fn [index item]
                 (vector :text {:key item :x x :y (+ start-y 4 (* index 5)) :fill "black" :font-size 4 :text-anchor "middle"}
                         (cond
                           (vector? item) (range-to-label item) ;; turn a vector into a label based on range
                           (keyword? item) (name item)      ;; turn keywords into simple labels
                           (string? item) item              ;; use a string if specified
                           :else ""
                           )
                         )) categories))

(defn draw-chart-axes
  "Generates an SVG chart"
  [y plot-width {:keys [heading title subtitle categories labels scores indexed-by]}]
  (let [n-categories (count categories)
        total-height (* 5 n-categories)
        titles-height (+ 8 2 4 2 3 4)
        hy (+ y (- (/ total-height 2) (/ titles-height 2)))
        ]
    [:<>
     [:rect {:x 0 :y (+ y 0) :width 32 :height total-height :fill colour-dark-blue}]
     [:text {:x 16 :y (+ hy 10) :fill colour-abcde :font-size "10" :text-anchor "middle"} heading]
     [:text {:x 16 :y (+ hy 15) :fill "white" :font-size "4" :font-weight "bold" :text-anchor "middle"} title]
     [:text {:x 16 :y (+ hy 18) :fill "white" :font-size "3" :text-anchor "middle"} subtitle]
     (draw-labels 44 y (if (nil? labels) categories labels))

     (for [i (range n-categories)]
       [:<>
        [:rect {:x 32 :y (+ y (* i 5)) :width 24 :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
        [:rect {:x 56 :y (+ y (* i 5)) :width plot-width :height "5" :fill (str "url(#grid-score-" (nth scores i) ")")}]
        ]
       )
     ]))

;; TODO: add support for other scales
(defn calculate-x-for-scale
  "Calculate the (x) for a date in the given scale"
  [start-date date-time scale]
  (case scale
    :day (* 7 (time-core/in-days (time-core/interval start-date date-time))))
  )

;; TODO: add support for other scales
(defn calculate-end-date-for-scale
  "Calculate the plot width for a given scale"
  [start-date number-boxes scale]
  (case scale
    :day (time-core/plus start-date (time-core/days number-boxes)))) ;; each box is a day.

(defn plot-results
  "Plots the results for a chart; designed to be used in conjunction with draw-chart to draw the scales/grid"
  [start-y start-date plot-width scale chart data value-key]
  (let [end-date (calculate-end-date-for-scale start-date plot-width scale)
        sorted-data (->> data
                         (filter #(and (time-core/after? (:date-time %1) start-date) (time-core/before? (:date-time %1) end-date)))
                         (filter #(not (nil? (get %1 value-key))))
                         (sort-by :date-time #(time-core/before? %1 %2)))]

    [:<>
     (doall (map #(let [x (+ 56 (calculate-x-for-scale start-date (:date-time %) scale))
                        y (+ 2.5 (* 5 ((:indexed-by chart) (get % value-key) (:categories chart))))]
                    (vector
                      :circle {:cx (+ 3.5 x) :cy (+ start-y y) :r "0.2" :stroke "black" :fill "black" :key (:date-time %)})
                    ) sorted-data))
     [:polyline {:points (doall (flatten (map #(
                                                 let [x (+ 56 (calculate-x-for-scale start-date (:date-time %) scale))
                                                      y (+ 2.5 (* 5 ((:indexed-by chart) (get % value-key) (:categories chart))))]
                                                 (vector (+ 3.5 x) (+ start-y y))
                                                 ) sorted-data)))
                 :fill   "none" :stroke "black" :stroke-width 0.2 :stroke-dasharray "1 1"
                 }]]))



;; charts defines our charts, their headings, the categories and the scores
(def charts
  {:respiratory-rate {:heading    "A+B"
                      :title      "Respirations"
                      :subtitle   "Breaths/min"
                      :categories [[25 nil] [21 24] [18 20] [15 17] [12 14] [9 11] [nil 8]]
                      :scores     [3 2 0 0 0 1 3]
                      :indexed-by index-by-numeric-range
                      }
   :sp02-scale-1     {:heading    "A+B"
                      :title      "SpO2 scale 1"
                      :subtitle   "Oxygen sats (%)"
                      :categories [[96 nil] [94 95] [92 93] [nil 91]]
                      :scores     [0 1 2 3]
                      :indexed-by index-by-numeric-range
                      }
   :air-or-oxygen    {
                      :heading    ""
                      :title      "Air or oxygen"
                      :categories [:air :oxygen :device]
                      :labels     ["Air" "O2 L/min" "Device"]
                      :scores     [0 2 0]
                      }

   :blood-pressure   {:heading    "C"
                      :title      "Blood pressure"
                      :subtitle   "mmHg"
                      :categories [[220 nil] [201 219] [181 200] [161 180] [141 160] [121 140] [111 120] [101 110]
                                   [91 100] [81 90] [71 80] [61 70] [51 60] [nil 50]]
                      :scores     [3 0 0 0 0 0 0 1 2 3 3 3 3 3]
                      :indexed-by index-by-numeric-range
                      }
   :pulse            {:heading    "C"
                      :title      "Pulse"
                      :subtitle   "Beats/min"
                      :categories [[131 nil] [121 130] [111 120] [101 110] [91 100] [81 90] [71 80]
                                   [61 70] [51 60] [41 50] [31 40] [nil 30]]
                      :scores     [3 2 2 1 1 0 0 0 0 1 3 3]
                      :indexed-by index-by-numeric-range}
   :consciousness    {:heading    "D"
                      :title      "Consciousness"
                      :categories [:clin/alert :clin/confused :clin/voice :clin/pain :clin/unresponsive]
                      :labels     ["A" "Confused" "V" "P" "U"]
                      :scores     [0 3 3 3 3]
                      :indexed-by index-by-category}
   :temperature      {:heading    "E"
                      :title      "Temperature"
                      :subtitle   "ºC"
                      :categories [[39.1 nil] [38.1 39.0] [37.1 38.0] [36.1 37.0] [35.1 36.0] [nil 35.0]]
                      :scores     [2 1 0 0 1 3]
                      :indexed-by index-by-numeric-range}
   })



(defn plot-results-bp
  "Specialised results plot just for blood pressure"
  [start-y start-date plot-width scale data value-key]
  (let [end-date (calculate-end-date-for-scale start-date plot-width scale)
        chart (:blood-pressure charts)
        sorted-data (->> data
                         (filter #(and (time-core/after? (:date-time %1) start-date) (time-core/before? (:date-time %1) end-date)))
                         (filter #(not (nil? (get %1 value-key))))
                         (sort-by :date-time #(time-core/before? %1 %2)))]

    [:<>
     (doall (map #(let [x (+ 56 (calculate-x-for-scale start-date (:date-time %) scale))
                        value (get % value-key)             ;; value is itself a map of systolic and diastolic
                        systolic (+ 2.5 (* 5 ((:indexed-by chart) (:systolic value) (:categories chart))))
                        diastolic (+ 2.5 (* 5 ((:indexed-by chart) (:diastolic value) (:categories chart))))]
                    (vector
                      :polyline {:points [(+ 3.5 x) (+ start-y systolic) (+ 3.5 x) (+ start-y diastolic)]
                                 :fill   "none" :stroke "black" :stroke-width 0.4 :marker-start "url(#arrow)" :marker-end "url(#arrow)"}
                      )
                    ) sorted-data))
     (comment
       [:polyline {:points (doall (flatten (map #(
                                                   let [x (+ 56 (calculate-x-for-scale start-date (:date-time %) scale))
                                                        y (+ 2.5 (* 5 ((:indexed-by chart) (:systolic (get % value-key)) (:categories chart))))]
                                                   (vector (+ 3.5 x) (+ start-y y))
                                                   ) sorted-data)))
                   :fill   "none" :stroke "black" :stroke-width 0.2 :stroke-dasharray "1 1"
                   }])]
    ))


(defn test-drawing [start-date data]
  (let [width-in-boxes 28                                   ;; number of boxes to show
        box-width 7                                         ;; the viewbox is based on the paper NEWS chart in millimetres, so our internal scale is same as "millimetres"
        box-height 5
        left-column-panel-width 32                          ;; the dark blue left column
        left-column-label-width 24                          ;; the labels     ;; 32+24 = 56 which is a multiple of 7
        left-column-width (+ left-column-panel-width left-column-label-width)
        width (* width-in-boxes box-width)
        dt-start-y 0
        rr-start-y 20
        end-date (time-core/plus start-date (time-core/days width-in-boxes))
        scale :days                                         ;; :12-hourly :6-hourly :4-hourly :hourly       ;; could offer to switch between
        calculate-x-for-days (fn [start-date date] (+ left-column-width (* 7 (time-core/in-days (time-core/interval start-date date)))))
        custom-formatter (time-format/formatter "dd MMM yyyy")
        day-week-formatter (time-format/formatter "E")
        day-month-formatter (time-format/formatter "dd")
        month-formatter (time-format/formatter "MM")
        now (time-core/now)
        dates (->> (range 0 width-in-boxes)                 ;; each box represents another day
                   (map #(time-core/plus start-date (time-core/days %)))
                   (map #(hash-map :day-of-week (time-format/unparse day-week-formatter %)
                                   :day-of-month (time-format/unparse day-month-formatter %)
                                   :month (time-format/unparse month-formatter %)
                                   :is-today (cljs-time.predicates/same-date? % now))))
        ]

    [:svg {:width "100%" :viewBox "0 0 255 391" :xmlns "http://www.w3.org/2000/svg"}

     [:defs
      [:pattern#grid-score-3 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width 7 :height 5 :fill colour-score-3 :stroke "black" :stroke-width 0.1}]]
      [:pattern#grid-score-2 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width 7 :height 5 :fill colour-score-2 :stroke "black" :stroke-width 0.1}]]
      [:pattern#grid-score-1 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width 7 :height 5 :fill colour-score-1 :stroke "black" :stroke-width 0.1}]]
      [:pattern#grid-score-0 {:width "7" :height "5" :patternUnits "userSpaceOnUse"}
       [:rect {:width 7 :height 5 :fill "white" :stroke "black" :stroke-width 0.1}]]
      [:marker#arrow {:viewBox "0 0 10 10" :refX "5" :refY "5" :markerWidth "6" :markerHeight "6" :orient "auto-start-reverse"}
       [:path {:d "M 0 0 L 10 5 L 0 10 z"}]]
      ]

     ;; date / time
     [:rect {:x 0 :y (+ dt-start-y 0) :width left-column-panel-width :height 15 :stroke "black" :fill "none" :stroke-width 0.1}]
     [:text {:x 5 :y (+ dt-start-y 6) :fill "black" :font-size 4} (time-format/unparse custom-formatter start-date)]
     [:rect {:x left-column-panel-width :y (+ dt-start-y 0) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ dt-start-y 5) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:rect {:x left-column-panel-width :y (+ dt-start-y 10) :width left-column-label-width :height 5 :fill "none" :stroke "black" :stroke-width "0.1"}]
     [:text {:x (+ left-column-panel-width (/ left-column-label-width 2)) :y (+ dt-start-y 4) :fill "black" :font-size "4" :text-anchor "middle"} "Day"]
     [:text {:x (+ left-column-panel-width (/ left-column-label-width 2)) :y (+ dt-start-y 9) :fill "black" :font-size "4" :text-anchor "middle"} "Date"]
     [:text {:x (+ left-column-panel-width (/ left-column-label-width 2)) :y (+ dt-start-y 14) :fill "black" :font-size "4" :text-anchor "middle"} "Month"]
     [:rect {:x left-column-width :y (+ dt-start-y 0) :width width :height "15" :fill "url(#grid-score-0"}]
     ;; write out days of week
     (doall (map-indexed (fn [index item]
                           (if (:is-today item)
                             [:rect {:x      (+ left-column-width (* index 7))
                                     :y      (+ dt-start-y 0)
                                     :width  7
                                     :height 15             ;;
                                     :stroke "black" :stroke-width 0.5
                                     :fill   "#ffeeee"
                                     ;;:fill "none"
                                     }])
                           ) dates))

     (doall (map-indexed (fn [index item]
                           [:text {:x           (+ left-column-width 3.5 (* index 7))
                                   :y           (+ dt-start-y 4) :font-size 3
                                   :key         (str "dw-" item) :text-anchor "middle"
                                   :font-weight (if (:is-today item) "bold" "normal")
                                   } (take 2 (:day-of-week item))]) dates))

     ;; write out days of month
     (doall (map-indexed (fn [index item]
                           [:text {:x    (+ left-column-width 3.5 (* index 7))
                                   :y    (+ dt-start-y 9)
                                   :fill "black" :font-size "3" :key (str "dm-" item) :text-anchor "middle"} (:day-of-month item)]) dates))
     ;; write out month
     (doall (map-indexed (fn [index item]
                           [:text {:x    (+ left-column-width 3.5 (* index 7))
                                   :y    (+ dt-start-y 14)
                                   :fill "black" :font-size "3" :key (str "m-" item) :text-anchor "middle"} (:month item)]) dates))


     (draw-chart-axes 20 width (:respiratory-rate charts))
     (plot-results 20 start-date width-in-boxes :day (:respiratory-rate charts) data :respiratory-rate)

     (draw-chart-axes 60 width (:sp02-scale-1 charts))
     (plot-results 60 start-date width-in-boxes :day (:sp02-scale-1 charts) data :spO2)
     (draw-chart-axes 85 width (:air-or-oxygen charts))
     (draw-chart-axes 105 width (:blood-pressure charts))
     (plot-results-bp 105 start-date width-in-boxes :day data :blood-pressure)
     (draw-chart-axes 180 width (:pulse charts))
     (plot-results 180 start-date width-in-boxes :day (:pulse charts) data :pulse-rate)
     (draw-chart-axes 245 width (:consciousness charts))
     (plot-results 245 start-date width-in-boxes :day (:consciousness charts) data :consciousness)
     (draw-chart-axes 275 width (:temperature charts))
     (plot-results 275 start-date width-in-boxes :day (:temperature charts) data :temperature)

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

