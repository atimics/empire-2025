(ns empire.computer.production
  "Computer production module - VMS Empire style ratio-based production."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as production]
            [empire.computer.continent :as continent]))

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

;; VMS Empire style production ratios
;; Format: {:army n :transport n :patrol-boat n :fighter n :destroyer n :submarine n :battleship n}
;; Values are relative weights (will be normalized to percentages)

(def ^:private ratio-1-2-cities
  "Production ratio for 1-2 cities: armies + transports + patrol boats"
  {:army 60 :transport 20 :patrol-boat 10 :fighter 0 :destroyer 0 :submarine 0 :battleship 0})

(def ^:private ratio-3-4-cities
  "Production ratio for 3-4 cities: add fighters"
  {:army 50 :transport 20 :patrol-boat 10 :fighter 10 :destroyer 0 :submarine 0 :battleship 0})

(def ^:private ratio-5-9-cities
  "Production ratio for 5-9 cities: add destroyers and subs"
  {:army 40 :transport 20 :patrol-boat 10 :fighter 10 :destroyer 10 :submarine 5 :battleship 5})

(def ^:private ratio-10-plus-cities
  "Production ratio for 10+ cities: full navy"
  {:army 30 :transport 20 :patrol-boat 5 :fighter 15 :destroyer 10 :submarine 10 :battleship 10})

(defn get-production-ratio
  "Returns the production ratio map based on number of cities owned."
  [city-count]
  (cond
    (<= city-count 2) ratio-1-2-cities
    (<= city-count 4) ratio-3-4-cities
    (<= city-count 9) ratio-5-9-cities
    :else ratio-10-plus-cities))

(defn- calculate-desired-count
  "Calculate desired count for a unit type based on ratio and total units."
  [unit-type ratio total-units]
  (let [weight (get ratio unit-type 0)
        total-weight (reduce + (vals ratio))]
    (if (zero? total-weight)
      0
      (Math/round (* (/ weight (double total-weight)) total-units)))))

(defn- unit-deficit
  "Calculate how many more of this unit type we need vs. have."
  [unit-type ratio current-counts total-target]
  (let [desired (calculate-desired-count unit-type ratio total-target)
        current (get current-counts unit-type 0)]
    (- desired current)))

(defn- can-city-produce?
  "Returns true if city can produce the given unit type."
  [city-pos unit-type]
  (let [coastal? (city-is-coastal? city-pos)]
    (case unit-type
      :army true
      :fighter true
      (:transport :patrol-boat :destroyer :submarine :battleship) coastal?
      false)))

(defn- continent-needs-army-producer?
  "Check if the continent containing city-pos needs more army producers."
  [city-pos]
  (let [cont-positions (continent/flood-fill-continent city-pos)
        counts (continent/scan-continent cont-positions)]
    ;; Need army producer if there's something to do on this continent
    (continent/has-land-objective? counts)))

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

(defn- decide-country-production
  "Country-aware production decision. Returns unit type or nil to fall back to ratio-based."
  [city-pos country-id coastal?]
  (cond
    ;; Priority 1: Need transport (< 2 per country, coastal only, need 6+ armies first)
    (and coastal?
         (< (count-country-transports country-id) 2)
         (>= (count-country-armies country-id) 6))
    :transport

    ;; Priority 2: Need army (< 10 per country, no other city producing armies)
    (and (< (count-country-armies country-id) 10)
         (not (country-city-producing-armies? city-pos country-id)))
    :army

    :else nil))

(defn- ratio-based-production
  "Choose unit with largest deficit based on production ratios."
  [city-pos coastal? country-id]
  (let [city-count (count-computer-cities)
        unit-counts (count-computer-units)
        total-units (reduce + (vals unit-counts))
        ratio (get-production-ratio city-count)
        target-total (max 10 (* 2 (inc total-units)))
        ;; Country cities exclude army/transport (managed by country system)
        excluded (if country-id #{:army :transport} #{})
        all-types (if coastal?
                    [:army :transport :patrol-boat :fighter :destroyer :submarine :battleship]
                    [:army :fighter])
        producible-types (remove excluded all-types)
        deficits (map (fn [t] [t (unit-deficit t ratio unit-counts target-total)])
                      producible-types)
        needed (filter #(pos? (second %)) deficits)]
    (when (seq needed)
      (first (apply max-key second needed)))))

(defn decide-production
  "Decide what a computer city should produce. Returns unit type keyword or nil."
  [city-pos]
  (let [city-cell (get-in @atoms/game-map city-pos)
        country-id (:country-id city-cell)
        coastal? (city-is-coastal? city-pos)]

    (or ;; Country-aware priorities (if city has a country-id)
        (when country-id
          (decide-country-production city-pos country-id coastal?))

        ;; Priority: Always maintain some army production on continents with objectives
        (when (and (not country-id)
                   (continent-needs-army-producer? city-pos)
                   (< (get (count-computer-units) :army 0)
                      (* (count-computer-cities) 3)))
          :army)

        ;; Ratio-based production
        (ratio-based-production city-pos coastal? country-id)

        ;; Fallback
        :army)))

(defn process-computer-city
  "Processes a computer city - sets production if not already set."
  [pos]
  (let [current-production (get @atoms/production pos)]
    (when (nil? current-production)
      (when-let [unit-type (decide-production pos)]
        (production/set-city-production pos unit-type)))))
