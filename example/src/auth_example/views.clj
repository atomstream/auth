(ns auth-example.views
  (:require
   [clojure.string :as str]
   [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
   [auth-example.util :as util]))

(defn layout
  "For wrap-render"
  [{:html/keys [head body]}]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    head]
   [:body
    body]])

(defn form
  "Drop-in replacement for :form, but adds a anti-forgery-token field"
  [props & children]
  (into
   [:form props
    [:input {:type "hidden"
             :id "__anti-forgery-token"
             :name "__anti-forgery-token"
             :value
             (-> *anti-forgery-token*
                 (str/replace "&" "&amp;")
                 (str/replace "\"" "&quot;")
                 (str/replace "<" "&lt;"))}]
    children]))

(defn dash
  "Dashboard component"
  [req]
  [:<>
   [:h1 "Welcome"]
   (if (util/authenticated? req)
     [:a {:href (util/path-for req :app/logout)} "Log out"]
     [form {:method "POST" :action "/login"}
      [:input {:type "submit" :value "Login"}]])])

