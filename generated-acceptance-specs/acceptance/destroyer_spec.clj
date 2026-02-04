(ns acceptance.destroyer-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "destroyer.txt"

  (it "destroyer.txt:6 - Destroyer moves 2 cells in one round"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["D~~="]))
    ;; GIVEN D is waiting for input
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses D (extended movement east)
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    ;; THEN at next round D will be at =
    (dotimes [_ 3] (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "destroyer.txt:14 - Destroyer attacks enemy ship and wins"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["Ds"]))
    ;; GIVEN D is waiting for input
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d and wins the battle
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    ;; THEN at the next round D occupies the s cell and there is no s
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "s")))

  (it "destroyer.txt:27 - Destroyer attacks enemy ship and loses"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["Ds"]))
    ;; GIVEN D is waiting for input
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d and loses the battle
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    ;; THEN at the next round s remains unmoved
    (let [{:keys [pos]} (get-test-unit atoms/game-map "s")]
      (should= [1 0] pos))
    ;; and there is no D on the map
    (should-be-nil (get-test-unit atoms/game-map "D"))
    ;; and the turn message contains "Destroyer destroyed"
    (should-contain "Destroyer destroyed" @atoms/turn-message))

  (it "destroyer.txt:41 - Destroyer put to sentry mode"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["D~"]))
    ;; GIVEN D is waiting for input
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses s
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    ;; THEN D has mode sentry
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "D"))))))
