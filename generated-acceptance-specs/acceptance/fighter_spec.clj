(ns acceptance.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell get-test-city reset-all-atoms! message-matches? make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(defn- advance-until-next-round []
  (let [start-round @atoms/round-number]
    (loop [n 100]
      (cond
        (not= start-round @atoms/round-number)
        (do (game-loop/advance-game) :ok)

        (zero? n) :timeout

        :else
        (do (game-loop/advance-game)
            (recur (dec n)))))))

(defn- advance-until-unit-waiting [unit-label]
  (loop [n 100]
    (cond
      (and @atoms/waiting-for-input
           (let [u (get-test-unit atoms/game-map unit-label)]
             (and u (= (:pos u) (first @atoms/cells-needing-attention)))))
      :ok

      (zero? n) :timeout

      @atoms/waiting-for-input
      (let [coords (first @atoms/cells-needing-attention)
            cell (get-in @atoms/game-map coords)
            k (if (= :city (:type cell)) :x :space)]
        (with-redefs [q/mouse-x (constantly 0)
                      q/mouse-y (constantly 0)]
          (reset! atoms/last-key nil)
          (input/key-down k))
        (game-loop/advance-game)
        (recur (dec n)))

      :else
      (do (game-loop/advance-game)
          (recur (dec n))))))

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
    (should= :ok (advance-until-unit-waiting "F"))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")]
      (should= (:pos (get-test-cell atoms/game-map "=")) pos))
    (should= 30 (:fuel (:unit (get-test-unit atoms/game-map "F"))))
    (should-not-be-nil (:hit-edge config/messages))
    (should (message-matches? (:hit-edge config/messages) @atoms/attention-message)))

  (it "fighter.txt:20 - Fighter refuels at player city"
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
    (game-loop/advance-game)
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 1 (:fighter-count cell)))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:32 - Fighter bingo wakes when fuel low and city nearby"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["OF"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 9)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "F"))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "F"))))
    (should= :ok
      (loop [n 100]
        (let [u (get-test-unit atoms/game-map "F")]
          (cond
            (and u (= :awake (:mode (:unit u))) @atoms/waiting-for-input) :ok
            (zero? n) :timeout
            :else (do (game-loop/advance-game) (recur (dec n)))))))
    (should-not-be-nil (:fighter-bingo config/messages))
    (should (message-matches? (:fighter-bingo config/messages) @atoms/attention-message)))

  (it "fighter.txt:44 - Fighter out of fuel wakes"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "F"))
    (should-not-be-nil (:fighter-out-of-fuel config/messages))
    (should (message-matches? (:fighter-out-of-fuel config/messages) @atoms/attention-message)))

  (it "fighter.txt:56 - Fighter crashes when fuel reaches zero"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should-be-nil (get-test-unit atoms/game-map "F"))
    (should-not-be-nil (:fighter-crashed config/messages))
    (should (message-matches? (:fighter-crashed config/messages) @atoms/error-message)))

  (it "fighter.txt:68 - Fighter lands on carrier"
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
    (should= :ok (advance-until-next-round))
    (should= 1 (:fighter-count (:unit (get-test-unit atoms/game-map "C"))))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  (it "fighter.txt:81 - Fighter launched from airport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O%"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/game-map update-in o-pos merge {:fighter-count 1 :awake-fighters 1}))
    (swap! atoms/production assoc (:pos (get-test-city atoms/game-map "O")) :none)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (dotimes [_ 1] (game-loop/advance-game))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))
          f-result (get-test-unit atoms/game-map "F")]
      (should-not-be-nil f-result)
      (should= target-pos (:pos f-result)))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))
          cell (get-in @atoms/game-map o-pos)]
      (should= 0 (:fighter-count cell))))

  (it "fighter.txt:94 - Fighter attention shows fuel"
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
    (should-contain "fuel:20" @atoms/attention-message))

  (it "fighter.txt:104 - Fighter speed is 8 per round"
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
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
