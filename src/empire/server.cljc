(ns empire.server
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.tutorial.core :as tutorial]
            [empire.tutorial.scenarios :as tutorial-scenarios]
            [empire.ui.core :as core]
            [empire.ui.input :as input]
            [empire.ui.rendering :as rendering]
            [empire.ui.rendering-util :as ru]
            [clojure.string :as str]
            [jsonista.core :as json]
            [org.httpkit.server :as http])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService]))

;; --- State ---

(defonce connected-clients (atom #{}))
(defonce previous-state (atom nil))
(defonce game-loop-executor (atom nil))
(defonce last-hover-cell (atom nil)) ;; [col row] from client

;; --- Cell serialization ---

(defn- serialize-unit [unit]
  (when (and unit (:type unit) (:owner unit))
    {:t (name (:type unit))
     :o (name (:owner unit))
     :m (name (or (:mode unit) :awake))
     :h (:hits unit)
     :fuel (:fuel unit)
     :marching-orders (:marching-orders unit)
     :flight-path (:flight-path unit)
     :transport-mission (:transport-mission unit)}))

(defn- serialize-cell [cell production-entry]
  (when cell
    (let [base {:t (name (or (:type cell) :unexplored))}]
      (cond-> base
        (:city-status cell) (assoc :cs (name (:city-status cell)))
        (:contents cell) (assoc :u (serialize-unit (:contents cell)))
        (:waypoint cell) (assoc :wp true)
        (pos? (:fighter-count cell 0)) (assoc :fc (:fighter-count cell))
        (pos? (:army-count cell 0)) (assoc :ac (:army-count cell))
        (pos? (:awake-fighters cell 0)) (assoc :af (:awake-fighters cell))
        (pos? (:awake-armies cell 0)) (assoc :aa (:awake-armies cell))
        (:country-id cell) (assoc :cid (:country-id cell))
        (and production-entry (:item production-entry))
        (assoc :prod {:item (name (:item production-entry))
                      :remaining (:remaining-rounds production-entry)})))))

(defn- serialize-map [the-map production]
  (let [cols (count the-map)]
    (vec (for [col (range cols)]
           (let [rows (count (nth the-map col))]
             (vec (for [row (range rows)]
                    (serialize-cell (get-in the-map [col row])
                                   (get production [col row])))))))))

;; --- State snapshot ---

(defn- build-state-snapshot []
  (let [the-map (case @atoms/map-to-display
                  :player-map @atoms/player-map
                  :computer-map @atoms/computer-map
                  :actual-map @atoms/game-map)]
    {:type "state"
     :map_size @atoms/map-size
     :cells (serialize-map the-map @atoms/production)
     :round @atoms/round-number
     :paused @atoms/paused
     :pause_requested @atoms/pause-requested
     :waiting_for_input @atoms/waiting-for-input
     :attention_coords @atoms/cells-needing-attention
     :attention_message @atoms/attention-message
     :turn_message @atoms/turn-message
     :error_message @atoms/error-message
     :error_until @atoms/error-until
     :hover_message @atoms/hover-message
     :production_status @atoms/production-status
     :destination @atoms/destination
     :map_to_display (name @atoms/map-to-display)
     :debug_message @atoms/debug-message
     :load_menu (when @atoms/load-menu-open
                  {:files @atoms/load-menu-files
                   :hovered @atoms/load-menu-hovered})
     :tutorial (when @atoms/tutorial-active
                 {:page_text (or (tutorial/current-page-text) "")
                  :page_index @atoms/tutorial-page-index
                  :page_count (count @atoms/tutorial-pages)
                  :scenario_name (:name (tutorial-scenarios/get-scenario @atoms/tutorial-scenario-id) "")
                  :overlay_visible @atoms/tutorial-overlay-visible})
     :tutorial_menu (when @atoms/tutorial-menu-open
                      {:scenarios (mapv (fn [{:keys [id name description]}]
                                         {:id (clojure.core/name id)
                                          :name name
                                          :description description})
                                       @atoms/tutorial-scenarios-list)})}))

;; --- Broadcasting ---

(defn- send-json! [channel data]
  (try
    (http/send! channel (json/write-value-as-string data))
    (catch Exception _)))

(defn- broadcast! [data]
  (doseq [ch @connected-clients]
    (send-json! ch data)))

(defn- broadcast-state! []
  (let [state (build-state-snapshot)]
    (reset! previous-state state)
    (broadcast! state)))

;; --- Hover from cell coordinates ---

(defn- update-hover-from-cell! [col row]
  (let [[cols rows] @atoms/map-size]
    (if (and col row (>= col 0) (< col cols) (>= row 0) (< row rows))
      (let [coords [col row]
            the-map (case @atoms/map-to-display
                      :player-map @atoms/player-map
                      :computer-map @atoms/computer-map
                      :actual-map @atoms/game-map)
            cell (get-in the-map coords)
            production (get @atoms/production coords)
            status (ru/format-hover-status coords cell production)]
        (reset! atoms/hover-message (or status "")))
      (reset! atoms/hover-message ""))))

;; --- Input handling ---

(defn- handle-client-message! [_channel msg-str]
  (try
    (let [msg (json/read-value msg-str)]
      (case (get msg "type")
        "key"
        (let [k (keyword (get msg "key"))
              mouse-x (get msg "mouse_x" 0)
              mouse-y (get msg "mouse_y" 0)]
          (when (nil? @atoms/last-key)
            (input/key-down k mouse-x mouse-y))
          (reset! atoms/last-key k))

        "key_up"
        (reset! atoms/last-key nil)

        "click"
        (let [col (get msg "col")
              row (get msg "row")
              button (keyword (get msg "button" "left"))]
          (input/mouse-down-cell col row button))

        "hover"
        (let [col (get msg "col")
              row (get msg "row")]
          (reset! last-hover-cell (when (and col row) [col row]))
          (update-hover-from-cell! col row))

        "tutorial_select"
        (let [id (keyword (get msg "id"))]
          (tutorial/start-tutorial! id)
          (core/calculate-screen-dimensions))

        nil))
    (catch Exception e
      (println "Error handling client message:" (.getMessage e)))))

;; --- WebSocket handler ---

(defn ws-handler [req]
  (http/as-channel req
    {:on-open (fn [channel]
                (println "Client connected")
                (swap! connected-clients conj channel)
                (send-json! channel (build-state-snapshot)))
     :on-close (fn [channel _status]
                 (println "Client disconnected")
                 (swap! connected-clients disj channel))
     :on-receive (fn [channel msg]
                   (handle-client-message! channel msg))}))

;; --- Static file serving ---

(def content-types
  {"html" "text/html"
   "js"   "application/javascript"
   "wasm" "application/wasm"
   "css"  "text/css"
   "json" "application/json"})

(defn- serve-static [req]
  (let [uri (:uri req)
        path (if (= uri "/") "/index.html" uri)
        file (java.io.File. (str "resources/public" path))]
    (if (.exists file)
      (let [ext (last (str/split (.getName file) #"\."))
            content-type (get content-types ext "application/octet-stream")]
        {:status 200
         :headers {"Content-Type" content-type}
         :body file})
      {:status 404
       :body "Not found"})))

;; --- HTTP handler ---

(defn app [req]
  (if (= "/ws" (:uri req))
    (ws-handler req)
    (serve-static req)))

;; --- Headless game loop ---

(defn- game-tick! []
  (try
    (game-loop/update-player-map)
    (game-loop/update-computer-map)
    (game-loop/advance-game-batch)
    ;; Update hover from last known cell coordinates
    (when-let [[col row] @last-hover-cell]
      (update-hover-from-cell! col row))
    ;; Broadcast state to all clients
    (broadcast-state!)
    (catch Exception e
      (println "Game tick error:" (.getMessage e))
      (.printStackTrace e))))

(defn- start-game-loop! []
  (let [^ScheduledExecutorService executor (Executors/newSingleThreadScheduledExecutor)]
    (reset! game-loop-executor executor)
    (.scheduleAtFixedRate executor
      ^Runnable game-tick!
      0 33 TimeUnit/MILLISECONDS)
    executor))

;; --- Entry point ---

(defn -main [& args]
  (let [[cols rows] (if (>= (count args) 2)
                      [(Integer/parseInt (first args))
                       (Integer/parseInt (second args))]
                      config/default-map-size)]
    ;; Initialize map size
    (reset! atoms/map-size [cols rows])
    (reset! atoms/map-size-constants (config/compute-size-constants cols rows))
    ;; Compute screen dimensions (used for coordinate calculations)
    (core/calculate-screen-dimensions)
    ;; Generate the map
    (let [num-cities (:number-of-cities @atoms/map-size-constants config/number-of-cities)]
      (init/make-initial-map @atoms/map-size config/smooth-count config/land-fraction num-cities config/min-city-distance))
    ;; Start headless game loop
    (start-game-loop!)
    (println (format "Empire server started. Map size: [%d %d]" cols rows))
    (println "Open http://localhost:8080")
    ;; Start HTTP/WS server
    (http/run-server app {:port 8080})
    (println "WebSocket server running on port 8080")))
