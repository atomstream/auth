(ns user)

(defn go []
  (try
    ((requiring-resolve 'co.gaiwan.oak.app.config/start!))
    (catch Exception e
      (println e)))
  ((requiring-resolve 'co.gaiwan.oak.app.config/print-table)))

(defn refresh []
  ((requiring-resolve 'clojure.tools.namespace.repl/set-refresh-dirs)
   (clojure.java.io/file "src")
   (clojure.java.io/file "test"))
  ((requiring-resolve 'co.gaiwan.oak.app.config/refresh)))

(defn restart! [& ks]
  ((requiring-resolve 'co.gaiwan.oak.app.config/restart!) ks))

(defn component [id]
  ((requiring-resolve 'co.gaiwan.oak.app.config/component) id))

(defn db []
  (:data-source (component :system/database)))

(comment
  (go)
  )
