(ns empire.computer.ship
  "Computer ship module - VMS Empire style ship movement.
   Attack adjacent enemies, explore sea, protect transports, patrol."
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]))

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a ship."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))  ; Can attack player
            (core/get-neighbors pos))))

(defn- find-adjacent-enemy-ship
  "Finds an adjacent enemy ship to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if ship died."
  [ship-pos enemy-pos]
  (let [attacker (get-in @atoms/game-map (conj ship-pos :contents))
        defender (get-in @atoms/game-map (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    ;; Remove attacker from original position
    (swap! atoms/game-map update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      ;; Attacker won - move to enemy position
      (do
        (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
        (visibility/update-cell-visibility ship-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      ;; Attacker lost
      (do
        (visibility/update-cell-visibility ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defn- find-computer-transports
  "Find computer transports to protect."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defn- find-nearest-transport
  "Find the nearest computer transport."
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defn- move-toward
  "Move ship one step toward target."
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      closest)))

(defn- explore-sea
  "Move toward unexplored sea via BFS. Stays put if all sea is explored."
  [pos ship-type]
  (when-let [target (pathfinding/find-nearest-unexplored pos ship-type)]
    (move-toward pos target)))

(defn- find-player-ship-sighting
  "Find the nearest visible player ship position."
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defn- retreat-if-damaged
  "If damaged and under threat, retreat toward friendly city."
  [pos unit]
  (when (threat/should-retreat? pos unit @atoms/computer-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit @atoms/computer-map passable))))

;; --- Patrol boat helpers ---

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- find-adjacent-non-transport-enemy
  "Finds an adjacent player unit that is not a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (not= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- adjacent-to-land?
  "Returns true if the given position has at least one adjacent land or city cell."
  [pos]
  (let [game-map @atoms/game-map]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (core/get-neighbors pos))))

(defn- flee-from
  "Move patrol boat away from the given enemy position."
  [pos enemy-pos]
  (let [passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)]
    (when (seq empty-passable)
      (let [farthest (apply max-key (partial core/distance enemy-pos) empty-passable)]
        (core/move-unit-to pos farthest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility farthest :computer)
        farthest))))

(defn- coastline-move
  "Move patrol boat to an adjacent sea cell that is also adjacent to land.
   Avoids recent positions from patrol-history to prevent backtracking."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:patrol-history unit []))
        passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)
        coastal-cells (filter adjacent-to-land? empty-passable)
        preferred (remove history coastal-cells)
        targets (if (seq preferred) preferred coastal-cells)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        ;; Update patrol history on the moved unit
        (let [new-history (vec (take-last 3 (conj (:patrol-history unit []) pos)))]
          (swap! atoms/game-map assoc-in (conj target :contents :patrol-history) new-history))
        target))))

(defn- process-patrol-boat
  "Processes a computer patrol boat with patrol-specific behavior.
   Priority: Attack adjacent transport > Flee non-transport enemy > Coastline patrol."
  [pos]
  (if-let [transport-pos (find-adjacent-player-transport pos)]
    (attack-enemy pos transport-pos)
    (if-let [enemy-pos (find-adjacent-non-transport-enemy pos)]
      (flee-from pos enemy-pos)
      (coastline-move pos))))

;; --- Destroyer escort helpers ---

(defn- find-unadopted-transport
  "Finds the nearest computer transport without an escort-destroyer-id."
  [pos]
  (let [game-map @atoms/game-map
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :transport (:type unit))
                                    (nil? (:escort-destroyer-id unit)))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial core/distance pos) candidates))))

(defn- adopt-transport
  "Pairs a destroyer at pos with a transport at transport-pos."
  [pos transport-pos]
  (let [destroyer (get-in @atoms/game-map (conj pos :contents))
        transport (get-in @atoms/game-map (conj transport-pos :contents))
        d-id (:destroyer-id destroyer)
        t-id (:transport-id transport)]
    (swap! atoms/game-map update-in (conj pos :contents)
           #(assoc % :escort-transport-id t-id :escort-mode :intercepting))
    (swap! atoms/game-map update-in (conj transport-pos :contents)
           #(assoc % :escort-destroyer-id d-id))))

(defn- find-transport-by-id
  "Finds the position of a transport with the given transport-id."
  [transport-id]
  (let [game-map @atoms/game-map]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :transport (:type unit))
                            (= transport-id (:transport-id unit)))]
             [i j]))))

