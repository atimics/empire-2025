(ns empire.computer.transport
  "Computer transport module - VMS Empire style transport movement.
   Loading state: move toward armies
   Unloading state: move toward enemy cities on other continents"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.continent :as continent]
            [empire.pathfinding :as pathfinding]
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
  "Find the nearest army to the transport."
  [transport-pos]
  (let [armies (find-armies-to-load)]
    (when (seq armies)
      (apply min-key
             (fn [[r c]]
               (let [[tr tc] transport-pos]
                 (+ (Math/abs (- r tr)) (Math/abs (- c tc)))))
             armies))))

(defn- adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos atoms/game-map))

(defn- find-unload-target
  "Find an enemy city to unload armies near."
  []
  (let [player-cities (core/find-visible-cities #{:player})
        free-cities (core/find-visible-cities #{:free})]
    (first (concat player-cities free-cities))))

(defn- find-unload-position
  "Find a sea cell adjacent to land near the target city."
  [target-city]
  (let [game-map @atoms/game-map
        [tr tc] target-city]
    ;; Find sea cells within range that are adjacent to land
    (first
     (for [dr (range -3 4)
           dc (range -3 4)
           :let [pos [(+ tr dr) (+ tc dc)]
                 cell (get-in game-map pos)]
           :when (and cell
                      (= :sea (:type cell))
                      (adjacent-to-land? pos)
                      (nil? (:contents cell)))]
       pos))))

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

(defn- unload-armies
  "Unload armies onto adjacent land. Returns true if any unloaded."
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (filter (fn [neighbor]
                                     (let [cell (get-in @atoms/game-map neighbor)]
                                       (and cell
                                            (#{:land :city} (:type cell))
                                            (nil? (:contents cell)))))
                                   (core/get-neighbors pos))
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          ;; Unload armies onto land cells
          (doseq [land-pos (take to-unload land-neighbors)]
            (swap! atoms/game-map assoc-in (conj land-pos :contents)
                   {:type :army :owner :computer :mode :awake :hits 1})
            (visibility/update-cell-visibility land-pos :computer))
          ;; Update transport army count
          (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
          ;; If fully unloaded, change mission to loading
          (when (<= (- army-count to-unload) 0)
            (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) :loading))
          true)))))

(defn- set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission))

(defn process-transport
  "Processes a transport unit using VMS Empire style logic.
   Loading: move toward armies, collect them
   Unloading: move toward enemy cities, drop armies"
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
            ;; Full transport - go unload
            (>= army-count 6)
            (do
              (set-transport-mission pos :unloading)
              (if (adjacent-to-land? pos)
                ;; Try to unload
                (if (unload-armies pos)
                  pos
                  ;; Can't unload here, move to better spot
                  (when-let [target (find-unload-target)]
                    (when-let [unload-pos (find-unload-position target)]
                      (move-toward-position pos unload-pos))))
                ;; Move toward unload position
                (when-let [target (find-unload-target)]
                  (when-let [unload-pos (find-unload-position target)]
                    (move-toward-position pos unload-pos)))))

            ;; Loading transport - go get armies
            (= current-mission :loading)
            (if-let [army-pos (find-nearest-army pos)]
              (move-toward-position pos army-pos)
              ;; No armies found - patrol near coast
              (let [passable (get-passable-sea-neighbors pos)
                    coastal (filter adjacent-to-land? passable)]
                (when-let [target (or (first coastal) (first passable))]
                  (core/move-unit-to pos target)
                  (visibility/update-cell-visibility pos :computer)
                  (visibility/update-cell-visibility target :computer)
                  target)))

            ;; Unloading transport - continue to target
            (= current-mission :unloading)
            (if (adjacent-to-land? pos)
              (if (unload-armies pos)
                pos
                ;; No valid unload spots, move to find more
                (when-let [target (find-unload-target)]
                  (when-let [unload-pos (find-unload-position target)]
                    (move-toward-position pos unload-pos))))
              (when-let [target (find-unload-target)]
                (when-let [unload-pos (find-unload-position target)]
                  (move-toward-position pos unload-pos))))

            :else nil))))))
