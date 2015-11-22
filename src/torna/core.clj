(ns torna.core
  (:import [kafka.javaapi.consumer ConsumerConnector])
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET]]
            [ring.util.response :refer [resource-response response]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :as json]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcoerceerce]
            [clj-kafka.core :as ckafka]
            [clj-kafka.consumer.zk :as ckafkaconsumerzk])
  (:gen-class))

(def kafka-docs (atom []))
(def num-items (atom 0))
(def batchlognow? (atom true))
(def total-items (atom 0))
(def batchtime? (atom false))

(defroutes app-routes
  (GET "/health" [] (response [{:torna-data "Torna is all fine"}]))
  (route/not-found "Page not found"))

(def app
  (-> app-routes
      wrap-json-response))

(defn run-healthapp
  [port]
  (future (jetty/run-jetty (handler/site app) {:port port})))

(defn check-batchlog-readiness
  [props]
  (while true
    (let [min (tc/minute (tc/to-time-zone (tc/now) tc/utc))]
      (if (= 0 (mod min 10))
        (do (reset! batchlognow? true)
            (Thread/sleep 60000))
        (Thread/sleep 30000)))))

(defn check-batch-time
  [batch-time]
  (while true
    (reset! batchtime? true)
    (Thread/sleep (* 1000 batch-time))))

(defn collect-kafka-msg
  "collects kafka msgs in an atom"
  [props batch-handler ^ConsumerConnector c batch-size kafka-msg]
  (let [json-msg (json/parse-string (String. (:value kafka-msg) "UTF-8" ))
        offset (:offset kafka-msg)
        partition (:partition kafka-msg)]
    (swap! kafka-docs conj json-msg)
    (swap! num-items inc)
    (swap! total-items inc)
    (when (or (= 0 (mod @num-items batch-size)) @batchtime?)
      (if @batchlognow?
        (do (log/info "processing batch , offset=" offset " topic.name=" (get props :topic.name) " batch-size=" batch-size " total-items so far=" @total-items)
            (reset! batchlognow? false)))
      (batch-handler props kafka-docs)
      (reset! kafka-docs [])
      (reset! num-items 0)
      (reset! batchtime? false)
      (.commitOffsets c))))

;; TODO add props checking and exit if requried params are not passed
(defn read-kafka
  [props batch-handler]
  (let [config {"zookeeper.connect" (get props :kafka.zk.connect)
                "group.id" (get props :group.id)
                "auto.offset.reset" "smallest"
                "auto.commit.enable" "false"
                }
        topic-name (get props :topic.name)
        batch-size (get props :batch.size)
        health-port (get props :health.port)
        batch-time (get props :batch.time)]
    (when health-port (run-healthapp health-port))
    (future (check-batchlog-readiness props))
    (if batch-time
      (future (check-batch-time batch-time)))
    (ckafka/with-resource [cons-conn (ckafkaconsumerzk/consumer config)]
      ckafkaconsumerzk/shutdown
      (doseq [msg (ckafkaconsumerzk/messages cons-conn topic-name)]
        (collect-kafka-msg props batch-handler cons-conn batch-size msg)))))
