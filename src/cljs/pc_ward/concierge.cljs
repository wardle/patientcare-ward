(ns pc-ward.concierge
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]))

;;;;;;;;;;;;; these specifications represent the types from concierge (https://github.com/wardle/concierge-api)

(s/def ::non-blank-string (s/and string? (complement str/blank?)))

;; identifiers
(s/def ::system ::non-blank-string)
(s/def ::value ::non-blank-string)
(s/def ::identifier (s/keys :req-un [::system ::value]))
(s/def ::identifiers (s/coll-of ::identifier))

;; practitioner
(s/def ::names (s/coll-of (s/keys :req-un [::family ::given] :opt-un [::prefixes ::suffices])) )
(s/def ::practitioner (s/keys :req-un [::identifiers ::active ::names]))

;; patient https://github.com/wardle/concierge-api/blob/d37fc8d6405d1b367092bdd3f7d546a43833c5c1/v1/model.proto#L11

