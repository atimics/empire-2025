(ns empire.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] @atoms/production]
    (when (map? prod)
      (let [cell (get-in @atoms/game-map coords)]
        (when-not (:contents cell)
          (let [item (:item prod)
                remaining (dec (:remaining-rounds prod))]
            (if (zero? remaining)
              (do
                (let [game-map @atoms/game-map
                      cell (get-in game-map coords)
                      owner (:city-status cell)
                      cell (assoc cell :contents {:type item :hits (config/item-hits item) :mode :awake :owner owner})]
                  (swap! atoms/game-map assoc-in coords cell)
                  (swap! atoms/production assoc coords (assoc prod :remaining-rounds (config/item-cost item)))))
              (swap! atoms/production assoc coords (assoc prod :remaining-rounds remaining)))))))))