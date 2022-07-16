(ns com.ben-allred.ws-client-cljc.core
  (:require
    [clojure.core.async :as async]
    [clojure.core.async.impl.protocols :as async.protocols]
    [com.ben-allred.ws-client-cljc.client :as client]))

(def ^:private CONNECTING_TIMEOUT 50)
(def ^:private RECONNECT_TIMEOUT 100)

(defn ^:private on-connect* [ch ws]
  (async/go-loop []
    (cond
      (client/connecting? ws) (do (async/<! (async/timeout CONNECTING_TIMEOUT))
                                  (recur))
      (client/open? ws) (when-let [msg (async/<! ch)]
                          (client/send! ws msg)
                          (recur)))))

(defn ^:private keep-alive* [uri opts reconnect? timeout init-timeout]
  (async/go
    (async/<! (async/timeout init-timeout))
    (loop []
      (if-let [ws (try (client/connect! uri opts)
                       (catch #?(:clj Throwable :cljs :default) _
                         nil))]
        ws
        (do (async/<! (async/timeout timeout))
            (when @reconnect?
              (recur)))))))

(defn connect!
  "Wraps the ws-client-cljs.client/IWebSocket with a core.async channel
  uri          - a URI such as ws://example.com/ws
  in-buf-or-n  - the buf-or-n arg used for creating the channel of messages from the server
  out-buf-or-n - the buf-or-n arg used for creating the channel of messages to be send to the server
  in-xform     - the transducer applied to all messages coming from the server
  out-xform    - the transducer applied to all messages being sent to the server
  subprotocols - a seq of protocols (strings) to be sent with the handshake request

  Example:
  (def ws (ws/connect! \"ws://example.com/ws\"
                       {:in-buf-or-n 10
                        :out-buf-or-n 10
                        :in-xform (map clojure.edn/read-string)
                        :out-xform (map pr-str)
                        :subprotocols [\"protocol-1\" \"protocol-2\"]}))
  (async/>!! ws {:message \"PING\"})
  (println (async/<!! ws))
  (async/close! ws)"
  ([uri]
   (connect! uri nil))
  ([uri {:keys [in-buf-or-n out-buf-or-n in-xform out-xform subprotocols on-connect]
         :or   {on-connect (constantly nil)}}]
   (let [in (async/chan in-buf-or-n in-xform)
         out (async/chan out-buf-or-n out-xform)
         client (volatile! nil)
         ws (try (client/connect! uri
                                  {:on-connect (fn [ws]
                                                 (on-connect* out ws)
                                                 (on-connect @client))
                                   (partial on-connect* out on-connect)
                                               :on-close (fn [_ _] (async/close! in) (async/close! out))
                                               :on-receive (fn [_ msg] (async/put! in msg))
                                               :subprotocols subprotocols})
                 (catch #?(:clj Throwable :default :default) ex
                   (async/close! in)
                   (async/close! out)
                   (throw ex)))]
     (vreset! client (reify
                       async.protocols/ReadPort
                       (take! [_ fn1-handler]
                         (async.protocols/take! in fn1-handler))

                       async.protocols/WritePort
                       (put! [_ val fn1-handler]
                         (async.protocols/put! out val fn1-handler))

                       async.protocols/Channel
                       (closed? [_]
                         (or (and (not (client/connecting? ws))
                                  (not (client/open? ws)))
                             (async.protocols/closed? in)
                             (async.protocols/closed? out)))
                       (close! [_]
                         (client/close! ws))))
     @client)))

(defn keep-alive!
  "Wraps the ws-client-cljs.client/IWebSocket with a core.async channel and continuously tries to keep a connection
  unless the channel is manually closed with `clojure.core.async/close!`.
  uri          - a URI such as ws://example.com/ws
  in-buf-or-n  - the buf-or-n arg used for creating the channel of messages from the server
  out-buf-or-n - the buf-or-n arg used for creating the channel of messages to be send to the server
  in-xform     - the transducer applied to all messages coming from the server
  out-xform    - the transducer applied to all messages being sent to the server
  subprotocols - a seq of protocols (strings) to be sent with the handshake request

  Example:
  (def ws (ws/keep-alive! \"ws://example.com/ws\"
                          {:in-buf-or-n 10
                           :out-buf-or-n 100
                           :in-xform (map clojure.edn/read-string)
                           :out-xform (map pr-str)
                           :subprotocols [\"protocol-1\" \"protocol-2\"]}))
  (async/>!! ws {:message \"PING\"})
  (println (async/<!! ws))
  (async/close! ws) ;; Discontinues \"keep alive\" behavior."
  ([uri]
   (keep-alive! uri nil))
  ([uri {:keys [in-buf-or-n out-buf-or-n in-xform out-xform subprotocols reconnect-ms on-connect]
         :or   {reconnect-ms RECONNECT_TIMEOUT on-connect (constantly nil)}}]
   (let [in (async/chan in-buf-or-n in-xform)
         out (async/chan out-buf-or-n out-xform)
         reconnect? (volatile! true)
         loop (volatile! nil)
         ws (volatile! nil)
         connect-opts (volatile! nil)]
     (letfn [(with-ws [init-timeout opts]
               (async/go
                 (vreset! ws (async/<! (keep-alive* uri opts reconnect? reconnect-ms init-timeout))))
               opts)]
       (let [client (reify
                      async.protocols/ReadPort
                      (take! [_ fn1-handler]
                        (async.protocols/take! in fn1-handler))

                      async.protocols/WritePort
                      (put! [_ val fn1-handler]
                        (async.protocols/put! out val fn1-handler))

                      async.protocols/Channel
                      (closed? [_]
                        (and (some? @ws)
                             (or (and (not (client/connecting? @ws))
                                      (not (client/open? @ws)))
                                 (async.protocols/closed? in)
                                 (async.protocols/closed? out))))
                      (close! [_]
                        (vreset! reconnect? false)
                        (when-let [socket @ws]
                          (client/close! socket))))]
         (->> {:on-connect   (fn [ws]
                               (vreset! loop (on-connect* out ws))
                               (on-connect client))
               :on-close     (fn [_ _]
                               (when-let [ch @loop]
                                 (async/close! ch))
                               (if @reconnect?
                                 (with-ws reconnect-ms @connect-opts)
                                 (run! async/close! [in out])))
               :on-receive   (fn [_ msg] (async/put! in msg))
               :subprotocols subprotocols}
              (with-ws 0)
              (vreset! connect-opts))
         client)))))
