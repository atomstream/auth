(ns auth-example.cli
  (:require
   ;; Using babashka.cli because I'm familiar with it.
   ;; Should probably change to lambdaisland.cli for parity with Oak
   [babashka.cli :as cli]
   [auth-example.app :as app]))

(def cli-spec
  {:spec
   {:client-id {:desc "Oak client ID"
                :require true}
    :client-secret {:desc "Oak client secret"
                    :require true}
    :port {:desc "Port"
           :coerce :long
           :validate pos?
           :default 8080}}
   :error-fn
   (fn [{:keys [spec type cause msg option] :as data}]
     (when (= :org.babashka/cli type)
       (cause cause
              :require
              (println (format "Missing required argument: %s\n" option))
              :validate
              (println msg))))})

(defn show-help [spec]
  (cli/format-opts (merge spec {:order (vec (keys (:spec spec)))})))

(defn -main [& args]
  (let [opts (cli/parse-opts args cli-spec)]
    (if (or (:help opts) (:h opts))
      (println (show-help cli-spec))
      (do
        (app/run-server opts)
        (println "App started on port" (:port opts))
        @(promise)))))
