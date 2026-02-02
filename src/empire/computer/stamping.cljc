(ns empire.computer.stamping
  (:require [empire.atoms :as atoms]))

(defn- apply-computer-satellite-direction
  "Assigns a random direction to computer satellites."
  [unit]
  (if (and (= :satellite (:type unit)) (= :computer (:owner unit)))
    (assoc unit :direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))

(defn- apply-computer-transport-id
  "Assigns a unique transport-id to computer transports."
  [unit]
  (if (and (= :transport (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-transport-id]
      (swap! atoms/next-transport-id inc)
      (assoc unit :transport-id id))
    unit))

(defn- apply-country-id
  "Assigns city's country-id to computer armies, transports, and fighters."
  [unit cell]
  (if (and (#{:army :transport :fighter} (:type unit)) (:country-id cell))
    (assoc unit :country-id (:country-id cell))
    unit))

(defn- apply-patrol-fields
  "Stamps patrol boat fields on computer patrol boats spawned from a country city."
  [unit cell]
  (if (and (= :patrol-boat (:type unit))
           (= :computer (:city-status cell))
           (:country-id cell))
    (assoc unit :patrol-country-id (:country-id cell)
                :patrol-direction :clockwise
                :patrol-mode :homing)
    unit))

(defn- apply-carrier-fields
  "Stamps carrier fields on computer carriers: mode, id, group slots."
  [unit]
  (if (and (= :carrier (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-carrier-id]
      (swap! atoms/next-carrier-id inc)
      (assoc unit :carrier-mode :positioning
                  :carrier-id id
                  :group-battleship-id nil
                  :group-submarine-ids []))
    unit))

(defn- apply-escort-fields
  "Stamps escort fields on computer battleships and submarines."
  [unit]
  (if (and (#{:battleship :submarine} (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-escort-id]
      (swap! atoms/next-escort-id inc)
      (assoc unit :escort-id id :escort-mode :seeking))
    unit))

(defn- apply-destroyer-fields
  "Stamps destroyer-id and escort-mode on computer destroyers."
  [unit]
  (if (and (= :destroyer (:type unit)) (= :computer (:owner unit)))
    (let [id @atoms/next-destroyer-id]
      (swap! atoms/next-destroyer-id inc)
      (assoc unit :destroyer-id id :escort-mode :seeking))
    unit))

(defn stamp-computer-fields
  "Applies all computer-specific fields to a unit.
   Called by stamp-unit-fields in player/production after shared attributes."
  [unit cell]
  (-> unit
      (apply-computer-satellite-direction)
      (apply-computer-transport-id)
      (apply-destroyer-fields)
      (apply-carrier-fields)
      (apply-escort-fields)
      (apply-country-id cell)
      (apply-patrol-fields cell)))

(defn apply-coast-walk-fields
  "Stamps coast-walk mode on first 2 computer armies per country.
   First army gets clockwise, second gets counter-clockwise."
  [unit item cell coords]
  (if (and (= item :army)
           (= (:city-status cell) :computer)
           (:country-id cell))
    (let [country-id (:country-id cell)
          produced (get @atoms/coast-walkers-produced country-id 0)]
      (cond
        (zero? produced)
        (do (swap! atoms/coast-walkers-produced update country-id (fnil inc 0))
            (assoc unit :mode :coast-walk
                        :coast-direction :clockwise
                        :coast-start coords
                        :coast-visited [coords]))

        (= produced 1)
        (do (swap! atoms/coast-walkers-produced update country-id inc)
            (assoc unit :mode :coast-walk
                        :coast-direction :counter-clockwise
                        :coast-start coords
                        :coast-visited [coords]))

        :else unit))
    unit))
