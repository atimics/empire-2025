(ns acceptance.battleship-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
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
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "B"))))))
