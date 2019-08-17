# com.ben-allred/ws-client-cljc

A Clojure library designed to wrap a web socket connection in a core.async channel. Works with clj and cljs.

## Usage

```clojure
(require '[clojure.core.async :as async])
(require '[com.ben-allred.ws-client-cljc.core :as ws])

(def ch (ws/connect! "ws://example.com/ws"))
(async/>!! ch "Hello")
(println (async/<!! ch))
(async/close! ch)
```

Works with transducers.

```clojure
(require '[clojure.core.async :as async])
(require '[com.ben-allred.ws-client-cljc.core :as ws])

(def ch (ws/connect! "ws://example.com/ws" {:in-buf-or-n 10   ;; Per the clojure.core.async spec:
                                            :out-buf-or-n 100 ;; "If a transducer is supplied a buffer must be specified."
                                            :in-xform (comp (map clojure.edn/read-string)
                                                            (remove (comp #{:ping} :type)))
                                            :out-xform (map pr-str)
                                            :subprotocols ["application/edn"]}))
(async/>!! ch {:message "hello"})
(println (async/<!! ch))
(async/close! ch)
```

For a client that will continuosly re-attempt to establish and keep a connection open:

```clojure
(require '[clojure.core.async :as async])
(require '[com.ben-allred.ws-client-cljc.core :as ws])

(def ch (ws/keep-alive! "ws://example.com/ws"))
(async/>!! ch "Hello")
(println (async/<!! ch))
(async/close! ch) ;; Closing the channel manually discontinues keeping the connection alive.
```

## License

Copyright © 2019

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
