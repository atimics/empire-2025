(ns acceptance.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]))

(describe "fighter.txt"
  (it "fighter.txt:6 - Fighter consumes fuel when moving over non-city terrain."
    (reset-all-atoms!)
    ;; GIVEN game map F~=
    (reset! atoms/game-map (build-test-map ["F~="]))
    ;; F has fuel 32.
    (set-test-unit atoms/game-map "F" :fuel 32)
    ;; GIVEN F is waiting for input.
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d.
    (input/handle-key :d)
    ;; THEN at next round F will be at =.
    (dotimes [_ 3] (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))
    ;; THEN F has fuel 31.
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= 31 (:fuel unit)))
    ;; THEN F is waiting for input and "Fighter hit edge of map" is displayed in line 2.
    (should @atoms/waiting-for-input)
    (should-contain "Fighter hit edge of map" @atoms/turn-message))

  (it "fighter.txt:20 - Fighter refuels at player city."
    (reset-all-atoms!)
    ;; GIVEN game map FO
    (reset! atoms/game-map (build-test-map ["FO"]))
    ;; F has fuel 10.
    (set-test-unit atoms/game-map "F" :fuel 10)
    ;; GIVEN F is waiting for input.
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d.
    (input/handle-key :d)
    ;; THEN at the next round O has one fighter in its airport and there is no fighter on the map.
    (dotimes [_ 3] (game-loop/advance-game))
    (let [o-cell (get-in @atoms/game-map (:pos (get-test-city atoms/game-map "O")))]
      (should= 1 (:fighter-count o-cell 0)))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:31 - Fighter bingo wakes when fuel low and city nearby."
    (reset-all-atoms!)
    ;; GIVEN game map OF
    (reset! atoms/game-map (build-test-map ["OF"]))
    ;; F is sentry with fuel 9.
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 9)
    ;; Set production for O so it doesn't intercept attention
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 10}))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN a new round starts.
    (game-loop/start-new-round)
    ;; Advance to process items and display message
    (game-loop/advance-game)
    ;; THEN F has mode awake.
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= :awake (:mode unit)))
    ;; and the bingo message is displayed.
    (should-contain "Bingo" @atoms/message))

  (it "fighter.txt:43 - Fighter out of fuel wakes."
    (reset-all-atoms!)
    ;; GIVEN game map #F
    (reset! atoms/game-map (build-test-map ["#F"]))
    ;; F is sentry with fuel 2.
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN a new round starts.
    (game-loop/start-new-round)
    ;; Advance to process items and display message
    (game-loop/advance-game)
    ;; THEN F wakes up and asks for input,
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= :awake (:mode unit)))
    (should @atoms/waiting-for-input)
    ;; and the out-of-fuel message is displayed.
    (should-contain "out of fuel" @atoms/message))

  (it "fighter.txt:54 - Fighter crashes when fuel reaches zero."
    (reset-all-atoms!)
    ;; GIVEN game map #F
    (reset! atoms/game-map (build-test-map ["#F"]))
    ;; F is sentry with fuel 1.
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN a new round starts.
    (game-loop/start-new-round)
    ;; THEN there is no F on the map and the crash message is displayed.
    (should-be-nil (get-test-unit atoms/game-map "F"))
    (should-not= "" @atoms/error-message))

  (it "fighter.txt:65 - Fighter lands on carrier."
    (reset-all-atoms!)
    ;; GIVEN game map FC
    (reset! atoms/game-map (build-test-map ["FC"]))
    ;; C has no fighters.
    (set-test-unit atoms/game-map "C" :fighter-count 0 :awake-fighters 0)
    ;; GIVEN F is waiting for input.
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d.
    (input/handle-key :d)
    ;; THEN At the next round C has one fighter aboard
    (dotimes [_ 3] (game-loop/advance-game))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "C")]
      (should= 1 (:fighter-count unit 0)))
    ;; and there is no F on the map.
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:78 - Fighter launched from airport."
    (reset-all-atoms!)
    ;; GIVEN game map O%
    (reset! atoms/game-map (build-test-map ["O%"]))
    ;; GIVEN O has one fighter in its airport.
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/game-map assoc-in (conj o-pos :fighter-count) 1)
      (swap! atoms/game-map assoc-in (conj o-pos :awake-fighters) 1))
    ;; Set production for O so city doesn't intercept attention during advance
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 10}))
    ;; GIVEN O is waiting for input.
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [o-pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d.
    (input/handle-key :d)
    ;; THEN at the next round there is an F at %.
    (dotimes [_ 3] (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos))
    ;; and O has no fighters.
    (let [o-cell (get-in @atoms/game-map (:pos (get-test-city atoms/game-map "O")))]
      (should= 0 (:fighter-count o-cell 0))))

  (it "fighter.txt:92 - Fighter attention shows fuel."
    (reset-all-atoms!)
    ;; GIVEN game map F#
    (reset! atoms/game-map (build-test-map ["F#"]))
    ;; F has fuel 20.
    (set-test-unit atoms/game-map "F" :fuel 20)
    ;; GIVEN F is waiting for input.
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; THEN message contains "fuel:20".
    (should-contain "fuel:20" @atoms/message))

  (it "fighter.txt:103 - Fighter speed is 8 per round."
    (reset-all-atoms!)
    ;; GIVEN game map F~~~~~~~~=~
    (reset! atoms/game-map (build-test-map ["F~~~~~~~~=~"]))
    ;; GIVEN F is waiting for input.
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses D.
    (reset! atoms/last-key nil)
    (input/key-down :D)
    ;; THEN at next round F will be at =.
    (dotimes [_ 3] (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
