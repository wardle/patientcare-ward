(ns pc-ward.config)

(def debug?
  ^boolean goog.DEBUG)

(def concierge-server-address "http://saturn.local:8080")
(def terminology-server-address "http://saturn.local:8081")
(def concierge-service-user-namespace "https://concierge.eldrix.com/Id/service-user")
(def concierge-service-user "patientcare")
;; conceivably our UI could allow selection from a list of "providers".
(def default-user-namespace "https://fhir.nhs.uk/Id/cymru-user-id")

;; todo: switch to using a secret configured at build time?
(def patientcare-service-secret "Tly65k8XRdCCBnJy7KJRrWDgc2V9lO5j0lGLjQzrwFwkryuCJZaAZT5xtxiIU7CR")


(def sct-fully-specified-name "900000000000003001")