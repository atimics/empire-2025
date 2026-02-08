(ns empire.tutorial.tips
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.containers.helpers :as uc]))

(defn toggle-enabled!
  []
  (swap! atoms/tips-enabled not)
  (atoms/set-turn-message (str "Tips: " (if @atoms/tips-enabled "on" "off")) 2000)
  @atoms/tips-enabled)

(defn dismiss!
  "Dismiss the current tip (by id)."
  [tip-id]
  (swap! atoms/tips-dismissed conj tip-id))

(defn- production-tip
  []
  {:id :city-production
   :title "City production"
   :text "Choose production: press a unit key. [X] none. [SPACE] skip."})

(defn- movement-tip
  [unit]
  (let [unit-type (:type unit)
        base "Move with QWEASDZXC."
        skip "[SPACE] skip."
        extra (cond
                (= unit-type :fighter) " Fighters burn fuel when skipping."
                (= unit-type :transport) " [U] wake cargo."
                (= unit-type :carrier) " [U] wake fighters."
                (uc/transport-with-armies? unit) " [U] wake cargo."
                (uc/carrier-with-fighters? unit) " [U] wake fighters."
                :else "")]
    {:id :unit-movement
     :title "Unit orders"
     :text (str base " " skip extra)}))

(defn- destination-tip
  [dest]
  {:id :destination
   :title "Destination"
   :text (str "Destination set: " (first dest) "," (second dest)
              ". Click a city to set marching orders, or press [.] again to change.")})

(defn- paused-tip
  []
  {:id :paused
   :title "Paused"
   :text "Game is paused. Press [P] to toggle pause; [SPACE] steps one round."})

(defn- attention-tip
  []
  (when-let [coords (first @atoms/cells-needing-attention)]
    (let [cell (get-in @atoms/game-map coords)
          active-unit (movement/get-active-unit cell)]
      (cond
        (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not active-unit))
        (production-tip)

        active-unit
        (movement-tip active-unit)

        :else nil))))

(defn current-tip
  "Returns the current contextual tip, or nil.

   Tips are meant to be lightweight and non-blocking. The selection is conservative:
   it only shows a tip when it can be clearly inferred from current game state."
  []
  (when (and @atoms/tips-enabled
             (not @atoms/load-menu-open)
             (not @atoms/tutorial-menu-open))
    (let [tip (cond
                (or @atoms/paused @atoms/pause-requested) (paused-tip)
                @atoms/waiting-for-input (attention-tip)
                @atoms/destination (destination-tip @atoms/destination)
                :else nil)]
      (when (and tip (not (contains? @atoms/tips-dismissed (:id tip))))
        tip))))

(defn current-tip-msg
  "Returns a JSON-friendly tip payload for the server snapshot, or nil."
  []
  (when-let [tip (current-tip)]
    {:id (name (:id tip))
     :title (:title tip)
     :text (:text tip)}))
