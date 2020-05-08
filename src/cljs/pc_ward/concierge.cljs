(ns pc-ward.concierge
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [cljs-time.core :as time-core]
    [cljs-time.format :as time-format]))

;;;;;;;;;;;;; these specifications represent the types from concierge (https://github.com/wardle/concierge-api)

(s/def ::non-blank-string (s/and string? (complement str/blank?)))

;; identifiers
(s/def ::system ::non-blank-string)
(s/def ::value ::non-blank-string)
(s/def ::identifier (s/keys :req-un [::system ::value]))
(s/def ::identifiers (s/coll-of ::identifier))

;; practitioner
(s/def ::names (s/coll-of (s/keys :req-un [::family ::given] :opt-un [::prefixes ::suffices])))
(s/def ::practitioner (s/keys :req-un [::identifiers ::active ::names]))

;; patient https://github.com/wardle/concierge-api/blob/d37fc8d6405d1b367092bdd3f7d546a43833c5c1/v1/model.proto#L11


(comment
  (def patient {:identifiers [
                              {:system "wibble" :value "wobble"}
                              {:system "https://fhir.nhs.uk/Id/nhs-number" :value "111111111"}]}))

(def systems {:nhs-number             "https://fhir.nhs.uk/Id/nhs-number"
              :gmc-number             "https://fhir.hl7.org.uk/Id/gmc-number"
              :ods-code               "https://fhir.nhs.uk/Id/ods-organization-code"
              :ods-site-code          "https://fhir.nhs.uk/Id/ods-site-code"
              :cymru-user-id          "https://fhir.nhs.uk/Id/cymru-user-id"
              :cardiff-pas            "https://fhir.cardiff.wales.nhs.uk/Id/pas-identifier"
              :concierge-service-user "https://concierge.eldrix.com/Id/service-user"})

(defn identifiers-for-system
  "Returns the identifiers matching the system specified"
  [patient system]
  (->> (:identifiers patient)
       (filter #(= (:system %) system))
       (map #(:value %))))

(defn identifier->string
  "Converts an identifier into a string such as https://fhir.nhs.uk/Id/nhs-number|1111111111"
  [id]
  (str (:system id) "|" (:value id)))

(defn string->identifier
  "Parses an identifier from a string such as https://fhir.nhs.uk/Id/nhs-number|1111111111"
  [s]
  (let [ss (str/split s #"\|")] {:system (first ss) :value (second ss)}))

(defn patient-deceased?
  "Determines whether the patient is deceased or not.
  HL7 FHIR defines two ways of recording whether a patient is deceased, either as a boolean or a date"
  [patient]
  (or (:deceased-boolean patient) (not (clojure.string/blank? (:deceased-date patient)))))

(defn parse-date
  "Parses a date from JSON "
  [date-string]
  (if (clojure.string/blank? date-string) nil (time-format/parse (time-format/formatters :date-time-no-ms) date-string)))

(defn format-date
  "Formats a date as 'dd MMM yyyy'"
  [date]
  (if (nil? date) "" (time-format/unparse (time-format/formatter "dd MMM yyyy") date)))

(defn format-date-time
  "Formats a date as 'dd MMM yyyy HH:MM:SS"
  [date-time]
  (if (nil? date-time) "" (time-format/unparse (time-format/formatter "dd MMM yyyy HH:MM:ss") date-time)))

(defn age-in-years
  "Calculates the age of a patient"
  [date-birth]
  (if (nil? date-birth) nil (time-core/in-years (time-core/interval date-birth (time-core/now)))))

(defn age-in-months
  "Calculates the age of a patient"
  [date-birth]
  (if (nil? date-birth) nil (time-core/in-months (time-core/interval date-birth (time-core/now)))))

(defn format-patient-age
  "A printable representation of the age of a patient"
  [patient]
  (let [age (time-core/interval (parse-date (:birth-date patient)) (time-core/now))
        years (time-core/in-years age)
        months (time-core/in-months age)]
    (cond
      (patient-deceased? patient) ""
      (>= years 6) (str years "y")
      (>= years 2) (str years "y " (- months (* 12 years)) "m")
      (>= months 6) (str months "m")
      (>= (time-core/in-weeks age) 2) (str (time-core/in-weeks age) "w")
      :else (str (time-core/in-days age) "d"))))


(defn within?
  "Is the date within the range specified? Unlike cljs-time, this
  handles nil intervals more intuitively rather than throwing an exception"
  [date start end]
  (cond
    (nil? date) false
    (and (nil? start) (nil? end)) true
    (nil? start) (time-core/before? date end)
    (nil? end) (or (time-core/= date start) (time-core/after? date start))
    :else (time-core/within? (time-core/interval start end) date)))

(defn active-addresses
  "Get the active address(es) from the list for the date specified, or today if no date specified"
  ([addresses]
   (active-addresses addresses (time-core/now)))
  ([addresses date]
   (filter #(within? date (parse-date (get-in % [:period :start])) (parse-date (get-in % [:period :end]))) addresses)))