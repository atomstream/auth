(ns auth-example.util
  (:require
   [reitit.core :as reitit])
  (:import
   (java.math BigInteger)
   (java.security SecureRandom)))

(set! *warn-on-reflection* true)
;; (set! *unchecked-math* :warn-on-boxed)

;; BigInt

(def ^:private base62-chars "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
(def ^:private base62-base (BigInteger/valueOf 62))

(defn bigint->base62 [^BigInteger num]
  (loop [num num
         sb  (StringBuilder.)]
    (if (pos? (.compareTo num BigInteger/ZERO))
      (let [[div rem] (.divideAndRemainder num base62-base)]
        (.append sb (.charAt ^String base62-chars (.intValue ^BigInteger rem)))
        (recur div sb))
      (.toString (.reverse sb)))))

;; Random

(defn random-bigint [bits]
  {:pre [(pos-int? bits)]}
  (let [bytes (byte-array (Math/ceil (/ bits 8)))]
    (.nextBytes (SecureRandom.) bytes)
    (BigInteger. 1 bytes)))

(defn secure-base62-str
  "Generates a cryptographically secure random alphanumeric (base62) string.
  `bits` is an integer representing the number of bits of entropy, this
  should be a multiple of 8."
  [bits]
  (bigint->base62 (random-bigint bits)))

;; Routing

(defn base-url [{:as req :keys [ctx]}]
  (or (:http-origin ctx)
      (let [{:keys [headers authority scheme host]} req
            scheme (or (get headers "x-forwaded-proto") ;; behind proxy
                       (and scheme (name scheme))
                       "http")
            host (or (get headers "x-forwaded-host") ;; behind proxy
                     (get headers "host")
                     authority)]
        (str scheme "://" host))))

(defn path-for
  ([req name]
   (reitit/match->path
    (reitit/match-by-name!
     (:reitit.core/router req)
     name)))
  ([req name params]
   (reitit/match->path
    (reitit/match-by-name!
     (:reitit.core/router req)
     name params))))

(defn url-for
  ([req name]
   (str (base-url req) (path-for req name)))
  ([req name params]
   (str (base-url req) (path-for req name params))))

;; View Helpers

(defn authenticated?
  "make sure the user is logged in"
  [req]
  (some? (get-in req [:session :identity])))

