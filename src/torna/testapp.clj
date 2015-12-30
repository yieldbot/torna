(ns torna.testapp
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [torna.core :as torna])
  (:gen-class))

(defn handle-kafka-batch
  " This function can be called from torna or s3toelastic"
  [props json-docs]
  (log/info "testapp: handle-kafka-batch called: num-docs=" (count @json-docs)))

(def cprops
  {:group.id "localhost.perfmetrics"
   :kafka.zk.connect "localhost:2181/kafka"
   :topic.name "testtopic"
   :health.port 31558
   :batch.size 1000
   :batch.interval 30
   :es.url "http://myelastic.domain.com:9200"
   :envelop true})


(defn -main
  "main func "
  [& args]
  (try
    (torna/read-kafka cprops handle-kafka-batch)
    (catch Exception e
      (do
        (.printStackTrace e)
        (System/exit 2)))))