(defn- process-escort-destroyer
  "Processes a destroyer in escort mode."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:escort-mode unit)]
    (case mode
      :seeking
      (when-let [transport-pos (find-unadopted-transport pos)]
        (adopt-transport pos transport-pos)
        (move-toward pos transport-pos))

      :intercepting
      (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
        (if (<= (core/distance pos transport-pos) 1)
          ;; Adjacent - switch to escorting
          (swap! atoms/game-map update-in (conj pos :contents)
                 assoc :escort-mode :escorting)
          (move-toward pos transport-pos))
        ;; Transport gone - back to seeking
        (do (swap! atoms/game-map update-in (conj pos :contents)
                   #(-> % (assoc :escort-mode :seeking)
                        (dissoc :escort-transport-id)))
            nil))

      :escorting
      (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
        ;; Stay adjacent - if already adjacent, stay put. If not, move closer.
        (when (> (core/distance pos transport-pos) 1)
          (move-toward pos transport-pos))
        ;; Transport gone - back to seeking
        (do (swap! atoms/game-map update-in (conj pos :contents)
                   #(-> % (assoc :escort-mode :seeking)
                        (dissoc :escort-transport-id)))
            nil))

      ;; Default (including nil) - fall through to normal ship behavior
      nil)))

;; --- Carrier positioning helpers ---

(defn find-refueling-sites
  "Returns positions of all computer cities and holding carriers."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])]
          :when (or (and (= :city (:type cell))
                         (= :computer (:city-status cell)))
                    (and (= :carrier (get-in cell [:contents :type]))
                         (= :computer (get-in cell [:contents :owner]))
                         (= :holding (get-in cell [:contents :carrier-mode]))))]
      [i j])))

(defn find-positioning-carrier-targets
  "Returns carrier-target positions of all computer carriers in :positioning mode."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [contents (get-in game-map [i j :contents])]
          :when (and (= :carrier (:type contents))
                     (= :computer (:owner contents))
                     (= :positioning (:carrier-mode contents))
                     (:carrier-target contents))]
      (:carrier-target contents))))

(defn- valid-carrier-position?
  "Returns true if pos is a valid carrier position: empty sea, within fuel range
   of at least one refueling site, and at least carrier-spacing from all spacing sites."
  [pos refuel-sites spacing-sites]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :sea (:type cell))
         (nil? (:contents cell))
         (some #(<= (core/distance pos %) config/fighter-fuel) refuel-sites)
         (every? #(>= (core/distance pos %) config/carrier-spacing) spacing-sites))))

(defn find-carrier-position
  "Finds a valid carrier position between two refueling sites, preferring
   positions closest to the midpoint of distant site pairs. Falls back to
   first valid position by top-left scan."
  []
  (let [refuel-sites (find-refueling-sites)
        positioning-targets (find-positioning-carrier-targets)
        spacing-sites (into refuel-sites positioning-targets)]
    (when (seq refuel-sites)
      (let [pairs (for [a refuel-sites
                        b refuel-sites
                        :when (and (not= a b)
                                   (> (core/distance a b) config/carrier-spacing)
                                   (<= (core/distance a b) (* 2 config/fighter-fuel)))]
                    [a b])
            sorted-pairs (sort-by (fn [[a b]] (- (core/distance a b))) pairs)]
        (or
          (first
            (for [[a b] sorted-pairs
                  :let [mid [(quot (+ (first a) (first b)) 2)
                             (quot (+ (second a) (second b)) 2)]
                        radius 8
                        candidates (for [dr (range (- radius) (inc radius))
                                         dc (range (- radius) (inc radius))
                                         :let [pos [(+ (first mid) dr)
                                                    (+ (second mid) dc)]]
                                         :when (valid-carrier-position? pos refuel-sites spacing-sites)]
                                     pos)]
                  :when (seq candidates)
                  :let [best (apply min-key #(core/distance % mid) candidates)]]
              best))
          (let [game-map @atoms/game-map]
            (first (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :when (valid-carrier-position? [i j] refuel-sites spacing-sites)]
                     [i j]))))))))

(declare position-carrier-without-target)

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target.
   Validates target; uses pathfinding for movement. CC=4."
  [pos target]
  (cond
    (= pos target)
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))

    (let [refuel-sites (vec (find-refueling-sites))
          spacing-sites (into refuel-sites (vec (find-positioning-carrier-targets)))]
      (not (valid-carrier-position? target refuel-sites spacing-sites)))
    (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :carrier-target)
        (position-carrier-without-target pos))

    :else
    (when-let [next-pos (pathfinding/next-step pos target :carrier)]
      (core/move-unit-to pos next-pos)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-pos :computer)
      next-pos)))

