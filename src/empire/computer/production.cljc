(ns empire.computer.production
  "Computer production module - priority-based production."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as production]
            [empire.computer.ship :as ship]))

;; Preserved utilities

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn city-is-coastal?
  "Returns true if city has adjacent sea cells and is not landlocked."
  [city-pos]
  (and (not (:landlocked (get-in @atoms/game-map city-pos)))
       (some (fn [neighbor]
               (= :sea (:type (get-in @atoms/game-map neighbor))))
             (get-neighbors city-pos))))

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
  "Counts live armies belonging to the given country-id,
   including armies aboard transports of the same country."
  [country-id]
  (reduce
    (fn [total [_i row]]
      (reduce
        (fn [total [_j cell]]
          (let [unit (:contents cell)]
            (cond
              (and unit
                   (= :computer (:owner unit))
                   (= :army (:type unit))
                   (= country-id (:country-id unit)))
              (inc total)

              (and unit
                   (= :computer (:owner unit))
                   (= :transport (:type unit))
                   (= country-id (:country-id unit)))
              (+ total (get unit :army-count 0))

              :else total)))
        total
        (map-indexed vector row)))
    0
    (map-indexed vector @atoms/game-map)))

(defn- count-country-fighters
  "Counts live fighters belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :fighter (:type unit))
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

(defn country-city-producing-armies?
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
  "Per-country production priorities. Returns unit type or nil. CC=5."
  [city-pos country-id coastal? unit-counts]
  (cond
    ;; 1. Transport: < max per country, coastal, need enough armies first
    (and coastal?
         (< (count-country-transports country-id) config/max-transports-per-country)
         (>= (count-country-armies country-id) config/armies-before-transport))
    :transport

    ;; 2. Army: < max per country, no other city producing armies
    (and (< (count-country-armies country-id) config/max-armies-per-country)
         (not (country-city-producing-armies? city-pos country-id)))
    :army

    ;; 3. Patrol boat: < max per country, coastal
    (and coastal?
         (< (count-country-patrol-boats country-id) config/max-patrol-boats-per-country))
    :patrol-boat

    ;; 4. Destroyer: global cap, country has unadopted transport
    (and coastal?
         (< (get unit-counts :destroyer 0) (get unit-counts :transport 0))
         (country-has-unadopted-transport? country-id))
    :destroyer

    ;; 5. Fighter: < max per country
    (< (count-country-fighters country-id) config/max-fighters-per-country)
    :fighter))

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
    ;; 5. Carrier: enough cities, under fleet cap, under producer cap, valid position
    (and coastal?
         (> (count-computer-cities) config/carrier-city-threshold)
         (< (get unit-counts :carrier 0) config/max-live-carriers)
         (< (count-carrier-producers) config/max-carrier-producers)
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

    ;; 8. Satellite: enough cities, under cap
    (and (> (count-computer-cities) config/satellite-city-threshold)
         (< (get unit-counts :satellite 0) config/max-satellites))
    :satellite

    ;; 9. No production needed — city stays idle
    :else nil))

(defn decide-production
  "Decide what a computer city should produce. Returns unit type keyword.
   Per-country priorities first, then global."
  [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
        country-id (:country-id city-cell)
        coastal? (city-is-coastal? city-pos)
        unit-counts (count-computer-units)]
    (or (when country-id
          (decide-country-production city-pos country-id coastal? unit-counts))
        (when country-id
          (decide-global-production coastal? unit-counts)))))

(defn process-computer-city
  "Processes a computer city - sets production if not already set."
  [pos]
  (let [current-production (get @atoms/production pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (production/set-city-production pos unit-type)))))
