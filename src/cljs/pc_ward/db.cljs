(ns pc-ward.db
  (:require
    [cljs.spec.alpha :as s]
    [pc-ward.concierge :as concierge]))




;; this is a clojure.spec for the database contents
(s/def ::token string?)
(s/def ::authenticated-user (s/keys :req-un [::token ::practitioner ::username ::namespace]))
(s/def ::errors (s/keys :req-un [::id ::message ::from] :opt-un [::date-time]))
(s/def ::session (s/keys :req-un [::authenticated-user ::errors]))
(s/def ::db (s/keys :req-un [::session]))



(def default-db
  {})



