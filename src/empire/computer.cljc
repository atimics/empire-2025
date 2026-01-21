(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.pathfinding :as pathfinding]
            [empire.production :as production]
            [empire.units.dispatcher :as dispatcher]))

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn- attackable-target?
  "Returns true if the cell contains an attackable target for the computer."
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player))))

(defn- find-adjacent-army-target
  "Finds an adjacent attackable target for an army (must be on land/city).
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/computer-map neighbor)]
                     (and (#{:land :city} (:type cell))
                          (attackable-target? cell))))
                 (get-neighbors pos))))

(defn- find-adjacent-target
  "Finds an adjacent attackable target.
   Uses computer-map to respect fog of war. Returns coords or nil."
  [pos]
  (first (filter (fn [neighbor]
                   (attackable-target? (get-in @atoms/computer-map neighbor)))
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

;; Threat Assessment Functions

(defn unit-threat
  "Returns threat value for a unit type.
   Higher values = more dangerous."
  [unit-type]
  (case unit-type
    :battleship 10
    :carrier 8
    :destroyer 6
    :submarine 5
    :fighter 4
    :patrol-boat 3
    :army 2
    :transport 1
    0))

(defn threat-level
  "Calculates threat level at position based on nearby enemy units.
   Checks all cells within radius 2 of position.
   Returns sum of threat values for nearby enemy units."
  [computer-map position]
  (let [radius 2
        [px py] position]
    (reduce + 0
            (for [dx (range (- radius) (inc radius))
                  dy (range (- radius) (inc radius))
                  :let [x (+ px dx)
                        y (+ py dy)
                        cell (get-in computer-map [x y])]
                  :when (and cell
                             (:contents cell)
                             (= (:owner (:contents cell)) :player))]
              (unit-threat (:type (:contents cell)))))))

(defn safe-moves
  "Filters moves to avoid high-threat areas when unit is damaged.
   Returns moves sorted by threat level (safest first).
   If unit is at full health, returns all moves unchanged."
  [computer-map _position unit possible-moves]
  (let [max-hits (dispatcher/hits (:type unit))
        current-hits (:hits unit max-hits)
        damaged? (< current-hits max-hits)]
    (if damaged?
      (sort-by #(threat-level computer-map %) possible-moves)
      possible-moves)))

(defn should-retreat?
  "Returns true if the unit should retreat rather than engage."
  [pos unit computer-map]
  (let [unit-type (:type unit)
        max-hits (dispatcher/hits unit-type)
        current-hits (:hits unit max-hits)
        threat (threat-level computer-map pos)]
    (or
      ;; Damaged and under threat
      (and (< current-hits max-hits) (> threat 3))
      ;; Transport carrying armies - always cautious
      (and (= unit-type :transport)
           (> (:army-count unit 0) 0)
           (> threat 5))
      ;; Severely damaged (< 50% health)
      (< current-hits (/ max-hits 2)))))

(defn- find-nearest-friendly-base
  "Finds the nearest computer-owned city."
  [pos _unit-type]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn retreat-move
  "Returns best retreat move toward nearest friendly city.
   Returns nil if no safe retreat available."
  [pos unit computer-map passable-moves]
  (when (seq passable-moves)
    (let [nearest-city (find-nearest-friendly-base pos (:type unit))]
      (when nearest-city
        (let [safe (safe-moves computer-map pos unit passable-moves)]
          (when (seq safe)
            ;; Pick move that's both safe and moves toward base
            (apply min-key #(+ (distance % nearest-city)
                               (* 2 (threat-level computer-map %)))
                   safe)))))))

(defn- move-toward-city-or-explore
  "Moves army toward nearest free/player city using A*, or explores."
  [pos passable]
  (let [free-cities (find-visible-cities #{:free})
        player-cities (find-visible-cities #{:player})]
    (cond
      (seq free-cities)
      (let [nearest (apply min-key #(distance pos %) free-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (move-toward pos nearest passable)))

      (seq player-cities)
      (let [nearest (apply min-key #(distance pos %) player-cities)]
        (or (pathfinding/next-step pos nearest :army)
            (move-toward pos nearest passable)))

      :else
      (first passable))))

(defn decide-army-move
  "Decides where a computer army should move. Returns target coords or nil.
   Priority: 1) Attack adjacent target 2) Retreat if damaged 3) Move toward free city
   4) Move toward player city 5) Explore. Uses A* pathfinding when available."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-target pos)
        passable (find-passable-neighbors pos)]
    (cond
      adjacent-target adjacent-target
      (should-retreat? pos unit @atoms/computer-map) (retreat-move pos unit @atoms/computer-map passable)
      (empty? passable) nil
      :else (move-toward-city-or-explore pos passable))))

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
   Priority: 1) Attack adjacent target 2) Retreat if damaged 3) Move toward player unit
   4) Patrol. Uses threat avoidance when damaged."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-target pos)
        passable (find-passable-ship-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target
      adjacent-target

      ;; Check if should retreat
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid moves
      (empty? passable)
      nil

      ;; Move toward nearest player unit with threat awareness
      :else
      (let [player-units (find-visible-player-units)
            safe-passable (safe-moves @atoms/computer-map pos unit passable)]
        (if (seq player-units)
          (let [nearest (apply min-key #(distance pos %) player-units)]
            (or (pathfinding/next-step pos nearest ship-type)
                (move-toward pos nearest safe-passable)))
          ;; Patrol - pick safe random neighbor
          (if (seq safe-passable)
            (rand-nth safe-passable)
            (rand-nth passable)))))))

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
   Priority: 1) Attack adjacent target 2) Return to base if low fuel 3) Explore.
   Uses A* pathfinding for better navigation."
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

      ;; Return to base if fuel is low (fuel <= distance to city + 2 buffer for safety)
      (and nearest-city dist-to-city (<= fuel (+ dist-to-city 2)))
      (or (pathfinding/next-step pos nearest-city :fighter)
          (move-toward pos nearest-city passable))

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
