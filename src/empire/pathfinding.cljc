(ns empire.pathfinding
  "A* pathfinding for computer AI units.
   Provides efficient pathfinding that respects terrain constraints."
  (:require [empire.atoms :as atoms]
            [empire.units.dispatcher :as dispatcher]))

(def path-cache
  "Cache for computed paths: {[start goal unit-type] path-vector}"
  (atom {}))

(defn clear-path-cache
  "Clears entire path cache. Called at start of each round."
  []
  (reset! path-cache {}))

(defn heuristic
  "Manhattan distance heuristic for A*."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn passable?
  "Returns true if unit-type can move through the cell."
  [unit-type cell]
  (and cell
       (not= (:type cell) :unexplored)
       (dispatcher/can-move-to? unit-type cell)))

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]         [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn get-passable-neighbors
  "Returns neighbors that the unit type can traverse."
  [pos unit-type game-map]
  (let [[x y] pos]
    (filter (fn [[nx ny]]
              (let [cell (get-in game-map [nx ny])]
                (passable? unit-type cell)))
            (map (fn [[dx dy]] [(+ x dx) (+ y dy)]) neighbor-offsets))))

(defn a-star
  "Finds shortest path from start to goal for unit-type.
   Returns vector of positions from start to goal (inclusive), or nil if no path.
   Uses standard A* with priority queue implemented via sorted-set."
  [start goal unit-type game-map]
  (if (= start goal)
    [start]
    (loop [open-set (sorted-set [(heuristic start goal) 0 start [start]])
           closed-set #{}
           best-g {start 0}]
      (when-let [[_f g current path] (first open-set)]
        (cond
          ;; Reached goal
          (= current goal)
          path

          ;; Already processed this node with better cost
          (closed-set current)
          (recur (disj open-set (first open-set)) closed-set best-g)

          :else
          (let [new-closed (conj closed-set current)
                neighbors (get-passable-neighbors current unit-type game-map)
                valid-neighbors (remove closed-set neighbors)
                new-g (inc g)
                ;; Only add neighbors if this path is better than any we've found
                {better-entries :better new-best-g :best-g}
                (reduce (fn [{:keys [better best-g]} n]
                          (let [existing-g (get best-g n Long/MAX_VALUE)]
                            (if (< new-g existing-g)
                              {:better (conj better n)
                               :best-g (assoc best-g n new-g)}
                              {:better better
                               :best-g best-g})))
                        {:better [] :best-g best-g}
                        valid-neighbors)
                new-entries (for [n better-entries
                                  :let [new-f (+ new-g (heuristic n goal))]]
                              [new-f new-g n (conj path n)])]
            (recur (into (disj open-set (first open-set)) new-entries)
                   new-closed
                   new-best-g)))))))

(defn- adjacent-to-unexplored?
  "Returns true if any neighbor of pos on the computer-map is nil (unexplored)."
  [pos computer-map]
  (let [[x y] pos
        height (count computer-map)
        width (count (first computer-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (nil? (get-in computer-map [nx ny])))))
          neighbor-offsets)))

(defn find-nearest-unexplored
  "BFS from start over passable cells to find nearest cell adjacent to unexplored.
   Returns the target position, or nil if none found.
   Skips the start position so the result is a meaningful movement target.
   unit-type determines which cells are passable (e.g. :transport for sea,
   :fighter for all terrain)."
  [start unit-type]
  (let [game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (adjacent-to-unexplored? current computer-map))
            current
            (let [neighbors (remove visited
                                    (get-passable-neighbors current unit-type game-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn- adjacent-to-target-continent-land?
  "Returns true if any neighbor of pos is land/city on target-continent."
  [pos target-continent game-map]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)
                  cell (get-in game-map [nx ny])]
              (and cell
                   (#{:land :city} (:type cell))
                   (contains? target-continent [nx ny]))))
          neighbor-offsets)))

(defn find-nearest-unload-position
  "BFS from start over sea cells to find nearest empty sea cell
   adjacent to land on target-continent. VMS-style global search."
  [start target-continent]
  (let [game-map @atoms/game-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)
              cell (get-in game-map current)]
          (if (and (not= current start)
                   (= :sea (:type cell))
                   (nil? (:contents cell))
                   (adjacent-to-target-continent-land? current target-continent game-map))
            current
            (let [neighbors (get-passable-neighbors current :transport game-map)
                  new-neighbors (remove visited neighbors)
                  new-visited (into visited new-neighbors)
                  new-queue (into rest-queue new-neighbors)]
              (recur new-queue new-visited))))))))

(defn next-step
  "Returns the next step toward goal, or nil if unreachable or already at goal.
   This is the main function computer.cljc will call.
   Uses caching to avoid recomputing paths."
  [start goal unit-type]
  (if (= start goal)
    nil
    (let [cache-key [start goal unit-type]
          cached (get @path-cache cache-key)]
      (if cached
        (second cached)  ; Return second element (first step after start)
        (let [game-map @atoms/game-map
              path (a-star start goal unit-type game-map)]
          (when path
            (swap! path-cache assoc cache-key path)
            (second path)))))))
