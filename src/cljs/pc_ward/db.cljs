(ns pc-ward.db
  (:require
    [cljs.spec.alpha :as s]
    [pc-ward.concierge :as concierge]))

;; this is a clojure.spec for the database contents
(s/def ::token string?)
(s/def ::authenticated-user (s/keys :req-un [::token ::username ::namespace] :opt-un [::concierge/practitioner]))
(s/def ::response (s/keys :req-un [::error ::message]))
(s/def ::http-error (s/keys :req-un [::response ::status ::debug-message ::response]))
(s/def ::error (s/or :string string? :error ::http-error))
(s/def ::errors (s/nilable (s/map-of keyword? ::error)))
(s/def ::concierge-service-token ::token)
(s/def ::active-panel keyword?)
(s/def ::db (s/keys :opt-un [::active-panel
                             ::concierge-service-token
                             ::authenticated-user
                             ::patient-search-result
                             ::snomed
                             ::current-time
                             ::errors]))

(def default-db
  {})



