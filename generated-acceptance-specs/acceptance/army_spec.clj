(ns acceptance.army-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       make-initial-test-map reset-all-atoms!]]
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

(describe "army.txt"

  (it "army.txt:7 - Army put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :s)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :sentry (:mode unit))))

  (it "army.txt:18 - Army set to explore mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :l)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :explore (:mode unit))))

  (it "army.txt:29 - Army wakes near hostile city with reason"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A+"]))
    (set-test-unit atoms/game-map "A" :mode :explore
                   :explore-steps 50 :visited #{[0 0]})
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      ;; First batch: explore unit gets stuck (no valid land cells), wakes up
      (item-processing/process-player-items-batch)
      ;; Army is now awake at [0 0] but was removed from player-items.
      ;; Re-add for attention processing (simulates next round discovery).
      (let [new-pos (:pos (get-test-unit atoms/game-map "A"))]
        (reset! atoms/player-items [new-pos])
        (item-processing/process-player-items-batch)))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :awake (:mode unit)))
    (should @atoms/waiting-for-input)
    (should-contain "Army found a city" @atoms/message))

  (it "army.txt:43 - Army conquers free city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A+"]))
    (setup-waiting-for-input "A")
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    (should= :player (:city-status (get-in @atoms/game-map [1 0]))))

  (it "army.txt:54 - Army skips round with space"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :space)
    (should-not @atoms/waiting-for-input))

  (it "army.txt:65 - Army blocked by friendly city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AO"]))
    (setup-waiting-for-input "A")
    (input/handle-key :d)
    ;; Army is now in :moving mode toward the city.
    ;; Advance game to let movement system process and wake the army.
    (advance-to-next-round)
    (should-contain "Can't move into city" @atoms/message)))
