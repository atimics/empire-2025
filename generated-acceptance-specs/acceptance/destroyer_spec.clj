(ns acceptance.destroyer-spec
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

(describe "destroyer.txt"

  (it "destroyer.txt:6 - Destroyer moves 2 cells in one round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~~="]))
    (setup-waiting-for-input "D")
    ;; WHEN - extended east
    (input/handle-key :D)
    ;; THEN at next round D will be at =
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "destroyer.txt:14 - Destroyer has 3 hits"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~"]))
    (setup-waiting-for-input "D")
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "D")]
      (should= 3 (:hits unit))))

  (it "destroyer.txt:23 - Destroyer attacks enemy ship and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ds"]))
    (setup-waiting-for-input "D")
    ;; WHEN the player presses d and wins the battle
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    ;; THEN
    (should= [1 0] (:pos (get-test-unit atoms/game-map "D")))
    (should-be-nil (get-test-unit atoms/game-map "s")))

  (it "destroyer.txt:36 - Destroyer put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~"]))
    (setup-waiting-for-input "D")
    ;; WHEN
    (input/handle-key :s)
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "D")]
      (should= :sentry (:mode unit)))))
