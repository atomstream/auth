(ns auth-example.app
  (:require
   [org.httpkit.server :as http]
   [hato.client :as hato]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.anti-forgery :as ring-csrf]
   [muuntaja.core :as muuntaja]
   [muuntaja.format.charred :as muuntaja-charred]
   [reitit.coercion.malli]
   [reitit.ring :as reitit-ring]
   [lambdaisland.hiccup.middleware :as hiccup-mw]
   [lambdaisland.uri :as uri]
   [co.gaiwan.oak.util.jose :as jose]
   [auth-example.views :as views]
   [auth-example.middleware :as middleware]
   [auth-example.util :as util]
   [clojure.walk :as walk])
  (:import
   (java.time Instant)))

(require 'co.gaiwan.oak.lib.hato-charred)

(defn fetch [ctx req]
  (hato/request (cond-> (merge {:as :auto} req)
                  (not (:request-method req))
                  (assoc :request-method :get)
                  (and (:oak-url ctx) (:path req))
                  (assoc :url (-> (str (:oak-url ctx)
                                       (:path req)))))))

(defn fetch-rfc8414-metadata
  "Retrieve OAuth server configuration using .well-known endpoint

RFC 8414 - OAuth 2.0 Authorization Server Metadata
"
  [ctx]
  (fetch ctx {:path "/.well-known/oauth-authorization-server"}))

(defn fetch-jwks-info [{:as ctx}]
  (fetch ctx {:path "/.well-known/jwks.json"}))

(defn fetch-tokens
  "Fetch tokens from the token endpoint, according to scope"
  [ctx code]
  (let [{:keys [body]}
        (fetch ctx {:path "/oauth/token"
                    :request-method :post
                    :headers {"Accept" "application/json"}
                    :form-params
                    {:grant_type "authorization_code"
                     :code code
                     :client_id (:client-id ctx)
                     :client_secret (:client-secret ctx)
                     :scope (:scope ctx)}})
        {:keys [id_token access_token refresh_token]} body]
    (cond-> {:access-token access_token}
      id_token
      (assoc :id-token id_token)
      refresh_token
      (assoc :refresh-token refresh_token))))

(defn find-jwk
  "Find JWK from Oak which matches 'kid'"
  [ctx kid]
  (let [jwks (get-in ctx [:server :jwks :keys])
        jwk (->> jwks
                 (filter #(= (:kid %) kid))
                 first)]
    (walk/stringify-keys jwk)))

(defn auth-url [{:as _ctx :keys [oak-url client-id port scope]} state]
  (let [redirect-uri (str "http://localhost:" port "/callback")]
    (str (uri/assoc-query* (str oak-url "/oauth/authorize")
                     {:response_type "code"
                      :client_id client-id
                      :state state
                      :redirect_uri redirect-uri
                      :scope scope}))))

(defn parse-verify
  [ctx token]
  (let [header (jose/parse-header token)
        kid (get header "kid")
        jwk (and kid (find-jwk ctx kid))]
    (when jwk
      (jose/parse-verify-jwt jwk token))))

(defn expired? [claims]
  (< (get claims "exp") (/ (System/currentTimeMillis) 1000)))

(defn identity-checks
  "???"
  [req session]
  (when (not (:identity session))
    {:status 302
     :headers {"Location" (util/path-for req :app/dash)}}))

;;; Handlers

(defn GET-homepage [req]
  {:status 200
   :html/body
    [views/dash req]})

;; Is validating an access token just as good as validating an ID token?
(defn GET-callback [{:as req :keys [ctx session]}]
  (let [{:keys [code state]} (:params req)
        session-state (::state session)]
    (if-not (= session-state state)
      {:status 400
       :html/body [:h1 "State does not match"]}
      (let [{:as _tokens :keys [id-token access-token]} (fetch-tokens ctx code)
            ;; store access-token to fetch profile later
            {:as claims :strs [sub]} (parse-verify ctx access-token)
            session (if sub (let [session (assoc session
                                                 :identity sub)]
                              (middleware/update-session-auth!
                               session (cond-> {:type "auth-code"
                                                :access-token access-token
                                                :created-at (Instant/now)}
                                         id-token
                                         (assoc :id-token id-token))))
                        session)]
        (cond
          ;; could not obtain a token, don't touch session
          (not sub)
          {:status 403
           :html/body
           [:<>
            [:h1 "Error"]
            [:p "Could not authenticate"]]}
          (expired? claims)
          {:status 403
           :html/body
           [:<>
            [:h1 "Error"]
            [:p "Auth server responded with an expired token"]]}
          :else
          ;; login succeeded
          {:status 301
           :headers {"Location" (util/path-for req :app/dash)}
           :session session})))))

(defn POST-login [{:as req :keys [ctx session]}]
  (let [state (util/secure-base62-str 400)
        session (assoc session ::state state)]
    {:status 302
     :headers {"Location" (auth-url ctx state)}
     :session session}))

(defn GET-logout [req]
  {:status 302
   :headers {"Location" (util/path-for req :app/dash)}
   :session ^:replace {}})

;;; Application

(def routes
  ["" {}
   ["/ping" {:get (constantly {:status 200 :body "pong"})}]
   ["" {:html/layout views/layout}
    ["/" {:name :app/dash
          :auth-claim :public
          :middleware [wrap-params
                       wrap-keyword-params
                       ring-csrf/wrap-anti-forgery
                       hiccup-mw/wrap-render]
          :get #'GET-homepage}]
    ["/login" {:name :app/login
               :auth-claim :public
               :middleware [wrap-params
                            wrap-keyword-params
                            ring-csrf/wrap-anti-forgery]
               :post #'POST-login}]
    ["/logout" {:name :app/logout
                :auth-claim :public
                :get #'GET-logout}]
    ["/callback" {:name :app/callback
                  :auth-claim :public
                  :middleware [wrap-params
                               wrap-keyword-params]
                  :get #'GET-callback}]]])

(defn muuntaja-instance []
  (muuntaja/create
   (-> muuntaja/default-options
       (assoc-in [:formats "application/json"] muuntaja-charred/format)
       (assoc-in [:formats "application/json; charset=utf-8"] muuntaja-charred/format))))

(defn handler [ctx routes]
  (reitit-ring/ring-handler
   (reitit-ring/router
    routes
    {:data
     {:coercion (reitit.coercion.malli/create)
      :muuntaja (muuntaja-instance)
      :middleware (middleware/app-middleware ctx)}})
   (reitit-ring/create-default-handler)))

(def initial-context
  {:oak-url "http://localhost:4800"
   :authorization-endpoint "/oauth/authorize"
   :redirect-uri "https://example.com/redirect"
   :scope "profile"})

;; TODO state should associate the ID token request with the ID token response
;; and shouldn't be at the application level

(defn run-server
  "Runs a server and returns the stop function"
  [opts]
  (let [ctx (merge initial-context opts)
        info (-> (fetch-rfc8414-metadata ctx) :body)
        ctx (assoc ctx :server info)
        jwks-info (-> (fetch-jwks-info ctx) :body)
        ctx (assoc-in ctx [:server :jwks] jwks-info)
        stop (http/run-server (handler ctx routes) ctx)]
    (assoc ctx :stop stop)))

(comment
  ;; Start server
  (def ctx
    (let [opts {:client-id "LwyQ7POY7Sq.oak.client"
                :client-secret "2AQg ... ZN2dU"
                :port 9000}]
      (run-server opts)))

  ;; Stop server
  ((:stop ctx))
  
  ,)
