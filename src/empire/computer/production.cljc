(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as production]
            [empire.computer.continent :as continent]
            [empire.computer.ship :as ship]))

;; Preserved utilities

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn city-is-coastal?
  "Returns true if city has adjacent sea cells."
  [city-pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors city-pos)))

(defn count-computer-units
  "Counts computer units by type. Returns map of type to count."
  []
  (let [units (for [i (range (count @atoms/game-map))
                    j (range (count (first @atoms/game-map)))
                    :let [cell (get-in @atoms/game-map [i j])
                          unit (:contents cell)]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

(defn count-computer-cities
  "Counts the number of computer-owned cities."
  []
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
           [i j])))


(defn- count-country-transports
  "Counts live transports belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= country-id (:country-id unit)))]
           true)))

(defn count-country-armies
  "Counts live armies belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :army (:type unit))
                          (= country-id (:country-id unit)))]
           true)))

(defn- count-country-patrol-boats
  "Counts live computer patrol boats belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :patrol-boat (:type unit))
                          (= country-id (:patrol-country-id unit)))]
           true)))

(defn- country-city-producing-armies?
  "Returns true if any other computer city in this country is already producing armies."
  [city-pos country-id]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= :army (:item prod))
               (not= coords city-pos)
               (let [cell (get-in @atoms/game-map coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        @atoms/production))

(defn- country-has-unadopted-transport?
  "Returns true if the country has a transport without an escort destroyer."
  [country-id]
  (some (fn [[i row]]
          (some (fn [[j cell]]
                  (let [unit (:contents cell)]
                    (and unit
                         (= :transport (:type unit))
                         (= :computer (:owner unit))
                         (= country-id (:country-id unit))
                         (nil? (:escort-destroyer-id unit)))))
                (map-indexed vector row)))
        (map-indexed vector @atoms/game-map)))

(defn- decide-country-production
  "Per-country production priorities. Returns unit type or nil. CC=4."
  [city-pos country-id coastal? unit-counts]
  (cond
    ;; 1. Transport: < 2 per country, coastal, need 6+ armies first
    (and coastal?
         (< (count-country-transports country-id) 2)
         (>= (count-country-armies country-id) 6))
    :transport

    ;; 2. Army: < 10 per country, no other city producing armies
    (and (< (count-country-armies country-id) 10)
         (not (country-city-producing-armies? city-pos country-id)))
    :army

    ;; 3. Patrol boat: 1 per country, coastal
    (and coastal?
         (zero? (count-country-patrol-boats country-id)))
    :patrol-boat

    ;; 4. Destroyer: global cap, country has unadopted transport
    (and coastal?
         (< (get unit-counts :destroyer 0) (get unit-counts :transport 0))
         (country-has-unadopted-transport? country-id))
    :destroyer))

(defn- count-carrier-producers
  "Counts computer cities currently producing carriers."
  []
  (count (filter (fn [[_coords prod]]
                   (and (map? prod)
                        (= :carrier (:item prod))))
                 @atoms/production)))

(defn- decide-global-production
  "Global production priorities. Returns unit type. CC=5."
  [coastal? unit-counts]
  (cond
    ;; 5. Carrier: >10 cities, <2 producing, valid position
    (and coastal?
         (> (count-computer-cities) 10)
         (< (count-carrier-producers) 2)
         (ship/find-carrier-position))
    :carrier

    ;; 6. Battleship: BB < carriers
    (and coastal?
         (< (get unit-counts :battleship 0)
            (get unit-counts :carrier 0)))
    :battleship

    ;; 7. Submarine: Sub < 2*carriers
    (and coastal?
         (< (get unit-counts :submarine 0)
            (* 2 (get unit-counts :carrier 0))))
    :submarine

    ;; 8. Satellite: >15 cities, max 1
    (and (> (count-computer-cities) 15)
         (zero? (get unit-counts :satellite 0)))
    :satellite

    ;; 9. Fighter fallback
    :else :fighter))

(defn decide-production
  "Decide what a computer city should produce. Returns unit type keyword.
   Per-country priorities first, then non-country army fallback, then global."
  [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
        country-id (:country-id city-cell)
        coastal? (city-is-coastal? city-pos)
        unit-counts (count-computer-units)]
    (or ;; Per-country priorities (if city has a country-id)
        (when country-id
          (decide-country-production city-pos country-id coastal? unit-counts))

        ;; Non-country cities: army if continent has objectives (free/player cities)
        (when (and (not country-id)
                   (continent/has-land-objective?
                     (continent/scan-continent
                       (continent/flood-fill-continent city-pos))))
          :army)

        ;; Global priorities (any city with country-id whose per-country needs are met)
        (decide-global-production coastal? unit-counts))))

(defn process-computer-city
  "Processes a computer city - sets production if not already set."
  [pos]
  (let [current-production (get @atoms/production pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (production/set-city-production pos unit-type)))))
