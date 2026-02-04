(ns acceptance.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell get-test-city reset-all-atoms! make-initial-test-map]]
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
    (reset! atoms/game-map (build-test-map ["F~="]))
    (set-test-unit atoms/game-map "F" :fuel 32)
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (dotimes [_ 2] (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))
    (should= 30 (:fuel (:unit (get-test-unit atoms/game-map "F"))))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should @atoms/waiting-for-input)
    (should-contain (:hit-edge config/messages) @atoms/message))

  (it "fighter.txt:21 - Fighter refuels at player city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FO"]))
    (set-test-unit atoms/game-map "F" :fuel 10)
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 1 (:fighter-count cell)))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:33 - Fighter bingo wakes when fuel low and city nearby"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["OF"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 9)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should-contain (:fighter-bingo config/messages) @atoms/message))

  (it "fighter.txt:45 - Fighter out of fuel wakes"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should @atoms/waiting-for-input)
    (should-contain (:fighter-out-of-fuel config/messages) @atoms/message))

  (it "fighter.txt:57 - Fighter crashes when fuel reaches zero"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should-be-nil (get-test-unit atoms/game-map "F"))
    (should-contain (:fighter-crashed config/messages) @atoms/error-message))

  (it "fighter.txt:69 - Fighter lands on carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FC"]))
    (set-test-unit atoms/game-map "C" :fighter-count 0)
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (should= 1 (:fighter-count (:unit (get-test-unit atoms/game-map "C"))))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:82 - Fighter launched from airport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O%"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/game-map update-in o-pos merge {:fighter-count 1 :awake-fighters 1}))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (advance-until-next-round)
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))
          f-result (get-test-unit atoms/game-map "F")]
      (should-not-be-nil f-result)
      (should= target-pos (:pos f-result)))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 0 (:fighter-count cell))))

  (it "fighter.txt:95 - Fighter attention shows fuel"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F#"]))
    (set-test-unit atoms/game-map "F" :fuel 20)
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (should-contain "fuel:20" @atoms/message))

  (it "fighter.txt:105 - Fighter speed is 8 per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F~~~~~~~~=~"]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (advance-until-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
