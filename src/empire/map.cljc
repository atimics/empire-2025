(ns empire.map)

(def game-map
  "A 100x100 2D atom containing vectors representing the game map."
  (atom (vec (repeat 100 (vec (repeat 100 0))))))