(defn- position-carrier-without-target
  "Handles carrier in positioning mode without a target. Finds one or holds."
  [pos]
  (if-let [new-target (find-carrier-position)]
    (do (swap! atoms/game-map assoc-in (conj pos :contents :carrier-target) new-target)
        (when-let [next-pos (pathfinding/next-step pos new-target :carrier)]
          (core/move-unit-to pos next-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          next-pos))
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :carrier-mode :holding)))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [new-target (find-carrier-position)]
    (do (swap! atoms/game-map update-in (conj pos :contents)
               #(-> % (assoc :carrier-mode :positioning :carrier-target new-target)))
        (when-let [next-pos (pathfinding/next-step pos new-target :carrier)]
          (core/move-unit-to pos next-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          next-pos))
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :carrier-mode :holding)))

(defn- process-carrier
  "Processes a computer carrier based on its carrier-mode.
   CC=5: case(3 branches + default) + if(target)."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:carrier-mode unit)]
    (case mode
      :positioning
      (let [target (:carrier-target unit)]
        (if target
          (position-carrier-with-target pos target)
          (position-carrier-without-target pos)))

      :holding nil

      :repositioning (reposition-carrier pos)

      nil)))

;; --- Carrier group escort helpers (battleship + submarine) ---

(def orbit-ring
  "16 offsets forming a clockwise Chebyshev ring at radius 2."
  [[-2 -2] [-2 -1] [-2 0] [-2 1] [-2 2]
   [-1 2] [0 2] [1 2]
   [2 2] [2 1] [2 0] [2 -1] [2 -2]
   [1 -2] [0 -2] [-1 -2]])

(defn- find-carrier-by-id
  "Finds the position of a carrier with the given carrier-id."
  [carrier-id]
  (let [game-map @atoms/game-map]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :carrier (:type unit))
                            (= carrier-id (:carrier-id unit)))]
             [i j]))))

(defn- find-carrier-with-open-slot
  "Finds the nearest computer carrier with an open slot for the given unit type."
  [pos unit-type]
  (let [game-map @atoms/game-map
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :carrier (:type unit))
                                    (= :computer (:owner unit))
                                    (case unit-type
                                      :battleship (nil? (:group-battleship-id unit))
                                      :submarine (< (count (:group-submarine-ids unit [])) 2)
                                      false))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial core/distance pos) candidates))))

(defn- initial-orbit-angle
  "Returns the starting orbit angle for a new escort."
  [unit-type carrier]
  (case unit-type
    :battleship 0
    :submarine (if (empty? (:group-submarine-ids carrier [])) 5 11)))

(defn- adopt-carrier-escort
  "Pairs a battleship or submarine escort with a carrier."
  [pos carrier-pos unit-type]
  (let [escort (get-in @atoms/game-map (conj pos :contents))
        carrier (get-in @atoms/game-map (conj carrier-pos :contents))
        carrier-id (:carrier-id carrier)
        escort-id (:escort-id escort)
        angle (initial-orbit-angle unit-type carrier)]
    ;; Update escort
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :escort-carrier-id carrier-id
                 :escort-mode :intercepting
                 :orbit-angle angle)
    ;; Update carrier group
    (case unit-type
      :battleship
      (swap! atoms/game-map update-in (conj carrier-pos :contents)
             assoc :group-battleship-id escort-id)
      :submarine
      (swap! atoms/game-map update-in (conj carrier-pos :contents)
             update :group-submarine-ids conj escort-id))))

(defn- orbit-target-pos
  "Computes the absolute position for an orbit angle around carrier."
  [carrier-pos angle]
  (let [[dr dc] (nth orbit-ring (mod angle 16))]
    [(+ (first carrier-pos) dr) (+ (second carrier-pos) dc)]))

(defn- valid-orbit-pos?
  "Returns true if pos is a valid empty sea cell on the game map."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and cell (= :sea (:type cell)) (nil? (:contents cell)))))

(defn- find-next-orbit-angle
  "Finds the next orbit angle with a valid sea position, starting from start-angle.
   Returns nil if all 16 positions are invalid."
  [carrier-pos start-angle]
  (first (for [i (range 16)
               :let [angle (mod (+ start-angle i) 16)
                     pos (orbit-target-pos carrier-pos angle)]
               :when (valid-orbit-pos? pos)]
           angle)))

(defn- revert-escort-to-seeking
  "Reverts an escort to seeking mode, clearing carrier reference."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         #(-> % (assoc :escort-mode :seeking)
              (dissoc :escort-carrier-id :orbit-angle))))

