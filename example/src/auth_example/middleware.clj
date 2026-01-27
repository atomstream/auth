(ns auth-example.middleware
  (:require
   [reitit.ring.middleware.muuntaja :as reitit-muuntaja]
   [ring.middleware.session :as ring-session]
   [ring.middleware.session.memory :as ring-session-memory]
   [ring.middleware.flash :as ring-flash]
   [reitit.ring.coercion :as ring-coercion]
   [auth-example.util :as util]))

(defn wrap-request-filter
  "Helper middleware to apply a function to requests, before they go to the
  handler. Used to inject additional things into the request map, like `:db`"
  [handler rf]
  (fn [req]
    (handler (rf req))))

;;; Auth

;; Oak has an authentication 'type' (password, otp, etc),
;; which I'm not sure we have access to via the callback.

(defn get-session-auth [session type]
  (let [{:keys [authentications identity]} session]
    (some #(when (= type (:type %)) %)
          (get-in session [:authentications identity]))))

(defn update-session-auth! [session auth]
  (let [{:keys [_ identity]} session
        {:keys [type _]} auth]
    (with-meta
      (-> session
          (update-in [:authentications identity]
                     #(set (remove (fn [a] (= (:type a) type)) %)))
          (update-in [:authentications identity]
                     (fnil conj #{})
                     auth))
      {:recreate true})))

(defn redirect-auth [{:keys [session uri] :as req} route]
  {:status 302
   :headers {"Location" (util/url-for req route)}
   :session (assoc session :redirect-after-login uri)})

;; If there is an identity in the session, retrieve the auth
;; The auth will have the access token (and possibly the associated claims?)
;; Here we should check that the auth is still valid.
;; If it's not valid, but we have a refresh token, then perhaps we can try
;; to extend the authentication.
(defn wrap-private-auth [h]
  (fn [{:keys [session] :as req}]
    (let [identity-id (:identity session)
          identity nil
          _auth (get-session-auth session "auth-code")
          ;; TODO here is the auth is still valid
          auth-valid? true]
      (if-not auth-valid?
        (redirect-auth req :app/dash)
        (h (assoc req :identity identity))))))

(defn- route-data [{:keys [request-method] :as req}]
  (let [data (-> req :reitit.core/match :data)]
    (merge (dissoc data :get :put :post :delete)
           (get data request-method))))

(defn wrap-auth-claims
  "Authorization handler

  Requires an :auth-claim key in the route metadata

  - :public  - route is publicly accessible
  - :private - user should be in session
  "
  [handler]
  (fn [req]
    (let [{:keys [auth-claim]} (route-data req)]
      (case auth-claim
        :public
        (handler req)

        :private
        ((wrap-private-auth handler) req)

        (throw (ex-info (str "Illegal `:auth-claim` value: " auth-claim)
                        (:reitit.core/match req)))))))

(def ^:dynamic *csp-nonce* nil)

(defn wrap-content-security-policy
  [h]
  (fn [req]
    (binding [*csp-nonce* (util/secure-base62-str 200)]
      (let [res (h req)]
        (assoc-in res
                  [:headers "content-security-policy"]
                  (str "'nonce-" *csp-nonce* "'"))))))

(defn wrap-404 [h]
  (fn [req]
    (let [res (h req)]
      (if (nil? res)
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body "404 Not Found"}
        res))))

(defn add-ctx [ctx]
  (fn [req]
    (assoc req :ctx ctx)))

;; TODO secure 

(defn app-middleware
  "Middleware common to all endpoints"
  [ctx]
  [reitit-muuntaja/format-negotiate-middleware
   reitit-muuntaja/format-response-middleware
   reitit-muuntaja/format-request-middleware
   ring-coercion/coerce-exceptions-middleware
   ring-coercion/coerce-response-middleware
   ring-coercion/coerce-request-middleware
   [wrap-request-filter (add-ctx ctx)]
   [ring-session/wrap-session
                     {:store (ring-session-memory/memory-store)
                      :cookie-name "atomauth-session"
                      :cookie-attrs {:http-only true
                                     :same-site :strict
                                     :secure false}}]
   wrap-auth-claims
   ring-flash/wrap-flash
   wrap-content-security-policy
   wrap-404])

