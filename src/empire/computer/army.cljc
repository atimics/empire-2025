(ns empire.computer.army
  "Computer army module - VMS Empire style army movement.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.atoms :as atoms]
            [empire.player.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.continent :as continent]
            [empire.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]))

(defn- get-passable-neighbors
  "Returns passable land neighbors for an army."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (#{:land :city} (:type cell)))))
            (core/get-neighbors pos))))

(defn- find-adjacent-enemy
  "Finds an adjacent enemy unit or city to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)]
                       (core/attackable-target? cell)))
                   (core/get-neighbors pos)))))

(defn- attack-enemy
  "Attack an adjacent enemy. Returns new position or nil if army died."
  [army-pos enemy-pos]
  (let [enemy-cell (get-in @atoms/game-map enemy-pos)]
    (cond
      ;; Attacking a city
      (= :city (:type enemy-cell))
      (core/attempt-conquest-computer army-pos enemy-pos)

      ;; Attacking a unit
      (:contents enemy-cell)
      (let [attacker (get-in @atoms/game-map (conj army-pos :contents))
            defender (:contents enemy-cell)
            result (combat/resolve-combat attacker defender)]
        ;; Remove attacker from original position
        (swap! atoms/game-map update-in army-pos dissoc :contents)
        (if (= :attacker (:winner result))
          ;; Attacker won - move to enemy position
          (do
            (swap! atoms/game-map assoc-in (conj enemy-pos :contents) (:survivor result))
            (visibility/update-cell-visibility army-pos :computer)
            (visibility/update-cell-visibility enemy-pos :computer)
            enemy-pos)
          ;; Attacker lost
          (do
            (visibility/update-cell-visibility army-pos :computer)
            nil)))

      :else nil)))

(defn- find-land-objective
  "Find a land objective (unexplored, free city, or player city) on same continent."
  [pos]
  (let [cont-positions (continent/flood-fill-continent pos)]
    (or (continent/find-free-city-on-continent pos cont-positions)
        (continent/find-player-city-on-continent pos cont-positions)
        (continent/find-unexplored-on-continent pos cont-positions))))

(defn- move-toward-objective
  "Move army one step toward objective. Returns new position."
  [pos objective]
  (if-let [next-step (pathfinding/next-step pos objective :army)]
    (do
      (core/move-unit-to pos next-step)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-step :computer)
      next-step)
    ;; No path - try direct movement
    (let [passable (get-passable-neighbors pos)
          closest (core/move-toward pos objective passable)]
      (when closest
        (core/move-unit-to pos closest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(defn- find-and-board-transport
  "Look for a loading transport and move toward/board it."
  [pos]
  ;; Check for adjacent loading transport first
  (if-let [transport-pos (core/find-adjacent-loading-transport pos)]
    (do
      (core/board-transport pos transport-pos)
      (visibility/update-cell-visibility pos :computer)
      nil)  ; Army is now on transport, return nil
    ;; Move toward nearest loading transport
    (when-let [transport-pos (core/find-loading-transport)]
      (move-toward-objective pos transport-pos))))

(defn- explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area."
  [pos]
  (let [passable (get-passable-neighbors pos)
        ;; Prefer cells adjacent to unexplored
        frontier (filter core/adjacent-to-computer-unexplored? passable)]
    (when-let [target (or (first frontier) (first passable))]
      (core/move-unit-to pos target)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility target :computer)
      target)))

(defn process-army
  "Processes a computer army's turn using VMS Empire style logic.
   Priority: Attack adjacent > Land objective > Board transport > Explore"
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      ;; Priority 1: Attack adjacent enemy
      (if-let [enemy-pos (find-adjacent-enemy pos)]
        (attack-enemy pos enemy-pos)

        ;; Priority 2: Find land objective on same continent
        (if-let [objective (find-land-objective pos)]
          (move-toward-objective pos objective)

          ;; Priority 3: Board transport if no land objectives
          (if (core/find-loading-transport)
            (find-and-board-transport pos)

            ;; Priority 4: Explore randomly
            (explore-randomly pos)))))))
