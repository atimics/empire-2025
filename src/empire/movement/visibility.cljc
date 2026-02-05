(ns empire.movement.visibility
  (:require [empire.atoms :as atoms]
            [empire.units.dispatcher :as dispatcher]))

(defn- is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn- is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn- reveal-surrounding-cells!
  "Reveals cells within radius around cell [i,j] in the transient result map.
   Clamps to map boundaries."
  [result game-map i j height width radius]
  (let [coords (for [row (range (max 0 (- i radius)) (min height (+ i radius 1)))
                     col (range (max 0 (- j radius)) (min width (+ j radius 1)))]
                 [row col])]
    (reduce (fn [r [row col]]
              (let [cell ((game-map row) col)]
                (assoc! r row (assoc! (r row) col cell))))
            result
            coords)))

(defn- cell-visibility-radius
  "Returns the visibility radius for a cell based on its contents."
  [cell]
  (if-let [unit-type (:type (:contents cell))]
    (dispatcher/visibility-radius unit-type)
    1))

(defn- process-map-cells
  "Iterates over all cells, revealing surroundings for owned cells.
   Returns the updated transient result."
  [result game-map ownership-predicate height width]
  (let [coords (for [i (range height)
                     j (range width)]
                 [i j])]
    (reduce (fn [res [i j]]
              (let [cell ((game-map i) j)]
                (if (ownership-predicate cell)
                  (reveal-surrounding-cells! res game-map i j height width
                                             (cell-visibility-radius cell))
                  res)))
            result
            coords)))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-atom owner]
  (when-let [visible-map @visible-map-atom]
    (let [game-map @atoms/game-map
          ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (reset! visible-map-atom (mapv persistent! (persistent! updated))))))

(defn update-cell-visibility
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2).
   When unit is a computer army with country-id, stamps newly-revealed land cells."
  ([pos owner] (update-cell-visibility pos owner nil))
  ([pos owner unit]
   (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
         [x y] pos
         cell (get-in @atoms/game-map pos)
         is-satellite? (= :satellite (:type (:contents cell)))
         radius (if is-satellite? 2 1)
         should-stamp? (and unit
                            (= :army (:type unit))
                            (= :computer (:owner unit))
                            (:country-id unit))]
     (when @visible-map-atom
       (let [height (count @atoms/game-map)
             width (count (first @atoms/game-map))
             visible-map @visible-map-atom]
         (doseq [di (range (- radius) (inc radius))
                 dj (range (- radius) (inc radius))]
           (let [ni (+ x di)
                 nj (+ y dj)]
             (when (and (>= ni 0) (< ni height)
                        (>= nj 0) (< nj width))
               (let [game-cell (get-in @atoms/game-map [ni nj])
                     was-unexplored? (let [vis-cell (get-in visible-map [ni nj])]
                                       (or (nil? vis-cell)
                                           (= :unexplored (:type vis-cell))))]
                 (swap! visible-map-atom assoc-in [ni nj] game-cell)
                 (when (and should-stamp? was-unexplored? (= :land (:type game-cell)))
                   (swap! atoms/game-map assoc-in [ni nj :country-id]
                          (:country-id unit))))))))))))
