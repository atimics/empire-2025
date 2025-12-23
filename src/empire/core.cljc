(ns empire.core
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [empire.init :as init]
            [empire.config :as config]))

(defn setup
  "Initial setup for the game state."
  []
  (init/make-initial-map config/map-size config/smooth-count config/land-fraction))

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
        the-map (:map state)
        sea-level (:sea-level state)
        height (count the-map)
        width (count (first the-map))
        cell-w (/ screen-w width)
        cell-h (/ screen-h height)]
    (doseq [i (range height)
            j (range width)]
      (let [[terrain-type _contents] (get-in the-map [i j])
            color (case terrain-type
                    :land [34 139 34]   ; forest green for land
                    :sea [25 25 112])] ; midnight blue for water
        (apply q/fill color)
        (q/rect (* j cell-w) (* i cell-h) cell-w cell-h)))
    (q/fill 255)
    (q/text (str "Empire: Global Conquest - Sea Level: " sea-level) 10 20)))

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
