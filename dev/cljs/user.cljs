(ns cljs.user
  "Commonly used symbols for easy access in the ClojureScript REPL during
  development."
  (:require
    [cljs.repl :refer (Error->map apropos dir doc error->str ex-str ex-triage
                                  find-doc print-doc pst source)]
    [clojure.pprint :refer (pprint)]
    [clojure.string :as str]
    [cljs-time.core :as time-core]
    [pc-ward.news-chart :as news]
    [pc-ward.views :as views]
    [clojure.pprint :as pp]))

(comment
  ;; SWITCH TO SHADOW CLJS REPL - copy and paste
  (shadow.cljs.devtools.api/nrepl-select :app)

  ; sample NEWS data - set the "start-date" to be a few days before the start of our sample data, for convenience

  (def start-date (cljs-time.core/minus (first (sort #(time-core/before? %1 %2) (map :date-time views/news-data))) (cljs-time.core/days 3)))
  (def data (news/score-all-news views/news-data false))

  (pprint views/news-data)

  ; perform NEWS scoring on all data

  ; get consecutive days from start-date
  (take 7 (news/dates-from start-date))

  ; get dates from start-date from our data
  (take 7 (news/dates-from-data start-date data))

  ;; generate the headings for columns - consecutive data
  (map #(news/datetime->map % nil news/default-chart-configuration) (take 7 (news/dates-from-data start-date data)))

  ;; generate headings for columns - consecutive days
  (map #(news/datetime->map % nil news/default-chart-configuration) (take 7 (news/dates-from start-date)))

  (news/scale-one-day start-date 7 data)
  (news/scale-one-day-fractional start-date 28 data)

  (news/scale-dates :days start-date 4 data)
  (news/scale-data :days start-date 4 data)
  (news/scale-dates :consecutive start-date 4 data)
  (news/scale-data :consecutive start-date 4 data)
  (news/scale-dates :6-hourly start-date 6 data)
  (news/scale-data :6-hourly start-date 24 data)


  (def fake-scores [
                    {:x 20 :score 4}
                    {:x 37 :score 5}
                    {:x 20 :score 6}
                    {:x 42 :score 8}])
  (->> fake-scores (sort-by :score) (group-by :x) (vals) (map (comp (juxt :x :score) last)) (into {}))

  )
