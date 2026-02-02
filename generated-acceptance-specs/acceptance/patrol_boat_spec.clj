(ns acceptance.patrol-boat-spec
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

(describe "patrol-boat.txt"

  (it "patrol-boat.txt:6 - Patrol-boat moves 4 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~~~~="]))
    (setup-waiting-for-input "P")
    (input/handle-key :D)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "P")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "patrol-boat.txt:17 - Patrol-boat has 1 hit"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~"]))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "P")]
      (should= 1 (:hits unit))))

  (it "patrol-boat.txt:25 - Patrol-boat sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["P~"]))
    (setup-waiting-for-input "P")
    (input/handle-key :s)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "P")]
      (should= :sentry (:mode unit)))))
