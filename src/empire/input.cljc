(ns empire.input
  (:require [empire.atoms :as atoms]
            [empire.attention :as attention]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.map-utils :as map-utils]
            [empire.menus :as menus]
            [empire.movement :as movement]
            [empire.production :as production]
            [empire.unit-container :as uc]))

(defn handle-city-click
  "Handles clicking on a city cell."
  [cell-x cell-y]
  (let [cell (get-in @atoms/game-map [cell-x cell-y])
        header (if (= (:city-status cell) :player) :production :city-info)
        coastal-city? (map-utils/on-coast? cell-x cell-y)
        basic-items (if (= header :production)
                      [:army :fighter :satellite]
                      ["City Status" (name (:city-status cell))])
        coastal-items (if (= header :production)
                        [:transport :patrol-boat :destroyer :submarine :carrier :battleship]
                        [])
        all-items (cond-> basic-items coastal-city? (into coastal-items))
        items all-items]
    (menus/show-menu cell-x cell-y header items)))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [cell-x cell-y clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)]
    (if (= clicked-coords attn-coords)
      ;; Clicked on the unit: show menu
      (let [header :unit
            items [:explore :sentry]]
        (menus/show-menu cell-x cell-y header items))
      ;; Clicked elsewhere
      (let [attn-cell (get-in @atoms/game-map attn-coords)
            active-unit (movement/get-active-unit attn-cell)
            unit-type (:type active-unit)
            is-airport-fighter? (movement/is-fighter-from-airport? attn-cell active-unit)
            is-army-aboard? (movement/is-army-aboard-transport? attn-cell active-unit)
            target-cell (get-in @atoms/game-map clicked-coords)
            [ax ay] attn-coords
            [cx cy] clicked-coords
            adjacent? (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))]
        (cond
          is-airport-fighter?
          (let [fighter-pos (movement/launch-fighter-from-airport attn-coords clicked-coords)]
            (reset! atoms/waiting-for-input false)
            (reset! atoms/message "")
            (reset! atoms/cells-needing-attention [])
            (swap! atoms/player-items #(cons fighter-pos (rest %))))

          (and is-army-aboard? adjacent? (= (:type target-cell) :land) (not (:contents target-cell)))
          (do
            (movement/disembark-army-from-transport attn-coords clicked-coords)
            (game-loop/item-processed))

          is-army-aboard?
          nil ;; Awake army aboard - ignore invalid disembark targets

          (and (= :army unit-type) adjacent? (combat/hostile-city? clicked-coords))
          (combat/attempt-conquest attn-coords clicked-coords)

          (and (= :fighter unit-type) adjacent? (combat/hostile-city? clicked-coords))
          (combat/attempt-fighter-overfly attn-coords clicked-coords)

          :else
          (movement/set-unit-movement attn-coords clicked-coords))
        (game-loop/item-processed)))))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [cell (get-in @atoms/game-map [cell-x cell-y])
        clicked-coords [cell-x cell-y]
        attention-coords @atoms/cells-needing-attention]
    (cond
      (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click cell-x cell-y clicked-coords attention-coords)

      (attention/is-city-needing-attention? cell clicked-coords attention-coords)
      (handle-city-click cell-x cell-y)

      (= (:type cell) :city)
      (handle-city-click cell-x cell-y)

      ;; Otherwise, do nothing
      :else nil)))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (reset! atoms/line3-message "")
  (if-not (map-utils/on-map? x y)
    (reset! atoms/line3-message (:not-on-map config/messages))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)
          cell (get-in @atoms/game-map [cell-x cell-y])]
      (case button
        :right
        (cond
          (and (= (:type cell) :city)
               (= (:city-status cell) :player))
          (handle-city-click cell-x cell-y)

          (= (:owner (:contents cell)) :player)
          (movement/set-unit-mode [cell-x cell-y] :awake)

          :else
          (reset! atoms/line2-message (str cell)))

        :left
        (do
          (menus/dismiss-existing-menu x y)
          (let [clicked-item (menus/handle-menu-click x y)]
            (when clicked-item
              (when (= :production (:header @atoms/menu-state))
                (production/set-city-production (:coords @atoms/menu-state) clicked-item)
                (game-loop/item-processed))
              (when (= :unit (:header @atoms/menu-state))
                (movement/set-unit-mode (:coords @atoms/menu-state) clicked-item)
                (game-loop/item-processed)))
            (when-not clicked-item
              (reset! atoms/last-clicked-cell [cell-x cell-y])
              (handle-cell-click cell-x cell-y))))

        nil))))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (:contents cell)))
    (cond
      ;; 'x' sets production to nothing
      (= k :x)
      (do
        (swap! atoms/production assoc coords :none)
        (game-loop/item-processed)
        true)

      ;; Standard production keys
      (config/key->production-item k)
      (let [item (config/key->production-item k)
            [x y] coords
            coastal? (map-utils/on-coast? x y)
            naval? (config/naval-unit? item)]
        (if (and naval? (not coastal?))
          (do
            (reset! atoms/line3-message (format "Must be coastal city to produce %s." (name item)))
            true)
          (do
            (production/set-city-production coords item)
            (game-loop/item-processed)
            true))))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [height (count @atoms/game-map)
        width (count (first @atoms/game-map))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))

