(ns pc-ward.clinical)


(defn calc-news-respiratory [rr]
  (cond (>= rr 25) 3
        (>= rr 21) 2
        (>= rr 12) 0
        (>= rr 9) 1
        (<= rr 8) 3))

(defn calc-news-o2-sats-scale-1 [sats]
  (cond (>= sats 96) 0
        (>= sats 94) 1
        (>= sats 92) 2
        (<= sats 91) 3))

(defn calc-news-o2-sats-scale-2 [sats on-o2?]
  (cond (and (>= sats 97) on-o2?) 3
        (and (>= sats 95) on-o2?) 2
        (and (>= sats 93) on-o2?) 1
        (and (>= sats 93) (not on-o2?)) 0
        (>= sats 88) 0
        (>= sats 86) 1
        (>= sats 84) 2
        (<= sats 83) 3))

(defn calc-news-on-oxygen [on-o2?]
  (if on-o2? 2 0))

(defn calc-news-bp [sbp]
  (cond (>= sbp 220) 3
        (>= sbp 111) 0
        (>= sbp 101) 1
        (>= sbp 91) 2
        (<= sbp 90) 3))

(defn calc-news-pulse [p]
  (cond (>= p 131) 3
        (>= p 111) 2
        (>= p 91) 1
        (>= p 51) 0
        (>= p 41) 1
        (<= p 40) 3))

(defn calc-news-consciousness [c]
  (case c
    ::alert 0
    ::confused 3                                            ;; new confusion
    ::verbal 3                                              ;; verbally responsive
    ::pain 3                                                ;; responsive to pain
    ::unresponsive 3
    0
    ))

(defn calc-news-temperature [t]
  (cond (>= t 39.1) 2
        (>= t 38.1) 1
        (>= t 36.1) 0
        (>= t 35.1) 1
        (<= t 35.0) 3))


(defn calc-news [rr sats on-o2? hypercapnic? sbp p consciousness temp]
  (+ (calc-news-respiratory rr)
     (if hypercapnic? (calc-news-o2-sats-scale-2 sats on-o2?) (calc-news-o2-sats-scale-1 sats))
     (calc-news-on-oxygen on-o2?)
     (calc-news-bp sbp)
     (calc-news-pulse p)
     (calc-news-consciousness consciousness)
     (calc-news-temperature temp)))