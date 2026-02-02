(ns acceptance.submarine-spec
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

(describe "submarine.txt"

  (it "submarine.txt:6 - Submarine has 2 hits"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S~"]))
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "S")]
      (should= 2 (:hits unit))))

  (it "submarine.txt:13 - Submarine moves on sea only"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S#"]))
    (setup-waiting-for-input "S")
    ;; WHEN
    (input/handle-key :d)
    ;; THEN
    (should-contain "Ships don't drive on land" @atoms/message))

  (it "submarine.txt:23 - Submarine attacks destroyer and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Sd"]))
    (setup-waiting-for-input "S")
    ;; WHEN the player presses d and wins the battle
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    ;; THEN
    (should= [1 0] (:pos (get-test-unit atoms/game-map "S")))
    (should-be-nil (get-test-unit atoms/game-map "d")))

  (it "submarine.txt:36 - Submarine put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["S~"]))
    (setup-waiting-for-input "S")
    ;; WHEN
    (input/handle-key :s)
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "S")]
      (should= :sentry (:mode unit)))))
