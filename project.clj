(defproject yieldbot/torna "0.1.14-SNAPSHOT"
  :description "Kafka batch processor library"
  :url "https://github.com/yieldbot/torna"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-kafka "0.2.8-0.8.1.1"]
                 [zookeeper-clj "0.9.4"]
                 [org.clojure/tools.logging "0.4.1"]
                 [cheshire "5.8.0"]
                 [clj-time "0.14.4"]
                 [compojure "1.6.1"
                  :exclusions [ring/ring-codec ring/ring-core]]
                 [capacitor "0.6.0"
                  :exclusions [clj-http]]
                 [clj-http "3.9.1"]
                 [ring/ring-jetty-adapter "1.7.0"]
                 [ring/ring-json "0.4.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]]
  :plugins [[brightnorth/uberjar-deploy "1.0.1"]]
  :profiles {:uberjar {:aot :all}}
  :min-lein-version "2.5.0")
