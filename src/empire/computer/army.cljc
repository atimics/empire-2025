(ns empire.computer.army
  "Computer army module - VMS Empire style army movement.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.continent :as continent]
            [empire.movement.pathfinding :as pathfinding]
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

(defn- get-empty-passable-neighbors
  "Returns passable land neighbors with no unit occupying them."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos))))

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
  "Find a land objective not already claimed by another army.
   VMS-style: distributes armies across different targets."
  [pos]
  (let [cont-positions (continent/flood-fill-continent pos)
        all-objectives (continent/find-all-objectives-on-continent cont-positions)]
    (when (seq all-objectives)
      (let [claimed @atoms/claimed-objectives
            unclaimed (remove claimed all-objectives)
            candidates (if (seq unclaimed) unclaimed all-objectives)
            nearest (apply min-key #(core/distance pos %) candidates)]
        (swap! atoms/claimed-objectives conj nearest)
        nearest))))

(defn- try-move
  "Attempt to move army from pos to target. Returns target if moved, nil if blocked."
  [pos target]
  (when (core/move-unit-to pos target)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility target :computer)
    target))

(defn- move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective."
  [pos objective]
  (let [preferred (pathfinding/next-step pos objective :army)]
    (or (when preferred (try-move pos preferred))
        ;; Preferred blocked or no path - try empty neighbors closest to objective
        (let [empty-neighbors (get-empty-passable-neighbors pos)]
          (when (seq empty-neighbors)
            (let [sorted (sort-by #(core/distance % objective) empty-neighbors)]
              (try-move pos (first sorted))))))))

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
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell."
  [pos]
  (let [empty (get-empty-passable-neighbors pos)
        frontier (filter core/adjacent-to-computer-unexplored? empty)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq empty) (rand-nth empty)))]
      (try-move pos target))))

(defn process-army
  "Processes a computer army's turn using VMS Empire style logic.
   Priority: Attack adjacent > Land objective > Board transport > Explore
   Returns nil after processing - armies only move once per round."
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
            (explore-randomly pos)))))
    nil))
