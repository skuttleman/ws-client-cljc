(defproject com.ben-allred/ws-client-cljc "0.3.0"
  :description "A validation and conforming library for nested data structures"
  :url "https://github.com/skuttleman/formation"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/cljc"]
  :test-paths ["test/cljc"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/core.async "0.4.500"]
                 [stylefruits/gniazdo "1.1.1"]]
  :plugins [[cider/cider-nrepl "0.21.1"]])
