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
  "Move patrol boat to an adjacent sea cell that is also adjacent to land."
  [pos]
  (let [passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)
        coastal-cells (filter adjacent-to-land? empty-passable)]
    (when (seq coastal-cells)
      (let [target (rand-nth coastal-cells)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
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

(defn- valid-carrier-position?
  "Returns true if pos is a valid carrier position: empty sea, within fuel range
   of at least one refueling site, and at least carrier-spacing from all sites."
  [pos sites]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :sea (:type cell))
         (nil? (:contents cell))
         (some #(<= (core/distance pos %) config/fighter-fuel) sites)
         (every? #(>= (core/distance pos %) config/carrier-spacing) sites))))

(defn find-carrier-position
  "Finds the first valid carrier position on the map, or nil."
  []
  (let [sites (vec (find-refueling-sites))
        game-map @atoms/game-map]
    (when (seq sites)
      (first (for [i (range (count game-map))
                   j (range (count (first game-map)))
                   :when (valid-carrier-position? [i j] sites)]
               [i j])))))

(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (if (= pos target)
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))
    (move-toward pos target)))

(defn- position-carrier-without-target
  "Handles carrier in positioning mode without a target. Finds one or holds."
  [pos]
  (if-let [new-target (find-carrier-position)]
    (do (swap! atoms/game-map assoc-in (conj pos :contents :carrier-target) new-target)
        (move-toward pos new-target))
    (swap! atoms/game-map update-in (conj pos :contents)
           assoc :carrier-mode :holding)))

(defn- reposition-carrier
  "Handles carrier in repositioning mode. Finds new position or holds."
  [pos]
  (if-let [new-target (find-carrier-position)]
    (do (swap! atoms/game-map update-in (conj pos :contents)
               #(-> % (assoc :carrier-mode :positioning :carrier-target new-target)))
        (move-toward pos new-target))
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
                (explore-sea pos ship-type))))))))))
  nil)
