(ns acceptance.player-production-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]))

(describe "player-production.txt"

  ;; player-production.txt:6 - Press a at city sets army production with 5 rounds remaining.
  (it "player-production.txt:6 - Press a at city sets army production with 5 rounds remaining"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN O is waiting for input
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses a
    (input/handle-key :a)
    ;; THEN production at O is army with 5 rounds remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))
      (should= 5 (:remaining-rounds prod))))

  ;; player-production.txt:17 - Press f at city sets fighter production with 10 rounds remaining.
  (it "player-production.txt:17 - Press f at city sets fighter production with 10 rounds remaining"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :f)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :fighter (:item prod))
      (should= 10 (:remaining-rounds prod))))

  ;; player-production.txt:28 - Press t at coastal city sets transport production.
  (it "player-production.txt:28 - Press t at coastal city sets transport production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :t)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :transport (:item prod))
      (should= 30 (:remaining-rounds prod))))

  ;; player-production.txt:40 - Non-coastal city rejects ship production.
  (it "player-production.txt:40 - Non-coastal city rejects ship production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#O#"
                                            "###"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :t)
    ;; THEN there is no production at O
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should (or (nil? prod) (= :none prod))))
    ;; THEN line3-message contains "coastal"
    (should-contain "coastal" @atoms/line3-message))

  ;; player-production.txt:53 - Press x cancels production.
  (it "player-production.txt:53 - Press x cancels production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN O is waiting for input (city with no production triggers attention)
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; GIVEN production at O is army (set after attention is triggered)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds (config/item-cost :army)}))
    ;; WHEN the player presses x
    (input/handle-key :x)
    ;; THEN there is no production at O
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should (or (nil? prod) (= :none prod)))))

  ;; player-production.txt:65 - Press space skips city.
  (it "player-production.txt:65 - Press space skips city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN O is waiting for input
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses space
    (input/handle-key :space)
    ;; THEN not waiting-for-input
    (should-not @atoms/waiting-for-input))

  ;; player-production.txt:76 - Army spawns when production completes.
  (it "player-production.txt:76 - Army spawns when production completes"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is army with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN there is an A at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    ;; THEN A has mode awake
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :awake (:mode unit)))
    ;; THEN A has owner player
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :player (:owner unit))))

  ;; player-production.txt:89 - Fighter spawns with fuel 32.
  (it "player-production.txt:89 - Fighter spawns with fuel 32"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :fighter :remaining-rounds 1}))
    (game-loop/start-new-round)
    ;; THEN there is an F at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")]
      (should= [0 0] pos))
    ;; THEN F has fuel 32
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= 32 (:fuel unit))))

  ;; player-production.txt:101 - Satellite spawns with turns-remaining 50.
  (it "player-production.txt:101 - Satellite spawns with turns-remaining 50"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :satellite :remaining-rounds 1}))
    (game-loop/start-new-round)
    ;; THEN there is a V at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "V")]
      (should= [0 0] pos))
    ;; THEN V has turns-remaining 50
    (let [{:keys [unit]} (get-test-unit atoms/game-map "V")]
      (should= 50 (:turns-remaining unit))))

  ;; player-production.txt:113 - Player production repeats after spawning.
  (it "player-production.txt:113 - Player production repeats after spawning"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    (game-loop/start-new-round)
    ;; THEN there is an A at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    ;; THEN production at O is army with 5 rounds remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))
      (should= 5 (:remaining-rounds prod))))

  ;; player-production.txt:125 - Unit not spawned if city occupied.
  (it "player-production.txt:125 - Unit not spawned if city occupied"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is army with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    ;; GIVEN cell [0 0] has contents with type army and owner player
    (swap! atoms/game-map assoc-in [0 0 :contents] {:type :army :owner :player :hits 1 :mode :awake})
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN production at O is army with 1 round remaining (not decremented because city occupied)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))
      (should= 1 (:remaining-rounds prod))))

  ;; player-production.txt:137 - Army spawns with marching orders.
  (it "player-production.txt:137 - Army spawns with marching orders"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O###%"]))
    ;; GIVEN production at O is army with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    ;; GIVEN cell [0 0] has marching-orders [4 0]
    (swap! atoms/game-map assoc-in [0 0 :marching-orders] [4 0])
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN there is an A at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    ;; THEN A has mode moving
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :moving (:mode unit)))
    ;; THEN A has target [4 0]
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= [4 0] (:target unit))))

  ;; player-production.txt:151 - Army spawns in explore from lookaround.
  (it "player-production.txt:151 - Army spawns in explore from lookaround"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is army with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    ;; GIVEN cell [0 0] has marching-orders lookaround
    (swap! atoms/game-map assoc-in [0 0 :marching-orders] :lookaround)
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN there is an A at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    ;; THEN A has mode explore
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :explore (:mode unit))))

  ;; player-production.txt:163 - Fighter spawns with flight path.
  (it "player-production.txt:163 - Fighter spawns with flight path"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O###%"]))
    ;; GIVEN production at O is fighter with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :fighter :remaining-rounds 1}))
    ;; GIVEN cell [0 0] has flight-path [4 0]
    (swap! atoms/game-map assoc-in [0 0 :flight-path] [4 0])
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN there is an F at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")]
      (should= [0 0] pos))
    ;; THEN F has mode moving
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= :moving (:mode unit)))
    ;; THEN F has target [4 0]
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= [4 0] (:target unit))))

  ;; player-production.txt:178 - Army costs 5 rounds.
  (it "player-production.txt:178 - Army costs 5 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :a)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))
      (should= 5 (:remaining-rounds prod))))

  ;; player-production.txt:189 - Fighter costs 10 rounds.
  (it "player-production.txt:189 - Fighter costs 10 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :f)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :fighter (:item prod))
      (should= 10 (:remaining-rounds prod))))

  ;; player-production.txt:200 - Satellite costs 50 rounds.
  (it "player-production.txt:200 - Satellite costs 50 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :z)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :satellite (:item prod))
      (should= 50 (:remaining-rounds prod))))

  ;; player-production.txt:211 - Transport costs 30 rounds.
  (it "player-production.txt:211 - Transport costs 30 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :t)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :transport (:item prod))
      (should= 30 (:remaining-rounds prod))))

  ;; player-production.txt:222 - Patrol-boat costs 15 rounds.
  (it "player-production.txt:222 - Patrol-boat costs 15 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :p)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :patrol-boat (:item prod))
      (should= 15 (:remaining-rounds prod))))

  ;; player-production.txt:233 - Destroyer costs 20 rounds.
  (it "player-production.txt:233 - Destroyer costs 20 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :destroyer (:item prod))
      (should= 20 (:remaining-rounds prod))))

  ;; player-production.txt:244 - Submarine costs 20 rounds.
  (it "player-production.txt:244 - Submarine costs 20 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :s)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :submarine (:item prod))
      (should= 20 (:remaining-rounds prod))))

  ;; player-production.txt:255 - Carrier costs 30 rounds.
  (it "player-production.txt:255 - Carrier costs 30 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :c)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :carrier (:item prod))
      (should= 30 (:remaining-rounds prod))))

  ;; player-production.txt:266 - Battleship costs 40 rounds.
  (it "player-production.txt:266 - Battleship costs 40 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                            "~~"]))
    (let [pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :b)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))
          prod (get @atoms/production city-coords)]
      (should= :battleship (:item prod))
      (should= 40 (:remaining-rounds prod)))))
