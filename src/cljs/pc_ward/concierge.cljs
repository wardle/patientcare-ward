(ns pc-ward.concierge
  (:require
    [cljs.spec.alpha :as s]))



;; these specifications represent the types from concierge (https://github.com/wardle/concierge-api)

(s/def ::system string?)
(s/def ::value string?)
(s/def ::identifier (s/keys :req-un [::system ::value]))
(s/def ::identifiers (s/coll-of ::identifier))

(s/def ::gender #{:male :female :unknown})
(s/def ::human-names (s/keys :req-un [::family ::given ::prefixes ::suffices]))
(s/def ::practitioner (s/keys :req-un [::identifiers ::active ::human-names ::gender]))
