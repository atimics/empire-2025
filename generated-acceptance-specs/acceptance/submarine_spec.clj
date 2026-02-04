(ns acceptance.submarine-spec
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

(describe "submarine.txt"

  (it "submarine.txt:6 - Submarine has 2 hits"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S~"]))
    (should= 2 (:hits (:unit (get-test-unit atoms/game-map "S")))))

  (it "submarine.txt:14 - Submarine moves on sea only"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S#"]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:ships-cant-drive-on-land config/messages))
    (should-contain (:ships-cant-drive-on-land config/messages) @atoms/attention-message))

  (it "submarine.txt:25 - Submarine attacks destroyer and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Sd"]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "S")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "d")))

  (it "submarine.txt:36 - Submarine put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S~"]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "S")))))

  (it "submarine.txt:47 - Submarine moves 2 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S~~="]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "S")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "submarine.txt:58 - Submarine attacks enemy and loses"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Sd"]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "d")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "S"))
    (should-contain "Submarine destroyed" @atoms/turn-message))

  (it "submarine.txt:71 - Submarine blocked by friendly ship"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["SD~"]))
    (set-test-unit atoms/game-map "S" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "S"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:somethings-in-the-way config/messages))
    (should-contain (:somethings-in-the-way config/messages) @atoms/attention-message))

  (it "submarine.txt:82 - Sentry submarine wakes when enemy is adjacent"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Sd~"]))
    (set-test-unit atoms/game-map "S" :mode :sentry)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "S"))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "S"))))
    (should-not-be-nil (:enemy-spotted config/messages))
    (should-contain (:enemy-spotted config/messages) @atoms/attention-message)))
