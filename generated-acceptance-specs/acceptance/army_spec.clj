(ns acceptance.army-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(defn- advance-until-next-round []
  (let [start-round @atoms/round-number]
    (while (= start-round @atoms/round-number)
      (game-loop/advance-game)))
  (game-loop/advance-game))

(describe "army.txt"

  (it "army.txt:7 - Army put to sentry mode"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN A is waiting for input
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses s
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    ;; THEN A has mode sentry
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "army.txt:18 - Army set to explore mode"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN A is waiting for input
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses l
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :l))
    ;; THEN A has mode explore
    (should= :explore (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "army.txt:29 - Army wakes near hostile city with reason"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A+"]))
    ;; A is explore
    (set-test-unit atoms/game-map "A" :mode :explore)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN the next round begins
    (game-loop/start-new-round)
    (advance-until-next-round)
    ;; THEN A is awake
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "A"))))
    ;; THEN the attention message contains :army-found-city
    (should-contain (:army-found-city config/messages) @atoms/message))

  (it "army.txt:42 - Army conquers free city"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A+"]))
    ;; GIVEN A is waiting for input
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d and wins the battle
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    ;; THEN cell [1 0] has city-status player
    (should= :player (:city-status (get-in @atoms/game-map [1 0]))))

  (it "army.txt:52 - Army skips round with space"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN A is waiting for input
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses space
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :space))
    ;; THEN not waiting-for-input
    (should-not @atoms/waiting-for-input))

  (it "army.txt:63 - Army blocked by friendly city"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["AO"]))
    ;; GIVEN A is waiting for input
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d
    (input/handle-key :d)
    ;; Advance until movement resolves and army gets attention
    (advance-until-next-round)
    ;; THEN the attention message contains :cant-move-into-city
    (should-contain (:cant-move-into-city config/messages) @atoms/message)))
