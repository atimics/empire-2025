(ns empire.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:production-type item :remaining-rounds (config/production-rounds item)}))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] @atoms/production]
    (when (and (map? prod) (> (:remaining-rounds prod) 0))
      (swap! atoms/production assoc coords (update prod :remaining-rounds dec)))))