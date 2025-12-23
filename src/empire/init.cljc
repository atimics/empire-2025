(ns empire.init
  (:require [empire.map :as map]))


(defn smooth-map
  "Takes a map and returns a smoothed version where each cell is the rounded average
   of itself and its 8 surrounding cells. Edge cells use their own value for missing neighbors."
  [input-map]
  (let [height (count input-map)
        width (count (first input-map))]
    (vec (for [i (range height)]
           (vec (for [j (range width)]
                  (let [neighbors (for [di [-1 0 1]
                                        dj [-1 0 1]]
                                    (let [c (get-in input-map [i j])
                                          ni (+ i di)
                                          nj (+ j dj)]
                                      (get-in input-map [ni nj] c)))]
                    (quot (apply + neighbors) 9))))))))

(defn make-map
  "Creates and initializes the game map with random integers, then applies smoothing."
  [width height smooth-count]
  (let [random-map (vec (for [_ (range height)]
                          (vec (for [_ (range width)]
                                 (rand-int 1001)))))
        smoothed-map (loop [m random-map cnt smooth-count]
                       (if (zero? cnt)
                         m
                         (recur (smooth-map m) (dec cnt))))]
    (reset! map/game-map smoothed-map)
    smoothed-map))

(defn finalize-map
  "Converts a height map to a terrain map with land/sea types."
  [the-map sea-level]
  (vec (for [row the-map]
         (vec (for [height row]
                (let [terrain-type (if (> height sea-level) :land :sea)]
                  [terrain-type :empty]))))))

(defn find-sea-level
  "Finds the sea-level threshold for a given land fraction."
  [the-map land-fraction]
  (let [flattened (flatten the-map)
        sorted-heights (sort flattened)
        total (count flattened)
        target-land (Math/round (* land-fraction total))
        sea-level-idx (max 0 (min (dec total) (- total target-land)))
        sea-level (nth sorted-heights sea-level-idx)]
    sea-level))

(defn make-initial-map
  "Creates and initializes the complete game map with terrain."
  [map-size smooth-count land-fraction]
  (let [[width height] map-size
        the-map (make-map width height smooth-count)
        sea-level (find-sea-level the-map land-fraction)
        finalized-map (finalize-map the-map sea-level)]
    {:map finalized-map :sea-level sea-level}))
