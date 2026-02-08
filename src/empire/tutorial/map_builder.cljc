(ns empire.tutorial.map-builder
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :as test-utils]
            [empire.units.dispatcher :as dispatcher]))

(defn- reveal-around!
  "Reveals cells within radius of [col row] on the player map."
  [game-map col row radius cols rows]
  (doseq [dc (range (- radius) (inc radius))
          dr (range (- radius) (inc radius))]
    (let [c (+ col dc)
          r (+ row dr)]
      (when (and (>= c 0) (< c cols) (>= r 0) (< r rows))
        (swap! atoms/player-map assoc-in [c r] (get-in game-map [c r]))))))

(defn- update-tutorial-visibility!
  "Reveals cells around all player units and cities."
  []
  (let [game-map @atoms/game-map
        [cols rows] @atoms/map-size]
    (doseq [col (range cols)
            row (range rows)]
      (let [cell (get-in game-map [col row])]
        (when (or (and (:contents cell) (= :player (:owner (:contents cell))))
                  (and (= :city (:type cell)) (= :player (:city-status cell))))
          (let [radius (if (:contents cell)
                         (dispatcher/visibility-radius (:type (:contents cell)))
                         2)]
            (reveal-around! game-map col row radius cols rows)))))))

(defn build-tutorial-map!
  "Sets up the game from ASCII map strings for a tutorial scenario.
   Each string is one row; characters map via test-utils/char->cell."
  [map-strings]
  (let [game-map (test-utils/build-test-map map-strings)
        cols (count game-map)
        rows (count (first game-map))]
    ;; Set map dimensions
    (reset! atoms/map-size [cols rows])
    (reset! atoms/map-size-constants (config/compute-size-constants cols rows))
    ;; Set game map
    (reset! atoms/game-map game-map)
    ;; Initialize fog-of-war maps as unexplored
    (reset! atoms/player-map (vec (repeat cols (vec (repeat rows nil)))))
    (reset! atoms/computer-map (vec (repeat cols (vec (repeat rows nil)))))
    ;; Note: caller must invoke (core/calculate-screen-dimensions) after this
    ;; Reset game state
    (reset! atoms/round-number 1)
    (reset! atoms/paused false)
    (reset! atoms/waiting-for-input false)
    (reset! atoms/cells-needing-attention [])
    (reset! atoms/player-items [])
    (reset! atoms/production {})
    (reset! atoms/destination nil)
    (reset! atoms/game-over-check-enabled false)
    ;; Reveal cells around player units and cities
    (update-tutorial-visibility!)))
