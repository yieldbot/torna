![Torna] (https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Torna_Fort1.jpg/640px-Torna_Fort1.jpg)

A clojure library for reading messages from kafka and passing it to a batch-handler.

The batch-handler can do whatever it likes with these batches of messages.
Few Examples:

 * Couchbase inserter
 
 * Elasticsearch indexer
 
 * Redis inserter
 
 * Custom processor.


## Installation
Torna is available from clojars.
Add one of the following to the dependences in your project.clj file:

### With Leiningen
[![Clojars Project](http://clojars.org/yieldbot/torna/latest-version.svg)]
`[yieldbot/torna "0.1.1-SNAPSHOT"]`


## License

Copyright © 2015 Yieldbot

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
