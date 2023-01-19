(ns socket-server.core
  (:gen-class)
  (:require [clojure.core.async :as async]))

;; LAYER 1: TCP/IP

(def channel-global (async/chan))

(defn write-message [client data]
  (let [out (.getOutputStream client)]
    (.write out (.getBytes data))))

(defn read-message [client]
  (let [buffer (byte-array 1024)
        bytes-read (.read (.getInputStream client) buffer)
        client-msg (when (not= bytes-read -1) (String. (java.util.Arrays/copyOfRange buffer 0 bytes-read) "UTF-8"))]
    client-msg)) 

(defn socket->channel [client]
  (let [channel (async/chan)]
    ;; reads messages client 
    (async/go-loop []
      (when-let [message (read-message client)]
        (async/>! channel-global message)
        (recur)))
    (async/go-loop []
      (when-let [message (async/<! channel)] 
        (write-message client message)
        (recur)))
    channel))

(defn channel-> [channels channel]
    ;; sends the message to all other clients
  (async/go-loop []
    (when-let [message (async/alts! channel-global)]
      (let [other-channels (filter #(not= % channel) @channels)]
        (async/>! other-channels message)))
    (recur)))

(defn handle-client
  "the function that handles a client connection, it calls the previously
   defined functions to add the client to the list of clients, start 
   listening for incoming messages and broadcasts them to other clients"
  [clients client]
  (println (str "New client : " (.getInetAddress client)))
  (swap! clients conj client)
    (let [channel (socket->channel client)
          channels (atom [])]
      (swap! channels conj channel)
      (channel-> channels channel)))

(defn -main
  "it creates a ServerSocket and waits for client connections, 
   calls the handle-client function for each incoming client."
  [& args]
  (let [server (java.net.ServerSocket. 3001)
        clients (atom [])]
    (println "Waiting for client...")
    (loop []
      (when-let [client (.accept server)]
        (async/go (handle-client clients client))
        (recur)))))