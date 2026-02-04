(ns acceptance.patrol-boat-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(defn- advance-until-next-round []
  (let [start-round @atoms/round-number]
    (loop [n 100]
      (cond
        (not= start-round @atoms/round-number)
        (do (game-loop/advance-game) :ok)

        (zero? n) :timeout

        :else
        (do (game-loop/advance-game)
            (recur (dec n)))))))

(defn- advance-until-unit-waiting [unit-label]
  (loop [n 100]
    (cond
      (and @atoms/waiting-for-input
           (let [u (get-test-unit atoms/game-map unit-label)]
             (and u (= (:pos u) (first @atoms/cells-needing-attention)))))
      :ok

      (zero? n) :timeout

      @atoms/waiting-for-input
      (let [coords (first @atoms/cells-needing-attention)
            cell (get-in @atoms/game-map coords)
            k (if (= :city (:type cell)) :x :space)]
        (with-redefs [q/mouse-x (constantly 0)
                      q/mouse-y (constantly 0)]
          (reset! atoms/last-key nil)
          (input/key-down k))
        (game-loop/advance-game)
        (recur (dec n)))

      :else
      (do (game-loop/advance-game)
          (recur (dec n))))))

(describe "patrol-boat.txt"

  (it "patrol-boat.txt:6 - Patrol-boat moves 4 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~~~~="]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "P")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "patrol-boat.txt:17 - Patrol-boat has 1 hit"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~"]))
    (should= 1 (:hits (:unit (get-test-unit atoms/game-map "P")))))

  (it "patrol-boat.txt:25 - Patrol-boat sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~"]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "P")))))

  (it "patrol-boat.txt:36 - Patrol-boat attacks enemy and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ps"]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "P")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "s")))

  (it "patrol-boat.txt:47 - Patrol-boat attacks enemy and loses"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ps"]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "s")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "P"))
    (should-contain "Patrol-boat destroyed" @atoms/turn-message))

  (it "patrol-boat.txt:60 - Patrol-boat blocked by land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P#"]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:ships-cant-drive-on-land config/messages))
    (should-contain (:ships-cant-drive-on-land config/messages) @atoms/attention-message))

  (it "patrol-boat.txt:71 - Patrol-boat blocked by friendly ship"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["PD~"]))
    (set-test-unit atoms/game-map "P" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "P"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:somethings-in-the-way config/messages))
    (should-contain (:somethings-in-the-way config/messages) @atoms/attention-message))

  (it "patrol-boat.txt:82 - Sentry patrol-boat wakes when enemy is adjacent"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ps~"]))
    (set-test-unit atoms/game-map "P" :mode :sentry)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "P"))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "P"))))
    (should-not-be-nil (:enemy-spotted config/messages))
    (should-contain (:enemy-spotted config/messages) @atoms/attention-message)))
