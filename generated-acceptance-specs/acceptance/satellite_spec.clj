(ns acceptance.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       make-initial-test-map reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.movement.visibility :as visibility]
            [empire.ui.input :as input]))

(defn- setup-waiting-for-input [unit-spec]
  (set-test-unit atoms/game-map unit-spec :mode :awake)
  (let [cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        pos (:pos (get-test-unit atoms/game-map unit-spec))]
    (reset! atoms/player-map (make-initial-test-map rows cols nil))
    (reset! atoms/player-items [pos])
    (item-processing/process-player-items-batch)))

(describe "satellite.txt"

  (it "satellite.txt:6 - Satellite needs attention only without target"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V#"]))
    (setup-waiting-for-input "V")
    (should @atoms/waiting-for-input))

  (it "satellite.txt:15 - Satellite with target does not need attention"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V####"]))
    (set-test-unit atoms/game-map "V" :target [4 0])
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    (reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "V"))])
    (item-processing/process-player-items-batch)
    (should-not @atoms/waiting-for-input))

  (it "satellite.txt:27 - Satellite has 2-cell visibility radius"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#####" "#####" "##V##" "#####" "#####"]))
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    (let [pos (:pos (get-test-unit atoms/game-map "V"))]
      (visibility/update-cell-visibility pos :player))
    (should-not-be-nil (get-in @atoms/player-map [2 2]))
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [4 4])))

  ;; NOTE: This test documents the desired behavior that satellites spawn with
  ;; turns-remaining 50. Currently build-test-map uses make-unit which only sets
  ;; :type, :owner, and :hits. The satellite's initial-state (including
  ;; :turns-remaining 50) is not applied by make-unit, so this test will FAIL
  ;; until make-unit or build-test-map is updated to include initial-state fields.
  (it "satellite.txt:49 - Satellite spawns with turns-remaining 50"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V#"]))
    (let [{:keys [unit]} (get-test-unit atoms/game-map "V")]
      (should= 50 (:turns-remaining unit)))))
