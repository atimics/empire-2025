(ns empire.core
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [empire.init :as init]
            [empire.config :as config]))

(defn setup
  "Initial setup for the game state."
  []
  (init/make-initial-map config/map-size config/smooth-count config/land-fraction config/number-of-cities config/min-city-distance))

(defn update-state
  "Update the game state."
  [state]
  state)

(defn draw-state
  "Draw the current game state."
  [state]
  (q/background 0)
  (let [screen-w (q/width)
        screen-h (q/height)
        the-map state
        height (count the-map)
        width (count (first the-map))
        cell-w (/ screen-w width)
        cell-h (/ screen-h height)]
    (doseq [i (range height)
            j (range width)]
      (let [[terrain-type contents] (get-in the-map [i j])
            color (cond
                    (= contents :free-city) [255 255 255]   ; white for free cities
                    (= terrain-type :land) [34 139 34]   ; forest green for land
                    (= terrain-type :sea) [25 25 112])] ; midnight blue for water
        (apply q/fill color)
        (q/rect (* j cell-w) (* i cell-h) cell-w cell-h)))
    (q/fill 255)
    (q/text "Empire: Global Conquest" 10 20)))

(declare empire)
(defn -main [& _args]
  (println "empire has begun.")
  (q/defsketch empire
    :title "Empire: Global Conquest"
    :size :fullscreen
    :setup setup
    :update update-state
    :draw draw-state
    :features [:keep-on-top]
    :middleware [m/fun-mode]
    :host "empire"))
