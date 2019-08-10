# com.ben-allred/ws-client-cljc

A Clojure library designed to wrap a web socket connection in a core.async channel. Works with clj and cljs.

## Usage

```clojure
(require '[clojure.core.async :as async])
(require '[ws-client-cljc.core :as ws])

(def ch (ws/connect "ws://example.com/ws"))
(async/>!! ch "Hello")
(println (async/<!! ch))
(async/close! ch)
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
