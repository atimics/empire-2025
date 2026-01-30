(ns empire.player.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn- create-base-unit
  "Creates a base unit with type, hits, mode, and owner."
  [item owner]
  {:type item :hits (config/item-hits item) :mode :awake :owner owner})

(defn- apply-unit-type-attributes
  "Adds type-specific attributes (fuel, turns, transport mission, transport id)."
  [unit item owner]
  (cond-> unit
    (= item :fighter)
    (assoc :fuel config/fighter-fuel)

    (= item :satellite)
    (assoc :turns-remaining config/satellite-turns)

    (and (= item :satellite) (= owner :computer))
    (assoc :direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))

    (= item :transport)
    (assoc :transport-mission :idle)

    (and (= item :transport) (= owner :computer))
    (assoc :transport-id (let [id @atoms/next-transport-id]
                           (swap! atoms/next-transport-id inc)
                           id))))

(defn- apply-movement-orders
  "Applies marching orders or flight path to unit."
  [unit item marching-orders flight-path]
  (cond
    (and (= item :army) (= marching-orders :lookaround))
    (assoc unit :mode :explore :explore-steps 50)

    (and (= item :army) marching-orders)
    (assoc unit :mode :moving :target marching-orders)

    (and (= item :fighter) flight-path)
    (assoc unit :mode :moving :target flight-path)

    :else unit))

(defn- apply-country-id
  "Assigns city's country-id to armies and transports."
  [unit item cell]
  (if (and (#{:army :transport} item) (:country-id cell))
    (assoc unit :country-id (:country-id cell))
    unit))

(defn- apply-patrol-fields
  "Stamps patrol boat fields on computer patrol boats spawned from a country city."
  [unit item cell]
  (if (and (= item :patrol-boat)
           (= (:city-status cell) :computer)
           (:country-id cell))
    (assoc unit :patrol-country-id (:country-id cell)
                :patrol-direction :clockwise
                :patrol-mode :homing)
    unit))

(defn- spawn-unit
  "Creates and places a unit at the given city coordinates."
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (create-base-unit item owner)
                 (apply-unit-type-attributes item owner)
                 (apply-country-id item cell)
                 (apply-patrol-fields item cell)
                 (apply-movement-orders item marching-orders flight-path))]
    (swap! atoms/game-map assoc-in (conj coords :contents) unit)
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in @atoms/game-map coords)
        owner (spawn-unit coords cell item)]
    (if (= owner :computer)
      (swap! atoms/production dissoc coords)
      (swap! atoms/production assoc coords
             (assoc prod :remaining-rounds (config/item-cost item))))))

(defn- update-city-production
  "Updates production for a single city."
  [coords prod]
  (let [cell (get-in @atoms/game-map coords)]
    (when-not (:contents cell)
      (let [item (:item prod)
            remaining (dec (:remaining-rounds prod))]
        (if (zero? remaining)
          (handle-production-complete coords prod item)
          (swap! atoms/production assoc coords
                 (assoc prod :remaining-rounds remaining)))))))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] @atoms/production]
    (when (map? prod)
      (update-city-production coords prod))))
