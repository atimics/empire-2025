(ns acceptance.battleship-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-cell make-initial-test-map
                                       reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]))

(defn- setup-waiting-for-input [unit-spec]
  (set-test-unit atoms/game-map unit-spec :mode :awake)
  (let [cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        pos (:pos (get-test-unit atoms/game-map unit-spec))]
    (reset! atoms/player-map (make-initial-test-map rows cols nil))
    (reset! atoms/player-items [pos])
    (item-processing/process-player-items-batch)))

(defn- advance-to-next-round []
  (dotimes [_ 3] (game-loop/advance-game)))

(describe "battleship.txt"

  (it "battleship.txt:6 - Battleship has 10 hits"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~"]))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "B")]
      (should= 10 (:hits unit))))

  (it "battleship.txt:14 - Battleship survives many hits"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~"]))
    (set-test-unit atoms/game-map "B" :hits 5)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "B")]
      (should= 5 (:hits unit))))

  (it "battleship.txt:23 - Battleship moves 2 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~~="]))
    (setup-waiting-for-input "B")
    (input/handle-key :D)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "B")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "battleship.txt:34 - Battleship put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["B~"]))
    (setup-waiting-for-input "B")
    (input/handle-key :s)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "B")]
      (should= :sentry (:mode unit)))))