(defn- handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (config/key->extended-direction k)]
    (when direction
      (let [active-unit (movement/get-active-unit cell)
            is-airport-fighter? (movement/is-fighter-from-airport? cell active-unit)
            is-carrier-fighter? (movement/is-fighter-from-carrier? cell active-unit)
            is-army-aboard? (movement/is-army-aboard-transport? cell active-unit)]
        (when (and active-unit (= (:owner active-unit) :player))
          (let [[x y] coords
                [dx dy] direction
                adjacent-target [(+ x dx) (+ y dy)]
                target-cell (get-in @atoms/game-map adjacent-target)
                target (if extended?
                         (calculate-extended-target coords direction)
                         adjacent-target)]
            (cond
              is-airport-fighter?
              (let [fighter-pos (movement/launch-fighter-from-airport coords target)]
                (reset! atoms/waiting-for-input false)
                (reset! atoms/message "")
                (reset! atoms/cells-needing-attention [])
                (swap! atoms/player-items #(cons fighter-pos (rest %)))
                true)

              is-carrier-fighter?
              (let [fighter-pos (movement/launch-fighter-from-carrier coords target)]
                (reset! atoms/waiting-for-input false)
                (reset! atoms/message "")
                (reset! atoms/cells-needing-attention [])
                (swap! atoms/player-items #(cons fighter-pos (rest %)))
                true)

              (and is-army-aboard? (not extended?) (= (:type target-cell) :land) (not (:contents target-cell)))
              (do
                (movement/disembark-army-from-transport coords adjacent-target)
                (game-loop/item-processed)
                true)

              (and is-army-aboard? extended? (= (:type target-cell) :land) (not (:contents target-cell)))
              (do
                (movement/disembark-army-with-target coords adjacent-target target)
                (game-loop/item-processed)
                true)

              is-army-aboard?
              true ;; Awake army aboard - ignore invalid disembark targets

              (and (= :army (:type active-unit))
                   (not extended?)
                   (combat/hostile-city? adjacent-target))
              (do
                (combat/attempt-conquest coords adjacent-target)
                (game-loop/item-processed)
                true)

              (and (= :fighter (:type active-unit))
                   (not extended?)
                   (combat/hostile-city? adjacent-target))
              (do
                (combat/attempt-fighter-overfly coords adjacent-target)
                (game-loop/item-processed)
                true)

              :else
              (do
                (movement/set-unit-movement coords target)
                (game-loop/item-processed)
                true))))))))

(defn handle-key [k]
  (when-let [coords (first @atoms/cells-needing-attention)]
    (let [cell (get-in @atoms/game-map coords)
          active-unit (movement/get-active-unit cell)
          contents (:contents cell)
          is-airport-fighter? (movement/is-fighter-from-airport? cell active-unit)
          is-carrier-fighter? (movement/is-fighter-from-carrier? cell active-unit)
          is-army-aboard? (movement/is-army-aboard-transport? cell active-unit)
          transport-at-beach? (and (= (:type active-unit) :transport)
                                   (= (:reason active-unit) :transport-at-beach)
                                   (pos? (:army-count active-unit 0)))
          carrier-with-fighters? (and (= (:type active-unit) :carrier)
                                      (pos? (uc/get-count active-unit :fighter-count)))]
      (if active-unit
        (cond
          (and (= k :w) transport-at-beach?)
          (do
            (movement/wake-armies-on-transport coords)
            (game-loop/item-processed)
            true)

          (and (= k :w) carrier-with-fighters?)
          (do
            (movement/wake-fighters-on-carrier coords)
            (game-loop/item-processed)
            true)

          (and (= k :s) is-army-aboard?)
          (do
            (movement/sleep-armies-on-transport coords)
            (game-loop/item-processed)
            true)

          (and (= k :s) is-carrier-fighter?)
          (do
            (movement/sleep-fighters-on-carrier coords)
            (game-loop/item-processed)
            true)

          (and (= k :s) (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
          (do
            (movement/set-unit-mode coords :sentry)
            (game-loop/item-processed)
            true)

          (and (= k :x) (= :army (:type active-unit)) (not is-army-aboard?))
          (do
            (movement/set-explore-mode coords)
            (game-loop/item-processed)
            true)

          (and (= k :x) is-army-aboard?)
          (let [[x y] coords
                adjacent-cells (for [dx [-1 0 1] dy [-1 0 1]
                                     :when (not (and (zero? dx) (zero? dy)))]
                                 [(+ x dx) (+ y dy)])
                valid-target (first (filter (fn [target]
                                              (let [tcell (get-in @atoms/game-map target)]
                                                (and tcell
                                                     (= :land (:type tcell))
                                                     (not (:contents tcell)))))
                                            adjacent-cells))]
            (when valid-target
              (let [army-pos (movement/disembark-army-to-explore coords valid-target)]
                (reset! atoms/waiting-for-input false)
                (reset! atoms/message "")
                (reset! atoms/cells-needing-attention [])
                (swap! atoms/player-items #(cons army-pos (rest %)))))
            true)

          :else
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))
