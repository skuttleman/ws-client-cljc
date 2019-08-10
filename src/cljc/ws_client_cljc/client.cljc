(ns ws-client-cljc.client
  #?(:clj
     (:require
       [gniazdo.core :as gn])))

(defprotocol IWebSocket
  (connecting? [this] "Indicates if the web socket in the process of trying to connect")
  (open? [this] "Indicates if the web socket connection is established and ready to send or receive messages")
  (send! [this ^String msg] "Sends the message across the web socket connection")
  (close! [this] "Closes the web socket connection"))

(def ^:private noop (constantly nil))

(defn ^:private ->ws [state-atom]
  (reify IWebSocket
    (connecting? [_]
      (let [{:keys [ws open?]} @state-atom]
        (boolean (and ws (nil? open?)))))
    (open? [_]
      (boolean (:open? @state-atom)))
    (send! [this msg]
      (when (open? this)
        #?(:clj  (gn/send-msg (:ws @state-atom) msg)
           :cljs (.send (:ws @state-atom) msg))))
    (close! [_]
      (when-let [ws (:ws @state-atom)]
        #?(:clj  (gn/close ws)
           :cljs (.close ws))))))

(defn ^:private ->on-open [interface on-connect state-atom]
  (fn [_]
    (swap! state-atom assoc :open? true)
    (on-connect interface)))

(defn ^:private ->on-close [interface on-close state-atom]
  (let [on-close (or on-close noop)]
    (fn #?(:cljs [event] :clj [code reason])
      (swap! state-atom assoc :open? false)
      (on-close interface {:code   #?(:cljs (.-code event) :clj code)
                           :reason #?(:cljs (.-reason event) :clj reason)}))))

(defn ^:private ->on-receive [interface on-receive]
  (if on-receive
    (fn [msg]
      (on-receive interface #?(:clj msg :cljs (.-data msg))))
    noop))

(defn ^:private ->on-error [interface on-error]
  (if on-error
    (partial on-error interface)
    noop))

(defn connect!
  "implements the IWebSocket interface with supplied options:
  uri          - a URI for establishing the web socket connection
  on-connect   - an optional callback that will be invoked with the IWebSocket when a connection is established
  on-close     - an optional callback that will be invoked with the IWebSocket and {:code {CODE} :reason {REASON}}
                 when a connection is established
  on-receive   - an optional callback that will be invoked with the IWebSocket and the payload when a message is
                 received
  on-error     - an optional callback that will be invoked with the IWebSocket and the error when an error occurs
  subprotocols - a seq of protocols (strings) to be sent with the handshake request

  Example:
  (ws-client/connect! \"ws://example.com/ws\"
                      {:on-connect   (fn [ws] (ws-client/send! ws \"a message\"))
                       :on-close     (fn [ws event] (println event))
                       :on-receive   (fn [ws msg] (if (= msg \"PING\") (ws-client/send! ws \"PONG\") (println msg)))
                       :on-error     (fn [ws error] (println error))
                       :subprotocols [\"protocol-1\" \"protocol-2\"]})"
  ([uri]
   (connect! uri nil))
  ([uri {:keys [on-connect on-close on-receive on-error subprotocols]}]
   (let [state-atom (atom nil)
         interface (->ws state-atom)
         on-connect (->on-open interface on-connect state-atom)
         on-close (->on-close interface on-close state-atom)
         on-receive (->on-receive interface on-receive)
         on-error (->on-error interface on-error)]
     (swap! state-atom assoc :ws #?(:clj  (gn/connect uri
                                                      :on-connect on-connect
                                                      :on-close on-close
                                                      :on-receive on-receive
                                                      :on-error on-error
                                                      :subprotocols (seq subprotocols))
                                    :cljs (doto (js/WebSocket. uri (to-array subprotocols))
                                            (aset "onopen" on-connect)
                                            (aset "onmessage" on-receive)
                                            (aset "onclose" on-close)
                                            (aset "onerror" on-error))))
     interface)))
