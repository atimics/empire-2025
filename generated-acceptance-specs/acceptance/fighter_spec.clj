(ns acceptance.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-cell get-test-city reset-all-atoms!
                                       make-initial-test-map]]
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

(describe "fighter.txt"

  (it "fighter.txt:6 - Fighter consumes fuel when moving over non-city terrain"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["F~="]))
    ;; F has fuel 32
    (set-test-unit atoms/game-map "F" :fuel 32)
    ;; GIVEN F is waiting for input
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses D (uppercase, extended movement east)
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    ;; THEN at next round F will be at =
    (advance-until-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))
    ;; THEN F has fuel 30
    (should= 30 (:fuel (:unit (get-test-unit atoms/game-map "F"))))
    ;; THEN F is waiting for input
    (should @atoms/waiting-for-input)
    ;; THEN the turn message is :hit-edge
    (should= (:hit-edge config/messages) @atoms/turn-message))

  (it "fighter.txt:21 - Fighter refuels at player city"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["FO"]))
    ;; F has fuel 10
    (set-test-unit atoms/game-map "F" :fuel 10)
    ;; GIVEN F is waiting for input
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d
    (input/handle-key :d)
    ;; THEN at the next round O has one fighter in its airport and there is no fighter on the map
    (advance-until-next-round)
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 1 (:fighter-count cell)))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:33 - Fighter bingo wakes when fuel low and city nearby"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["OF"]))
    ;; F is sentry with fuel 9
    (set-test-unit atoms/game-map "F" :fuel 9 :mode :sentry)
    ;; Dummy production on O to prevent city attention interference
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 10}))
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    (game-loop/advance-game)
    ;; THEN F has mode awake
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    ;; and the attention message contains :fighter-bingo
    (should-contain (:fighter-bingo config/messages) @atoms/message))

  (it "fighter.txt:45 - Fighter out of fuel wakes"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["#F"]))
    ;; F is sentry with fuel 2
    (set-test-unit atoms/game-map "F" :fuel 2 :mode :sentry)
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    (game-loop/advance-game)
    ;; THEN F wakes up and asks for input
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should @atoms/waiting-for-input)
    ;; and the attention message contains :fighter-out-of-fuel
    (should-contain (:fighter-out-of-fuel config/messages) @atoms/message))

  (it "fighter.txt:57 - Fighter crashes when fuel reaches zero"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["#F"]))
    ;; F is sentry with fuel 1
    (set-test-unit atoms/game-map "F" :fuel 1 :mode :sentry)
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    (game-loop/advance-game)
    ;; THEN there is no F on the map
    (should-be-nil (get-test-unit atoms/game-map "F"))
    ;; THEN the turn message contains :fighter-out-of-fuel
    (should-contain (:fighter-out-of-fuel config/messages) @atoms/turn-message))

  (it "fighter.txt:69 - Fighter lands on carrier"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["FC"]))
    ;; C has no fighters
    (set-test-unit atoms/game-map "C" :fighter-count 0 :awake-fighters 0)
    ;; GIVEN F is waiting for input
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d
    (input/handle-key :d)
    ;; THEN at the next round C has one fighter aboard
    (advance-until-next-round)
    (should= 1 (:fighter-count (:unit (get-test-unit atoms/game-map "C"))))
    ;; and there is no F on the map
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:82 - Fighter launched from airport"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["O%"]))
    ;; GIVEN O has one fighter in its airport
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/game-map update-in o-pos merge {:fighter-count 1 :awake-fighters 1}))
    ;; Dummy production on O to prevent attention interference during advance
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 10}))
    ;; GIVEN O is waiting for input
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          o-pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [o-pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses d
    (input/handle-key :d)
    ;; THEN at the next round there is an F at %
    (advance-until-next-round)
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))
          f-result (get-test-unit atoms/game-map "F")]
      (should-not-be-nil f-result)
      (should= target-pos (:pos f-result)))
    ;; and O has no fighters
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 0 (:fighter-count cell))))

  (it "fighter.txt:95 - Fighter attention shows fuel"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["F#"]))
    ;; F has fuel 20
    (set-test-unit atoms/game-map "F" :fuel 20)
    ;; GIVEN F is waiting for input
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; THEN the attention message contains "fuel:20"
    (should-contain "fuel:20" @atoms/message))

  (it "fighter.txt:105 - Fighter speed is 8 per round"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["F~~~~~~~~=~"]))
    ;; GIVEN F is waiting for input
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    ;; WHEN the player presses D (uppercase, extended movement east)
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    ;; THEN at next round F will be at =
    (advance-until-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
