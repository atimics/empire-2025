(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.production :as production]
            [empire.units.dispatcher :as dispatcher]))

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (let [[x y] pos
        height (count @atoms/game-map)
        width (count (first @atoms/game-map))]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx)
                ny (+ y dy)]
          :when (and (>= nx 0) (< nx height)
                     (>= ny 0) (< ny width))]
      [nx ny])))

(defn- attackable-target?
  "Returns true if the cell contains an attackable target for the computer."
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player))))

(defn- find-adjacent-army-target
  "Finds an adjacent attackable target for an army (must be on land/city). Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)]
                     (and (#{:land :city} (:type cell))
                          (attackable-target? cell))))
                 (get-neighbors pos))))

(defn- find-adjacent-target
  "Finds an adjacent attackable target. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (attackable-target? (get-in @atoms/game-map neighbor)))
                 (get-neighbors pos))))

(defn- can-army-move-to?
  "Returns true if an army can move to this cell (land only, not cities)."
  [cell]
  (and (= :land (:type cell))
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-neighbors
  "Returns neighbors an army can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-army-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn- distance
  "Manhattan distance between two positions."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn- find-visible-cities
  "Finds cities visible on computer-map matching the status predicate."
  [status-pred]
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])]
        :when (and (= (:type cell) :city)
                   (status-pred (:city-status cell)))]
    [i j]))

(defn- move-toward
  "Returns the neighbor that moves closest to target."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defn decide-army-move
  "Decides where a computer army should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Move toward free city
   3) Move toward player city 4) Explore"
  [pos]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; No valid moves
      (empty? passable)
      nil

      ;; Move toward nearest free city
      :else
      (let [free-cities (find-visible-cities #{:free})
            player-cities (find-visible-cities #{:player})]
        (cond
          ;; Move toward nearest free city
          (seq free-cities)
          (let [nearest (apply min-key #(distance pos %) free-cities)]
            (move-toward pos nearest passable))

          ;; Move toward nearest player city
          (seq player-cities)
          (let [nearest (apply min-key #(distance pos %) player-cities)]
            (move-toward pos nearest passable))

          ;; Explore - pick first passable neighbor
          :else
          (first passable))))))

(defn- can-ship-move-to?
  "Returns true if a ship can move to this cell."
  [cell]
  (and (= (:type cell) :sea)
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-ship-neighbors
  "Returns neighbors a ship can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-ship-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn- find-visible-player-units
  "Finds player units visible on computer-map."
  []
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])
              contents (:contents cell)]
        :when (and contents (= (:owner contents) :player))]
    [i j]))

(defn decide-ship-move
  "Decides where a computer ship should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Move toward player unit 3) Patrol"
  [pos _ship-type]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-ship-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; No valid moves
      (empty? passable)
      nil

      ;; Move toward nearest player unit
      :else
      (let [player-units (find-visible-player-units)]
        (if (seq player-units)
          (let [nearest (apply min-key #(distance pos %) player-units)]
            (move-toward pos nearest passable))
          ;; Patrol - pick random passable neighbor
          (rand-nth passable))))))

(defn- can-fighter-move-to?
  "Returns true if a fighter can move to this cell (fighters can fly anywhere)."
  [cell]
  (and cell
       (or (nil? (:contents cell))
           (= (:owner (:contents cell)) :player))))

(defn- find-passable-fighter-neighbors
  "Returns neighbors a fighter can move to."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and (can-fighter-move-to? cell)
                   (not (and (:contents cell)
                             (= (:owner (:contents cell)) :computer))))))
          (get-neighbors pos)))

(defn- find-nearest-friendly-city
  "Finds the nearest computer-owned city."
  [pos]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn decide-fighter-move
  "Decides where a computer fighter should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Return to base if low fuel 3) Explore"
  [pos fuel]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-fighter-neighbors pos)
        nearest-city (find-nearest-friendly-city pos)
        dist-to-city (when nearest-city (distance pos nearest-city))]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; No valid moves
      (empty? passable)
      nil

      ;; Return to base if fuel is low (fuel <= distance to city + 1 buffer)
      (and nearest-city dist-to-city (<= fuel (inc dist-to-city)))
      (move-toward pos nearest-city passable)

      ;; Explore - move toward unexplored or random
      :else
      (first passable))))

(defn- move-unit-to
  "Moves a unit from from-pos to to-pos. Returns to-pos."
  [from-pos to-pos]
  (let [from-cell (get-in @atoms/game-map from-pos)
        unit (:contents from-cell)]
    (swap! atoms/game-map assoc-in from-pos (dissoc from-cell :contents))
    (swap! atoms/game-map assoc-in (conj to-pos :contents) unit)
    to-pos))

(defn- attempt-conquest-computer
  "Computer army attempts to conquer a city. Returns new position or nil if army died."
  [army-pos city-pos]
  (let [army-cell (get-in @atoms/game-map army-pos)
        city-cell (get-in @atoms/game-map city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-pos (assoc city-cell :city-status :computer))
        nil)
      ;; Failure - army dies
      (do
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        nil))))

(defn- process-army [pos]
  (when-let [target (decide-army-move pos)]
    (let [target-cell (get-in @atoms/game-map target)]
      (cond
        ;; Attack player unit
        (and (:contents target-cell)
             (= (:owner (:contents target-cell)) :player))
        (combat/attempt-attack pos target)

        ;; Attack hostile city (player or free)
        (and (= (:type target-cell) :city)
             (#{:player :free} (:city-status target-cell)))
        (attempt-conquest-computer pos target)

        ;; Normal move
        :else
        (move-unit-to pos target))))
  nil)

(defn- process-ship [pos ship-type]
  (when-let [target (decide-ship-move pos ship-type)]
    (let [target-cell (get-in @atoms/game-map target)]
      (if (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
        ;; Attack player unit
        (combat/attempt-attack pos target)
        ;; Normal move
        (move-unit-to pos target))))
  nil)

(defn- process-fighter [pos unit]
  (let [fuel (:fuel unit 20)]
    (when-let [target (decide-fighter-move pos fuel)]
      (let [target-cell (get-in @atoms/game-map target)]
        (cond
          ;; Attack player unit
          (and (:contents target-cell)
               (= (:owner (:contents target-cell)) :player))
          (combat/attempt-attack pos target)

          ;; Land at friendly city
          (and (= (:type target-cell) :city)
               (= (:city-status target-cell) :computer))
          (do
            ;; Remove fighter from current position
            (swap! atoms/game-map update-in pos dissoc :contents)
            ;; Add to city airport
            (swap! atoms/game-map update-in [target :fighter-count] (fnil inc 0)))

          ;; Normal move - consume fuel
          :else
          (do
            (move-unit-to pos target)
            (swap! atoms/game-map update-in (conj target :contents :fuel) dec))))))
  nil)

(defn process-computer-unit
  "Processes a single computer unit's turn. Returns nil when done."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= (:owner unit) :computer))
      (case (:type unit)
        :army (process-army pos)
        :fighter (process-fighter pos unit)
        (:transport :destroyer :submarine :patrol-boat :carrier :battleship)
        (process-ship pos (:type unit))
        ;; Satellite - no processing needed
        nil))))

(defn decide-production
  "Decides what a computer city should produce. Returns unit type keyword.
   Phase 1: Always produce armies."
  [_city-pos]
  :army)

(defn process-computer-city
  "Processes a computer city. Sets production if none exists."
  [pos]
  (when-not (@atoms/production pos)
    (let [unit-type (decide-production pos)]
      (production/set-city-production pos unit-type))))
