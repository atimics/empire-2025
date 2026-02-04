(ns acceptance.player-production-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "player-production.txt"

  (it "player-production.txt:6 - Press a at city sets army production with 5 rounds remaining"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :a)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :army (:item prod))
      (should= 5 (:remaining-rounds prod))))

  (it "player-production.txt:17 - Press f at city sets fighter production with 10 rounds remaining"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :f))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :fighter (:item prod))
      (should= 10 (:remaining-rounds prod))))

  (it "player-production.txt:28 - Press t at coastal city sets transport production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :t))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :transport (:item prod))
      (should= 30 (:remaining-rounds prod))))

  (it "player-production.txt:40 - Non-coastal city rejects ship production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#O#" "###"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :t))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should (or (nil? prod) (= :none prod))))
    (should-contain "coastal" @atoms/error-message))

  (it "player-production.txt:53 - Press x cancels production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army}))
    (input/handle-key :x)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should (or (nil? prod) (= :none prod)))))

  (it "player-production.txt:65 - Press space skips city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :space))
    (should-not @atoms/waiting-for-input))

  (it "player-production.txt:76 - Army spawns when production completes"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 1}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "A"))))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "A")))))

  (it "player-production.txt:89 - Fighter spawns with fuel 32"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :fighter :remaining-rounds 1}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")]
      (should= [0 0] pos))
    (should= 32 (:fuel (:unit (get-test-unit atoms/game-map "F")))))

  (it "player-production.txt:101 - Satellite spawns with turns-remaining 50"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :satellite :remaining-rounds 1}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "V")]
      (should= [0 0] pos))
    (should= 50 (:turns-remaining (:unit (get-test-unit atoms/game-map "V")))))

  (it "player-production.txt:113 - Player production repeats after spawning"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 1}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :army (:item prod))
      (should= 5 (:remaining-rounds prod))))

  (it "player-production.txt:125 - Unit not spawned if city occupied"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 1}))
    (swap! atoms/game-map update-in [0 0] merge {:contents :army})
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :army (:item prod))
      (should= 1 (:remaining-rounds prod))))

  (it "player-production.txt:137 - Army spawns with marching orders"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O###%"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 1}))
    (swap! atoms/game-map update-in [0 0] merge {:marching-orders [4 0]})
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :moving (:mode (:unit (get-test-unit atoms/game-map "A"))))
    (should= [4 0] (:target (:unit (get-test-unit atoms/game-map "A")))))

  (it "player-production.txt:150 - Army spawns in explore from lookaround"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 1}))
    (swap! atoms/game-map update-in [0 0] merge {:marching-orders :lookaround})
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :explore (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "player-production.txt:162 - Fighter spawns with flight path"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O##########%"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :fighter :remaining-rounds 1}))
    (swap! atoms/game-map update-in [0 0] merge {:flight-path [11 0]})
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :moving (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should= [11 0] (:target (:unit (get-test-unit atoms/game-map "F")))))

  (it "player-production.txt:175 - Satellite costs 50 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :z)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :satellite (:item prod))
      (should= 50 (:remaining-rounds prod))))

  (it "player-production.txt:186 - Patrol-boat costs 15 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :p))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :patrol-boat (:item prod))
      (should= 15 (:remaining-rounds prod))))

  (it "player-production.txt:198 - Destroyer costs 20 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :destroyer (:item prod))
      (should= 20 (:remaining-rounds prod))))

  (it "player-production.txt:210 - Submarine costs 20 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :submarine (:item prod))
      (should= 20 (:remaining-rounds prod))))

  (it "player-production.txt:222 - Carrier costs 30 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :c)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :carrier (:item prod))
      (should= 30 (:remaining-rounds prod))))

  (it "player-production.txt:234 - Battleship costs 40 rounds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "~~"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :b))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :battleship (:item prod))
      (should= 40 (:remaining-rounds prod))))

  (it "player-production.txt:246 - Production countdown decrements each round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 3}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :army (:item prod))
      (should= 2 (:remaining-rounds prod))))

  (it "player-production.txt:257 - Changing production replaces previous production"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army}))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :f))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "O")))]
      (should= :fighter (:item prod))
      (should= 10 (:remaining-rounds prod)))))
