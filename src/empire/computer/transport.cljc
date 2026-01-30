(ns empire.computer.transport
  "Computer transport module - VMS Empire style transport movement.
   Loading state: move toward armies
   Unloading state: move toward enemy cities on other continents"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.continent :as continent]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]
            [empire.movement.map-utils :as map-utils]))

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn- find-armies-to-load
  "Find computer armies that should board transports."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit)))]
      [i j])))

(defn- find-nearest-army
  "Find the nearest army to the transport. When pickup-continent is provided,
   only considers armies on that continent (so the transport doesn't re-load
   armies it just dropped off on an enemy continent)."
  [transport-pos pickup-continent]
  (let [armies (find-armies-to-load)
        candidates (if pickup-continent
                     (filter #(contains? pickup-continent %) armies)
                     armies)]
    (when (seq candidates)
      (apply min-key #(core/distance transport-pos %) candidates))))

(defn- adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos atoms/game-map))

(defn- find-adjacent-land-pos
  "Returns the first adjacent land or city position, or nil."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn- score-target-city
  "Score a target city for a transport. Lower = more attractive.
   Factors: distance, continent attackable cities, computer presence."
  [transport-pos target-city]
  (let [dist (core/distance transport-pos target-city)
        target-continent (continent/flood-fill-continent target-city)
        scan (when target-continent (continent/scan-continent target-continent))
        attackable (+ (:player-cities scan 0) (:free-cities scan 0))
        continent-factor (if (pos? attackable)
                           (/ 100.0 attackable)
                           100.0)
        presence-penalty (if (pos? (:computer-cities scan 0)) 10.0 1.0)]
    (* dist continent-factor presence-penalty)))

(defn find-unload-target
  "Find best enemy city to unload near, excluding pickup continent.
   Prefers unclaimed targets to spread transports."
  [pickup-continent transport-pos]
  (let [player-cities (core/find-visible-cities #{:player})
        free-cities (core/find-visible-cities #{:free})
        all-targets (concat player-cities free-cities)
        off-continent (if pickup-continent
                        (remove #(contains? pickup-continent %) all-targets)
                        all-targets)]
    (when (seq off-continent)
      (let [claimed @atoms/claimed-transport-targets
            unclaimed (remove claimed off-continent)
            candidates (if (seq unclaimed) unclaimed off-continent)
            best (apply min-key
                        #(score-target-city transport-pos %)
                        candidates)]
        (when best
          (swap! atoms/claimed-transport-targets conj best)
          best)))))

(defn- find-unload-position
  "Find a sea cell adjacent to land near the target city, closest to transport.
   Excludes positions whose adjacent land is on the pickup continent."
  [target-city pickup-continent transport-pos]
  (let [game-map @atoms/game-map
        [tr tc] target-city
        candidates (for [dr (range -3 4)
                         dc (range -3 4)
                         :let [pos [(+ tr dr) (+ tc dc)]
                               cell (get-in game-map pos)]
                         :when (and cell
                                    (= :sea (:type cell))
                                    (adjacent-to-land? pos)
                                    (nil? (:contents cell))
                                    (or (nil? pickup-continent)
                                        (let [adj-land (find-adjacent-land-pos pos)]
                                          (not (contains? pickup-continent adj-land)))))]
                     pos)]
    (when (seq candidates)
      (apply min-key #(core/distance transport-pos %) candidates))))

(defn- move-toward-position
  "Move transport one step toward target. Returns new position."
  [pos target]
  (if-let [next-step (pathfinding/next-step pos target :transport)]
    (do
      (core/move-unit-to pos next-step)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-step :computer)
      next-step)
    ;; No path - try direct movement
    (let [passable (get-passable-sea-neighbors pos)
          closest (core/move-toward pos target passable)]
      (when closest
        (core/move-unit-to pos closest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(defn- explore-sea
  "Move transport toward unexplored sea via BFS. Stays put if all sea is explored."
  [pos]
  (when-let [target (pathfinding/find-nearest-unexplored pos :transport)]
    (move-toward-position pos target)))

(defn- find-next-pickup-continent-pos
  "After unloading, find the nearest continent with >3 computer armies,
   excluding the current unload continent. Returns an army position on
   that continent, or nil if none qualifies."
  [transport-pos current-continent]
  (let [game-map @atoms/game-map
        all-armies (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :army (:type unit))
                                    (or (nil? current-continent)
                                        (not (contains? current-continent [i j]))))]
                     [i j])]
    ;; Group armies by continent, avoiding redundant flood-fills
    (loop [remaining all-armies
           seen #{}
           continents []]
      (if (empty? remaining)
        ;; Find nearest qualifying continent (>3 armies)
        (let [qualifying (filter #(> (count (:armies %)) 3) continents)]
          (when (seq qualifying)
            (let [best (apply min-key
                              (fn [{:keys [armies]}]
                                (apply min (map #(core/distance transport-pos %) armies)))
                              qualifying)]
              ;; Return the nearest army position from the best continent
              (apply min-key #(core/distance transport-pos %) (:armies best)))))
        (let [army-pos (first remaining)]
          (if (contains? seen army-pos)
            (recur (rest remaining) seen continents)
            (let [cont (continent/flood-fill-continent army-pos)
                  cont-armies (filter #(contains? cont %) all-armies)]
              (recur (rest remaining)
                     (into seen cont)
                     (conj continents {:continent cont :armies cont-armies})))))))))

(defn unload-armies
  "Unload armies onto adjacent land, excluding pickup continent. Returns true if any unloaded."
  [pos pickup-continent]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (filter (fn [neighbor]
                                     (let [cell (get-in @atoms/game-map neighbor)]
                                       (and cell
                                            (#{:land :city} (:type cell))
                                            (nil? (:contents cell))
                                            (or (nil? pickup-continent)
                                                (not (contains? pickup-continent neighbor))))))
                                   (core/get-neighbors pos))
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          ;; Unload armies onto land cells
          (let [unload-eid (:unload-event-id transport)]
            (doseq [land-pos (take to-unload land-neighbors)]
              (swap! atoms/game-map assoc-in (conj land-pos :contents)
                     (cond-> {:type :army :owner :computer :mode :awake :hits 1}
                       unload-eid (assoc :unload-event-id unload-eid)))
              (visibility/update-cell-visibility land-pos :computer)))
          ;; Update transport army count
          (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
          ;; If fully unloaded, change mission to loading and update pickup continent
          (when (<= (- army-count to-unload) 0)
            (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) :loading)
            (let [current-continent (when-let [land-pos (find-adjacent-land-pos pos)]
                                     (continent/flood-fill-continent land-pos))
                  next-pickup (find-next-pickup-continent-pos pos current-continent)]
              (swap! atoms/game-map assoc-in
                     (conj pos :contents :pickup-continent-pos) next-pickup)))
          true)))))

(defn- set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission))

(defn- mint-unload-event-id
  "Mint a new unload-event-id when transport transitions to unloading."
  [pos transport]
  (when-not (:unload-event-id transport)
    (let [id @atoms/next-unload-event-id]
      (swap! atoms/next-unload-event-id inc)
      (swap! atoms/game-map assoc-in
             (conj pos :contents :unload-event-id) id))))

(defn- record-pickup-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the pickup continent reference point."
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (swap! atoms/game-map assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos))))

(defn- move-toward-unload-or-explore
  "Pick an enemy/free city off the pickup continent, then BFS for the nearest
   sea cell adjacent to that city's continent. Falls back to explore-sea."
  [pos pickup-continent]
  (if-let [target-city (find-unload-target pickup-continent pos)]
    (let [target-continent (continent/flood-fill-continent target-city)]
      (if-let [unload-pos (pathfinding/find-nearest-unload-position pos target-continent)]
        (move-toward-position pos unload-pos)
        (explore-sea pos)))
    (explore-sea pos)))

(defn process-transport
  "Processes a transport unit using VMS Empire style logic.
   Loading: move toward armies, collect them
   Unloading: move toward enemy cities on OTHER continents, drop armies
   Returns nil after processing - transports only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (let [army-count (:army-count transport 0)
            mission (:transport-mission transport :idle)]

        ;; Determine mission if idle
        (when (= mission :idle)
          (set-transport-mission pos :loading))

        (let [current-mission (or (:transport-mission transport) :loading)]
          (cond
            ;; Full transport - go unload on a different continent
            (>= army-count 6)
            (do
              (set-transport-mission pos :unloading)
              (mint-unload-event-id pos transport)
              (record-pickup-continent-pos pos transport)
              (let [updated-transport (get-in @atoms/game-map (conj pos :contents))
                    pickup-continent (when-let [ocp (:pickup-continent-pos updated-transport)]
                                      (continent/flood-fill-continent ocp))]
                (if (adjacent-to-land? pos)
                  (when-not (unload-armies pos pickup-continent)
                    (move-toward-unload-or-explore pos pickup-continent))
                  (move-toward-unload-or-explore pos pickup-continent))))

            ;; Loading transport - go get armies (only on pickup continent if known)
            (= current-mission :loading)
            (let [pickup-continent (when-let [ocp (:pickup-continent-pos transport)]
                                    (continent/flood-fill-continent ocp))]
              (if-let [army-pos (find-nearest-army pos pickup-continent)]
                (move-toward-position pos army-pos)
                (explore-sea pos)))

            ;; Unloading transport - continue to target on different continent
            (= current-mission :unloading)
            (let [pickup-continent (when-let [ocp (:pickup-continent-pos transport)]
                                     (continent/flood-fill-continent ocp))]
              (if (adjacent-to-land? pos)
                (when-not (unload-armies pos pickup-continent)
                  (move-toward-unload-or-explore pos pickup-continent))
                (move-toward-unload-or-explore pos pickup-continent)))

            :else nil)))))
  nil)
