(defproject yieldbot/torna "0.1.7-SNAPSHOT"
  :description "Kafka batch processor library"
  :url "https://github.com/yieldbot/torna"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-kafka "0.2.8-0.8.1.1"]
                 [zookeeper-clj "0.9.3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cheshire "5.4.0"]
                 [clj-http "1.1.0"]
                 [clj-time "0.6.0"]
                 [compojure "1.3.3"]
                 [capacitor "0.6.0"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-json "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.10"]]
  :plugins [[brightnorth/uberjar-deploy "1.0.1" ]]
  :profiles {:uberjar {:aot :all}}
  :min-lein-version "2.5.0")
