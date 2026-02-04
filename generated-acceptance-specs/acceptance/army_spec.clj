(ns acceptance.army-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "army.txt"

  (it "army.txt:7 - Army put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "army.txt:18 - Army set to explore mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :l))
    (should= :explore (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "army.txt:29 - Army wakes near hostile city with reason"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#+"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target (:pos (get-test-city atoms/game-map "+")))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "A"))))
    (should-not-be-nil (:army-found-city config/messages))
    (should-contain (:army-found-city config/messages) @atoms/attention-message))

  (it "army.txt:41 - Army conquers free city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A+"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    (should= :player (:city-status (get-in @atoms/game-map [1 0]))))

  (it "army.txt:52 - Army skips round with space"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :space))
    (should-not @atoms/waiting-for-input))

  (it "army.txt:63 - Army blocked by friendly city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AO"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:cant-move-into-city config/messages))
    (should-contain (:cant-move-into-city config/messages) @atoms/attention-message)))
