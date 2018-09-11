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
            [clj-kafka.core :as ckafka]
            [clj-kafka.consumer.zk :as ckafkaconsumerzk])
  (:gen-class))

(def kafka-docs (atom []))
(def batchlognow? (atom true))
(def total-items (atom 0))
(def cons-conn (atom nil))
;; The timer thread calls ship-it method when it is time to send the batch
;; It may happen that the core-thread is already in process of shipping,
;; which read the kafka-docs and modifies them, if ship-it is called from within
;; the check-batch-interval thread then it may cause problems, hence this locking
;; We only do this locking if batch-interval is specified
(def shipping-lock (Object.))

(defroutes app-routes
  (GET "/health" [] (response [{:torna-data "Torna is all fine"}]))
  (route/not-found "Page not found"))

(def app
  (-> app-routes
      wrap-json-response))

(defn run-healthapp
  [port]
  (future
    (try
      (jetty/run-jetty (handler/site app) {:port port})
      (catch Exception e
        (log/info (str "run-healthapp caught exception e= " e))))))

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
    (.commitOffsets @cons-conn)))

(defn ready-to-ship?
  "If accumulated msgs so far are more than batch-size then return true
  Else if batch-interval is configured and if elapsed time more than
  batch-interval then return true else return false"
  [batch-size batch-interval]
  (when (= 0 (mod (count @kafka-docs) batch-size))
    true))

(defn check-batch-interval
  [props batch-interval batch-handler]
  (while true
    (locking shipping-lock
      (ship-it props batch-handler))
    (Thread/sleep (* 1000 batch-interval))))

(defn accumulate-kafka-msg
  "accumulates kafka msgs in an atom"
  [props batch-handler batch-size kafka-msg batch-interval]
  (let [json-msg (json/parse-string (String. (:value kafka-msg) "UTF-8"))]
    (swap! kafka-docs conj json-msg)
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
                "auto.offset.reset" (get props :auto.offset.reset "smallest")
                "auto.commit.enable" "false"
                "fetch.message.max.bytes" (str (get props :fetch.message.max.bytes 1048576))}
        topic-name (get props :topic.name)
        batch-size (get props :batch.size)
        health-port (get props :health.port)
        batch-interval (get props :batch.interval)]
    (verify-params props)
    (when health-port
      (run-healthapp health-port))
    (future
      (try
        (check-batchlog-readiness props)
        (catch Exception e
          (log/info (str "check-batchlog-readiness caught exception e= " e)))))
    (when batch-interval
      (future
        (try
          (check-batch-interval props batch-interval batch-handler)
          (catch Exception e
            (log/info (str "check-batch-interval caught exception e= " e))))))
    (ckafka/with-resource [tmp (reset! cons-conn (ckafkaconsumerzk/consumer config))]
      ckafkaconsumerzk/shutdown
      (while true
        (try
          (doseq [msg (ckafkaconsumerzk/messages @cons-conn topic-name)]
            (try
              (if batch-interval
                (locking shipping-lock
                  (accumulate-kafka-msg props batch-handler batch-size msg batch-interval))
                (accumulate-kafka-msg props batch-handler batch-size msg batch-interval))
              (catch Exception e
                (do
                  (log/error "Caught exception for message " msg)
                  (log/error (String. (:value msg) "UTF-8"))
                  (log/error "Exception:" e))))))))))
