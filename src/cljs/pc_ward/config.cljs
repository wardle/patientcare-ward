(ns pc-ward.config)

(def debug?
  ^boolean goog.DEBUG)

(def concierge-service-user-namespace "https://concierge.eldrix.com/Id/service-user")
(def concierge-service-user "patientcare")
;; this is a fake secret used for development - TODO: replace with one configured at build time
(def concierge-service-user-secret "Tly65k8XRdCCBnJy7KJRrWDgc2V9lO5j0lGLjQzrwFwkryuCJZaAZT5xtxiIU7CR")