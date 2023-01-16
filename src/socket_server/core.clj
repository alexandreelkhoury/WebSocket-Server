(ns socket-server.core
  (:gen-class)
  (:require [clojure.core.async :as async]))

(defn read-client-name 
  "reads the client's name from the input stream and returns it"
  [client]
  (let [message (str "Hello, please enter your name : ")
        _ (.write (.getOutputStream client) (.getBytes message))
        buffer (byte-array 1024)
        bytes-read (.read (.getInputStream client) buffer)
        name (when (not= bytes-read -1) (String. (java.util.Arrays/copyOfRange buffer 0 bytes-read) "UTF-8"))]
    name))

(defn send-welcome-message 
  "sends a welcome message to the client, including their name"
  [client name]
  (let [welcome-message (str "Welcome to the chat room, " name)]
    (.write (.getOutputStream client) (.getBytes welcome-message))
    (println (str "New client : " name))))

(defn listen-for-client-message 
  "starts a go-loop to listen for incoming messages from the client and adds them to the in-chan channel"
  [client in-chan]
  (async/go-loop []
    (when (.isConnected client)
      (let [buffer (byte-array 1024)
            bytes-read (.read (.getInputStream client) buffer)
            client-msg (when (not= bytes-read -1) (String. (java.util.Arrays/copyOfRange buffer 0 bytes-read) "UTF-8"))]
        (when client-msg
          (async/>! in-chan client-msg)
          (recur))))))

(defn broadcast-message 
  "starts a go-loop to broadcast messages from the client to all other connected clients"
  [clients client in-chan name]
  (async/go-loop []
    (when-let [client-msg (async/<! in-chan)]
      (doseq [other-client @clients]
        (when (and (not= other-client client) (.isConnected other-client))
          (let [out (.getOutputStream other-client)
                _ (.write out (.getBytes (str "message from " name ": " client-msg)))]
            (println (str "message from " name ": " client-msg)))))
      (recur))))

(defn handle-client
  "the function that handles a client connection, it calls the previously defined functions
   to read the client's name, send a welcome message, add the client to the list of clients, 
   start listening for incoming messages and start broadcasting messages to other clients"
  [clients client]
  (when (.isConnected client)
    (let [in-chan (async/chan)
          name (read-client-name client)
          _ (send-welcome-message client name)
          _ (swap! clients conj client)
          _ (listen-for-client-message client in-chan)
          _ (when name (broadcast-message clients client in-chan name))])))

(defn -main
  "it creates a ServerSocket and waits for client connections, calls the handle-client function for each incoming client."
  [& args]
  (let [server (java.net.ServerSocket. 3001)
        clients (atom [])]
    (println "Waiting for client...")
    (loop []
      (when-let [client (.accept server)]
        (async/go (handle-client clients client))
        (recur)))))
