(ns acceptance.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
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

(defn- setup-waiting-for-input-city [city-spec]
  (let [pos (:pos (get-test-city atoms/game-map city-spec))
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))]
    (reset! atoms/player-map (make-initial-test-map rows cols nil))
    (reset! atoms/player-items [pos])
    (item-processing/process-player-items-batch)))

(defn- advance-to-next-round []
  (dotimes [_ 3] (game-loop/advance-game)))

(describe "fighter.txt"

  ;; ===============================================================
  ;; Fighter consumes fuel when moving over non-city terrain.
  ;; ===============================================================
  (it "fighter.txt:6 - Fighter consumes fuel when moving over non-city terrain"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F~="]))
    (set-test-unit atoms/game-map "F" :fuel 32)
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= 31 (:fuel unit))))

  ;; ===============================================================
  ;; Fighter refuels at player city.
  ;; ===============================================================
  (it "fighter.txt:19 - Fighter refuels at player city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FO"]))
    (set-test-unit atoms/game-map "F" :fuel 10)
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [cell (get-in @atoms/game-map [1 0])]
      (should= 1 (:fighter-count cell))))

  ;; ===============================================================
  ;; Fighter bingo wakes when fuel low and city nearby.
  ;; ===============================================================
  (it "fighter.txt:31 - Fighter bingo wakes when fuel low and city nearby"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["OF"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 9)
    (reset! atoms/round-number 1)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    (game-loop/start-new-round)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= :awake (:mode unit))
      (should= :fighter-bingo (:reason unit))))

  ;; ===============================================================
  ;; Fighter out of fuel wakes.
  ;; ===============================================================
  (it "fighter.txt:44 - Fighter out of fuel wakes"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 2)
    (reset! atoms/round-number 1)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    (game-loop/start-new-round)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "F")]
      (should= :awake (:mode unit))
      (should= :fighter-out-of-fuel (:reason unit))))

  ;; ===============================================================
  ;; Fighter crashes when fuel reaches zero.
  ;; ===============================================================
  (it "fighter.txt:57 - Fighter crashes when fuel reaches zero"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#F"]))
    (set-test-unit atoms/game-map "F" :mode :sentry :fuel 1)
    (reset! atoms/round-number 1)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    (game-loop/start-new-round)
    (should-be-nil (get-test-unit atoms/game-map "F")))

  ;; ===============================================================
  ;; Fighter lands on carrier.
  ;; ===============================================================
  (it "fighter.txt:69 - Fighter lands on carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FC"]))
    (set-test-unit atoms/game-map "C" :fighter-count 0 :awake-fighters 0)
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "C")]
      (should= 1 (:fighter-count unit)))
    (should-be-nil (get-test-unit atoms/game-map "F")))

  ;; ===============================================================
  ;; Fighter launched from airport.
  ;; ===============================================================
  (it "fighter.txt:82 - Fighter launched from airport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O%"]))
    (swap! atoms/game-map assoc-in [0 0 :awake-fighters] 1)
    (swap! atoms/game-map assoc-in [0 0 :fighter-count] 1)
    ;; Set production so city doesn't demand attention after launch
    (swap! atoms/production assoc [0 0] {:item :army :remaining-rounds 10})
    (setup-waiting-for-input-city "O")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos))
    (let [cell (get-in @atoms/game-map [0 0])]
      (should= 0 (:awake-fighters cell))))

  ;; ===============================================================
  ;; Fighter attention shows fuel.
  ;; ===============================================================
  (it "fighter.txt:95 - Fighter attention shows fuel"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F#"]))
    (set-test-unit atoms/game-map "F" :fuel 20)
    (setup-waiting-for-input "F")
    (should @atoms/waiting-for-input)
    (should-contain "fuel:20" @atoms/message))

  ;; ===============================================================
  ;; Fighter speed is 8 per round.
  ;; ===============================================================
  (it "fighter.txt:106 - Fighter speed is 8 per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F~~~~~~~~="]))
    (setup-waiting-for-input "F")
    (input/key-down :D)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
