(ns empire.rendering-util
  (:require [empire.config :as config]))

(defn format-unit-status
  "Formats status string for a unit."
  [unit]
  (let [type-name (name (:type unit))
        hits (:hits unit)
        max-hits (config/item-hits (:type unit))
        fuel (when (= (:type unit) :fighter) (:fuel unit))
        cargo (case (:type unit)
                :transport (:army-count unit 0)
                :carrier (:fighter-count unit 0)
                nil)
        orders (cond
                 (:marching-orders unit) "march"
                 (:flight-path unit) "flight"
                 :else nil)]
    (str type-name
         " [" hits "/" max-hits "]"
         (when fuel (str " fuel:" fuel))
         (when cargo (str " cargo:" cargo))
         (when orders (str " " orders))
         " " (name (:mode unit)))))

(defn format-city-status
  "Formats status string for a city. Production is the production entry for this city, or nil."
  [cell production]
  (let [status (:city-status cell)
        fighters (:fighter-count cell 0)
        sleeping (:sleeping-fighters cell 0)]
    (str "city:" (name status)
         (when (and (= status :player) production)
           (str " producing:" (if (= production :none) "none" (name (:item production)))))
         (when (pos? fighters) (str " fighters:" fighters))
         (when (pos? sleeping) (str " sleeping:" sleeping))
         (when (:marching-orders cell) " march")
         (when (:flight-path cell) " flight"))))

(defn format-hover-status
  "Formats a status string for a cell. Production is the production entry for this cell, or nil."
  [cell production]
  (cond
    (:contents cell) (format-unit-status (:contents cell))
    (= (:type cell) :city) (format-city-status cell production)
    :else nil))

(defn group-cells-by-color
  "Groups map cells by their display color for batched rendering.
   Returns a map of [r g b] color to seq of {:col :row :cell} maps.
   blink-attention? and blink-completed? control flash states for attention cells and completed cities."
  [the-map attention-coords production blink-attention? blink-completed?]
  (let [cols (count the-map)
        rows (count (first the-map))
        attention-cell (first attention-coords)]
    (reduce
     (fn [acc [col row]]
       (let [cell (get-in the-map [col row])]
         (if (= :unexplored (:type cell))
           acc
           (let [color (config/color-of cell)
                 current [col row]
                 should-flash-black (= current attention-cell)
                 completed? (and (= (:type cell) :city)
                                 (not= :free (:city-status cell))
                                 (let [prod (production [col row])]
                                   (and (map? prod) (= (:remaining-rounds prod) 0))))
                 blink-white? (and completed? blink-completed?)
                 blink-black? (and should-flash-black blink-attention?)
                 final-color (cond blink-black? [0 0 0]
                                   blink-white? [255 255 255]
                                   :else color)]
             (update acc final-color conj {:col col :row row :cell cell})))))
     {}
     (for [col (range cols) row (range rows)] [col row]))))
