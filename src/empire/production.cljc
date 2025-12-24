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
    (when (map? prod)
      (let [new-remaining (max 0 (dec (:remaining-rounds prod)))]
        (swap! atoms/production assoc coords (assoc prod :remaining-rounds new-remaining))))))