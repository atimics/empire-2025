(ns acceptance.carrier-spec
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

(describe "carrier.txt"

  (it "carrier.txt:6 - Carrier holds fighters"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :fighter-count 3)
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "C")]
      (should= 3 (:fighter-count unit))))

  (it "carrier.txt:15 - Carrier wakes with awake fighters"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :fighter-count 2 :awake-fighters 1)
    ;; GIVEN player-items C
    (reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "C"))])
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN player items are processed
    (item-processing/process-player-items-batch)
    ;; THEN
    (should @atoms/waiting-for-input))

  (it "carrier.txt:26 - Wake fighters on carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    ;; C is sentry with fighter-count 2 and awake-fighters 0
    ;; But sentry + 0 awake-fighters won't get attention, so we must set :awake
    ;; for the "waiting for input" pattern to work. The u command will set it back to sentry.
    (set-test-unit atoms/game-map "C" :fighter-count 2 :awake-fighters 0)
    (setup-waiting-for-input "C")
    ;; WHEN
    (input/handle-key :u)
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "C")]
      (should= 2 (:awake-fighters unit))
      (should= :sentry (:mode unit))))

  (it "carrier.txt:39 - Carrier attention message shows fighter count"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :fighter-count 3)
    (setup-waiting-for-input "C")
    ;; THEN
    (should @atoms/waiting-for-input)
    (should-contain "carrier" @atoms/message)
    (should-contain "3 fighters" @atoms/message))

  (it "carrier.txt:52 - Damaged carrier attention message"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :hits 5 :fighter-count 0)
    (setup-waiting-for-input "C")
    ;; THEN
    (should @atoms/waiting-for-input)
    (should-contain "Damaged" @atoms/message)))
