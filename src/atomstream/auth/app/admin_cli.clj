(ns atomstream.auth.app.admin-cli
  "Implementation of the oakadm CLI

  Command line interface for various administrative tasks.
  "
  (:gen-class)
  (:require [co.gaiwan.oak.app.admin-cli :as oak-cli]
            [co.gaiwan.oak.lib.cli-error-mw :as oak-cli-error-mw]
            [lambdaisland.cli :as cli]))

(defn -main [& args]
  (cli/dispatch*
   {:name "oakadm"
    :init oak-cli/init
    :flags oak-cli/flags
    :commands oak-cli/commands
    :middleware [oak-cli/wrap-stop-system
                 oak-cli/wrap-print-output
                 oak-cli-error-mw/wrap-error]}
   args))
