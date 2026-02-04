(ns acceptance.battleship-spec
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

(describe "battleship.txt"

  (it "battleship.txt:6 - Battleship moves 2 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~~="]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "B")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "battleship.txt:17 - Battleship put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~"]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "B")))))

  (it "battleship.txt:28 - Battleship attacks enemy submarine and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Bs"]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "B")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "s")))

  (it "battleship.txt:39 - Battleship attacks enemy submarine and loses"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Bs"]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "s")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "B"))
    (should-contain "Battleship destroyed" @atoms/turn-message))

  (it "battleship.txt:52 - Battleship blocked by land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B#"]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:ships-cant-drive-on-land config/messages))
    (should-contain (:ships-cant-drive-on-land config/messages) @atoms/attention-message))

  (it "battleship.txt:63 - Battleship blocked by friendly ship"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["BS~"]))
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:somethings-in-the-way config/messages))
    (should-contain (:somethings-in-the-way config/messages) @atoms/attention-message))

  (it "battleship.txt:74 - Damaged battleship attention message"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~"]))
    (set-test-unit atoms/game-map "B" :hits 5)
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (should-contain "Damaged" @atoms/attention-message))

  (it "battleship.txt:85 - Damaged battleship has reduced speed"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~=~"]))
    (set-test-unit atoms/game-map "B" :hits 5)
    (set-test-unit atoms/game-map "B" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "B"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "B")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "battleship.txt:97 - Sentry battleship wakes when enemy is adjacent"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Bs~"]))
    (set-test-unit atoms/game-map "B" :mode :sentry)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "B"))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "B"))))
    (should-not-be-nil (:enemy-spotted config/messages))
    (should-contain (:enemy-spotted config/messages) @atoms/attention-message)))
