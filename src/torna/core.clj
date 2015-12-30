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
            [clj-time.coerce :as tcoerce]
            [clj-kafka.core :as ckafka]
            [clj-kafka.consumer.zk :as ckafkaconsumerzk])
  (:gen-class))

(def kafka-docs (atom []))
(def num-items (atom 0))
(def batchlognow? (atom true))
(def total-items (atom 0))
(def cons-conn (atom nil))

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

(defn ship-it
  [props batch-handler]
  (when (and (> (count @kafka-docs) 0) @cons-conn)
    (when @batchlognow?
      (log/info "Shipping batch topic.name=" (get props :topic.name) " total-items so far=" @total-items)
      (reset! batchlognow? false))
    (batch-handler props kafka-docs)
    (reset! kafka-docs [])
    (reset! num-items 0)
    (.commitOffsets @cons-conn)))

(defn ready-to-ship?
  "If collected msgs so far are more than batch-size then return true
  Else if batch-interval is configured and if elapsed time more than
  batch-interval then return true else return false"
  [batch-size batch-interval]
  (when (= 0 (mod @num-items batch-size))
    true))

(defn check-batch-interval
  [props batch-interval batch-handler]
  (while true
    (ship-it props batch-handler)
    (Thread/sleep (* 1000 batch-interval))))

(defn collect-kafka-msg
  "collects kafka msgs in an atom"
  [props batch-handler batch-size kafka-msg batch-interval]
  (let [json-msg (json/parse-string (String. (:value kafka-msg) "UTF-8" ))]
    (swap! kafka-docs conj json-msg)
    (swap! num-items inc)
    (swap! total-items inc)
    (when (ready-to-ship? batch-size batch-interval)
      (ship-it props batch-handler))))

(defn verify-params
  [props]
  (when (nil? (get props :group.id))
    (log/error "ERROR: Mandatory param :group.id absent")
    (System/exit 2))
  (when (nil? (get props :kafka.zk.connect))
    (log/error "ERROR: Mandatory param :kafka.zk.connect absent")
    (System/exit 2))
  (when (nil? (get props :topic.name))
    (log/error "ERROR: Mandatory param :topic.name absent")
    (System/exit 2))
  (when (nil? (get props :batch.size))
    (log/error "ERROR: Mandatory param :batch.size absent")
    (System/exit 2)))

(defn read-kafka
  [props batch-handler]
  (let [config {"zookeeper.connect" (get props :kafka.zk.connect)
                "group.id" (get props :group.id)
                "auto.offset.reset" "smallest"
                "auto.commit.enable" "false"}
        topic-name (get props :topic.name)
        batch-size (get props :batch.size)
        health-port (get props :health.port)
        batch-interval (get props :batch.interval)]
    (verify-params props)
    (when health-port
      (run-healthapp health-port))
    (future (check-batchlog-readiness props))
    (when batch-interval
      (future (check-batch-interval props batch-interval batch-handler)))
    (ckafka/with-resource [tmp (reset! cons-conn (ckafkaconsumerzk/consumer config))]
      ckafkaconsumerzk/shutdown
      (doseq [msg (ckafkaconsumerzk/messages @cons-conn topic-name)]
        (collect-kafka-msg props batch-handler batch-size msg batch-interval)))))
