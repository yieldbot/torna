(ns torna.influxdb
    (:require [capacitor.core                       :as icore]
              [clojure.tools.logging                :as log]))

(def influx-con (atom nil))

(defn create-influx-connection
  "Convenience fn for creating a influxdb client"
  [influx-config]
  (try
      (let [influx-default {:scheme "http" :port 8086 :username "" :password ""}
            configs (merge influx-default influx-config)
            client (icore/make-client {
                        :host (:host configs)
                        :scheme (:scheme configs)
                        :port (:port configs)
                        :username (:username configs)
                        :password (:password configs)
                        :db (:db configs)})]
          (reset! influx-con {:client client})
          (log/info "Influxdb client created for Influx version:" (icore/version (:client @influx-con))))
      (catch Exception e
          (log/info (format "create-influx-connection caught exception: " (.getMessage e)))
          (throw (Exception. "Error in creating influx connection")))))

(defn insert
    [influx-config data x]
    (try
        (do
            (when (nil? @influx-con)
                (Thread/sleep 30000)
                (create-influx-connection influx-config))
            (icore/write-point (:client @influx-con)
                data)
            (log/info "Data written to influx: " data)
            (println "Data written")
            1)
        (catch Exception e
            (reset! influx-con nil)
            (log/info (format "insert caught exception: %s for %d time" (.getMessage e) x))
            (if (= x 10)
                (throw (Exception. "Max Retries over for inserting the data into influxdb.")))
            nil)))
 
(defn write-to-influxdb
    "write time interval to influxdb"
    [influx-config metric-name metric-values]
    (try
        (let [metric {:measurement metric-name}
              values metric-values
              data (merge metric values)]
           (loop [x 1]
               (when (and (nil? (insert influx-config data x)) (< x 10))
                   (recur (+ x 1)))))
        (catch Exception e
            (log/info (format "write-to-influxdb caught exception: Not able to write data to influxdb. Error: %s" (.getMessage e)))
            (throw (Exception. (.getMessage e))))))

(defn init
    [props])
