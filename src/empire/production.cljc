(ns empire.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:production-type item :remaining-rounds (config/production-rounds item)}))