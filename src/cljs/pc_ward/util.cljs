(ns pc-ward.util
  (:require
    [goog.crypt.base64 :as b64]
    [clojure.string :as string])
  (:import [goog.date UtcDateTime]))

(def token "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODY2MzI0MTMsImlhdCI6MTU4NjM3MzIxMywic3ViIjoiaHR0cHM6Ly9jb25jaWVyZ2UuZWxkcml4LmNvbS9JZC9zZXJ2aWNlLXVzZXJ8cGF0aWVudGNhcmUifQ.KRTuQgLcoj8A2x8CSx4JzrKLIBKSjPY9kM3kH8CXtSfBICrN4SBBl1qfhzhMhnWZaz7R7EPpveAZAGnZhxO3QfUKVu55xli3TrAlIeEtJ0Z9f1UMrMjtyhrXEY36rlTW4MpU734UcKCWfRU3uffHBV2bFgIH6Mi4tbu-zo_uqZ1Rc-e7HCP4XuFNRcFIW8e0465bVmiwh1M_dVmRywmJjkwgFxSTE0L4uIkeR-ULYAqaQbd7mAygsrXsmhANvR-P0ROLV7bOa6e_DMekVm4Cd2r1IbriKrjS5YMjBXmVnyGaJ_kGJwv3G0L5p3rcOacXtlujOz9aBaLO1W9fj6cj7w")


(defn jwt-token-payload
  "Extracts the payload from a JWT token"
  [token]
  (js->clj (.parse js/JSON (b64/decodeString (second (string/split token #"\.")))) :keywordize-keys true))


(defn jwt-expiry-date
  "Determines the expiry date of the token"
  [token]
  (some-> (:exp (jwt-token-payload token))
          (* 1000)                                          ;; jwt token expiry is in seconds from epoch. convert to milliseconds
          UtcDateTime/fromTimestamp)
  )

(defn jwt-expires-seconds
  "Gives the number of seconds that this token will remain valid"
  [token]
  (if (string/blank? token)
    0
    (- (:exp (jwt-token-payload token)) (int (/ (.getTime (js/Date.)) 1000)))
    ))

(defn jwt-expires-in-seconds?
  "Does the token expire within the next (x) seconds? Returns true if no token"
  [token, sec]
  (if (string/blank? token)
    true
    (let [now (int (/ (.getTime (js/Date.)) 1000))
          dln (+ now sec)
          exp (:exp (jwt-token-payload token))]
      (> dln exp)
      ))
  )



(defn jwt-valid?
  "Is the token non-nil and not expired? This does not test the token cryptographically"
  [token]
  (if (string/blank? token)
    false
    (let [now (int (/ (.getTime (js/Date.)) 1000))
          exp (:exp (jwt-token-payload token))]
      (> now exp)
      ))
  )

