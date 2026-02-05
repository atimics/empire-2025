(ns empire.computer.army
  "Computer army module - VMS Empire style army movement.
   Priority: Attack adjacent enemies > Find land objective > Board transport > Explore"
  (:require [empire.atoms :as atoms]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]))

;; Coast-walk helpers

(defn- adjacent-to-sea?
  "Returns true if position has at least one adjacent sea cell."
  [pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (core/get-neighbors pos)))

(defn- count-unexplored-neighbors
  "Counts unexplored cells adjacent to position on computer-map."
  [pos]
  (count (filter (fn [neighbor]
                   (nil? (get-in @atoms/computer-map neighbor)))
                 (core/get-neighbors pos))))

(defn- update-backtrack
  "Adds pos to visited vector, keeping at most 10 entries."
  [visited pos]
  (let [v (conj (or visited []) pos)]
    (if (> (count v) 10)
      (subvec v (- (count v) 10))
      v)))

(defn- terminate-coast-walk
  "Switches army from coast-walk to explore mode."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents)
         #(-> % (assoc :mode :explore :explore-steps 50)
              (dissoc :coast-direction :coast-start :coast-visited))))

;; Standard army helpers

(defn- sovereign-passable?
  "Returns true if a computer army with country-id can enter the cell.
   Foreign land (different non-nil country-id) is blocked. Cities are always passable."
  [country-id cell]
  (and cell
       (#{:land :city} (:type cell))
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (= country-id (:country-id cell)))))

(defn- get-passable-neighbors
  "Returns passable land neighbors for an army, respecting sovereignty."
  [pos country-id]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (sovereign-passable? country-id (get-in game-map neighbor)))
            (core/get-neighbors pos))))

(defn- get-empty-passable-neighbors
  "Returns passable land neighbors with no unit occupying them."
  [pos country-id]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (nil? (:contents cell))))
            (get-passable-neighbors pos country-id))))

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
            (core/stamp-territory enemy-pos (:survivor result))
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
  (let [cont-positions (land-objectives/flood-fill-continent pos)
        all-objectives (land-objectives/find-all-objectives-on-continent cont-positions)]
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

(defn- sovereignty-passability-fn
  "Returns a passability function for A* that respects sovereignty for the given country-id."
  [country-id]
  (fn [cell] (sovereign-passable? country-id cell)))

(defn- move-toward-objective
  "Move army one step toward objective. If preferred step is occupied,
   try other empty neighbors sorted by distance to objective."
  [pos objective country-id]
  (let [pass-fn (when country-id (sovereignty-passability-fn country-id))
        preferred (pathfinding/next-step pos objective :army pass-fn country-id)]
    (or (when preferred (try-move pos preferred))
        ;; Preferred blocked or no path - try empty neighbors closest to objective
        (let [empty-neighbors (get-empty-passable-neighbors pos country-id)]
          (when (seq empty-neighbors)
            (let [sorted (sort-by #(core/distance % objective) empty-neighbors)]
              (try-move pos (first sorted))))))))

(defn- find-and-board-transport
  "Look for a loading transport and move toward/board it."
  [pos country-id]
  ;; Check for adjacent loading transport first
  (if-let [transport-pos (core/find-adjacent-loading-transport pos)]
    (do
      (core/board-transport pos transport-pos)
      (visibility/update-cell-visibility pos :computer)
      nil)  ; Army is now on transport, return nil
    ;; Move toward nearest loading transport
    (when-let [transport-pos (core/find-loading-transport)]
      (move-toward-objective pos transport-pos country-id))))

(defn- explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell."
  [pos country-id]
  (let [empty (get-empty-passable-neighbors pos country-id)
        frontier (filter core/adjacent-to-computer-unexplored? empty)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq empty) (rand-nth empty)))]
      (try-move pos target))))

(defn- coast-walk-candidates
  "Returns empty land/city neighbors that are adjacent to sea."
  [pos country-id]
  (filter adjacent-to-sea? (get-empty-passable-neighbors pos country-id)))

(defn- process-coast-walk
  "Handles coast-walk movement. Returns new position or nil."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        coast-start (:coast-start unit)
        visited (set (:coast-visited unit))
        candidates (coast-walk-candidates pos country-id)]
    (if (empty? candidates)
      (do (terminate-coast-walk pos) nil)
      (let [not-visited (remove visited candidates)
            pool (if (seq not-visited) not-visited candidates)
            scored (map (fn [c] [c (count-unexplored-neighbors c)]) pool)
            best-score (apply max (map second scored))
            best (map first (filter #(= best-score (second %)) scored))
            target (if (= 1 (count best)) (first best) (rand-nth (vec best)))]
        (when (try-move pos target)
          (swap! atoms/game-map update-in (conj target :contents)
                 #(assoc % :coast-visited (update-backtrack (:coast-visited %) target)))
          (if (= target coast-start)
            (do (terminate-coast-walk target) target)
            target))))))

(defn- find-and-execute-land-action [pos country-id]
  (if-let [objective (find-land-objective pos)]
    (move-toward-objective pos objective country-id)
    (if (core/find-loading-transport)
      (find-and-board-transport pos country-id)
      (explore-randomly pos country-id))))

(defn process-army
  "Processes a computer army's turn using VMS Empire style logic.
   Priority: Attack adjacent > Coast-walk (if mode) > Land objective > Board transport > Explore
   Returns nil after processing - armies only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (let [enemy-pos (find-adjacent-enemy pos)
            country-id (:country-id unit)]
        (cond
          enemy-pos (attack-enemy pos enemy-pos)
          (= :coast-walk (:mode unit)) (process-coast-walk pos country-id)
          :else (find-and-execute-land-action pos country-id))))
    nil))
