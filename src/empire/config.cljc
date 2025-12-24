(ns empire.config
  (:require [clojure.set]))

(def smooth-count 10)

(def land-fraction 0.3)

(def number-of-cities 70)

(def min-city-distance 5)

;; Production items: keywords -> display strings
(def production-items->strings
  {:army "Army"
   :fighter "Fighter"
   :satellite "Satellite"
   :transport "Transport"
   :patrol-boat "Patrol Boat"
   :destroyer "Destroyer"
   :submarine "Submarine"
   :carrier "Carrier"
   :battleship "Battleship"})

;; Reverse lookup: display strings -> keywords
(def production-strings->items (clojure.set/map-invert production-items->strings))

;; Production rounds required for each item
(def production-rounds
  {:army 5
   :fighter 10
   :satellite 50
   :transport 30
   :patrol-boat 15
   :destroyer 20
   :submarine 20
   :carrier 30
   :battleship 40})

;; Production item display characters
(def production-item-chars
  {:army "A"
   :fighter "F"
   :satellite "Z"
   :transport "T"
   :patrol-boat "P"
   :destroyer "D"
   :submarine "S"
   :carrier "C"
   :battleship "B"})