(defn- process-escort-seeking
  "Escort seeking: find a carrier with an open slot and adopt it."
  [pos unit-type]
  (when-let [carrier-pos (find-carrier-with-open-slot pos unit-type)]
    (adopt-carrier-escort pos carrier-pos unit-type)
    (move-toward pos carrier-pos)))

(defn- process-escort-intercepting
  "Escort intercepting: move toward carrier, transition to orbiting at radius 2."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (find-carrier-by-id (:escort-carrier-id unit))]
      (if (<= (core/chebyshev-distance pos carrier-pos) 2)
        ;; At orbit radius - start orbiting
        (let [angle (or (:orbit-angle unit) 0)
              valid-angle (find-next-orbit-angle carrier-pos angle)]
          (if valid-angle
            (let [target (orbit-target-pos carrier-pos valid-angle)]
              (when (not= pos target)
                (move-toward pos target))
              (swap! atoms/game-map update-in
                     (conj (or (when (not= pos target) target) pos) :contents)
                     assoc :escort-mode :orbiting :orbit-angle valid-angle))
            ;; No valid orbit position - stay and orbit
            (swap! atoms/game-map update-in (conj pos :contents)
                   assoc :escort-mode :orbiting)))
        ;; Not at radius yet - move closer
        (move-toward pos carrier-pos))
      ;; Carrier gone
      (revert-escort-to-seeking pos))))

(defn- process-escort-orbiting
  "Escort orbiting: advance one step along the orbit ring."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (if-let [carrier-pos (find-carrier-by-id (:escort-carrier-id unit))]
      (let [current-angle (or (:orbit-angle unit) 0)
            next-angle (find-next-orbit-angle carrier-pos (inc current-angle))]
        (if next-angle
          (let [target (orbit-target-pos carrier-pos next-angle)]
            (if (= pos target)
              ;; Already at target, just update angle
              (swap! atoms/game-map update-in (conj pos :contents)
                     assoc :orbit-angle next-angle)
              ;; Move to next orbit position
              (when (valid-orbit-pos? target)
                (core/move-unit-to pos target)
                (visibility/update-cell-visibility pos :computer)
                (visibility/update-cell-visibility target :computer)
                (swap! atoms/game-map update-in (conj target :contents)
                       assoc :orbit-angle next-angle))))
          ;; No valid orbit positions - stay put
          nil))
      ;; Carrier gone
      (revert-escort-to-seeking pos))))

(defn- process-carrier-group-escort
  "Processes a battleship or submarine in carrier group escort mode."
  [pos unit-type]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        mode (:escort-mode unit)]
    (case mode
      :seeking (process-escort-seeking pos unit-type)
      :intercepting (process-escort-intercepting pos)
      :orbiting (process-escort-orbiting pos)
      nil)))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Priority: Retreat if damaged > Attack adjacent > Escort transports > Hunt enemies > Explore
   Returns nil after processing - ships only move once per round."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))

      ;; Patrol boat special behavior (when it has patrol fields)
      (if (and (= :patrol-boat ship-type)
               (:patrol-country-id unit))
        (process-patrol-boat pos)

      ;; Carrier positioning behavior
      (if (and (= :carrier ship-type)
               (:carrier-mode unit))
        (process-carrier pos)

      ;; Priority 0: Retreat if damaged and under threat
      (if-let [retreat-pos (retreat-if-damaged pos unit)]
        (do
          (core/move-unit-to pos retreat-pos)
          (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility retreat-pos :computer))

        ;; Priority 1: Attack adjacent enemy ship
        (if-let [enemy-pos (find-adjacent-enemy-ship pos)]
          (attack-enemy pos enemy-pos)

          ;; Priority 2: Destroyers with escort mode
          (if (and (= :destroyer ship-type) (:escort-mode unit))
            (or (process-escort-destroyer pos)
                (explore-sea pos ship-type))

          ;; Priority 2b: Battleship/Submarine carrier group escort
          (if (and (#{:battleship :submarine} ship-type) (:escort-mode unit))
            (or (process-carrier-group-escort pos ship-type)
                (explore-sea pos ship-type))

            ;; Priority 3: Destroyers without escort - escort transports (legacy)
            (if (and (= :destroyer ship-type)
                     (find-nearest-transport pos))
              (let [transport-pos (find-nearest-transport pos)]
                (if (> (core/distance pos transport-pos) 2)
                  (move-toward pos transport-pos)
                  (explore-sea pos ship-type)))

              ;; Priority 4: Hunt player ships
              (if-let [enemy-sighting (find-player-ship-sighting pos)]
                (move-toward pos enemy-sighting)

                ;; Priority 5: Explore sea
                (explore-sea pos ship-type)))))))))))
  nil)
