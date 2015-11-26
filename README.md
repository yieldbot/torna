![Torna] (https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Torna_Fort1.jpg/640px-Torna_Fort1.jpg)

A simple clojure library for your Kafka to 'X' needs..

## Why ? 
Reading messages from Kafka and doing 'X' is a very common need. This library simplifies that via a common usage pattern. It hides all the complexities about connecting to kafka, reading the messages, handling timeouts etc. It lets you concentrate on the application logic of processing the kafka messages.  You simply need to pass your batch-handler, a function that has all your core processing logic and it handles all the low level details about Kafka.

Few Examples of Batch Handlers:

 * Couchbase inserter
 
 * Elasticsearch indexer
 
 * Redis inserter
 
 * Custom processor.

## Installation
Torna is available from clojars.
Add one of the following to the dependences in your project.clj file:

### With Leiningen
![Clojars Project](http://clojars.org/yieldbot/torna/latest-version.svg)

## Usage 
```clojure
(ns couchbaseinserter
  (:require [torna.core :as torna]))

(def props
  {:group.id "couchbaseinserter.mygroupd-id"
   :kafka.zk.connect "zkhost-0.mydomain.com:2181,zkhost-1.mydomain.com:2181,zkhost-2.mydomain.com:2181/kafka"
   :topic.name "topic-name"
   :health.port 31522
   :batch.size 100
   :batch.time 5
   :couchbase.bucketname "mybucket"
   :couchbase.hosts ["cbhost-0.mydomain.com"
                     "cbhost-1.mydomain.com"
                     "cbhost-2.mydomain.com"]})

(defn format-mydoc
  "Do Formatting stuff"
  [docid mydoc])

(defn insert-mydocs
  "Do Inserting stuff"
  [allmydocs])

(defn handle-kafka-batch
 "Core application logic handler"
  [props json-docs]
  (let [cb-docs (map (fn [item]
                       (let [mydocid (item "mydocid")]
                         (format-mydoc mydocid item)))
                     @json-docs)]
    (insert-mydocs cb-docs)))

(defn -main
  [& args]
  (torna/read-kafka props handle-kafka-batch))
```

The `properties` definitions

|Property Name          |Mandatory|Default|Meaning     |
|-----------------------|----------|--------|----------------------------------------------------------------------------------------|
| `:group.id`  | Yes| N/A| The Kafka consumer group id |
| `:kafka.zk.connect` | Yes| N/A| A comma separated list Kafka host:port/zkpath |
| `:topic.name`  | Yes| N/A| Kafka topic name to read from |
| `:batch.size`  | Yes| N/A| Accumulation size until handler is called |
| `:batch.time`  | No| N/A| Time until handler will be called, when accum messages are less than `batch.size` |
| `:health.port`  | No| N/A| Health Port |
| `:couchbase.bucketname`  | No| N/A| Your custom property |
| `:couchbase.hosts`  | No| N/A| Your custom property |

## Running 
```shell
$ lein uberjar
...
$ java -jar your-ns-STANDALONE-1.0.0.jar -c <any custom flag>
```

## License

Copyright © 2015 Yieldbot

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
