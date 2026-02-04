(ns acceptance.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
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

(describe "satellite.txt"

  (it "satellite.txt:6 - Satellite needs attention only without target"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V#"]))
    (set-test-unit atoms/game-map "V" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "V"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (should @atoms/waiting-for-input))

  (it "satellite.txt:15 - Satellite with target does not need attention"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V###%"]))
    (set-test-unit atoms/game-map "V" :mode :moving :target (:pos (get-test-cell atoms/game-map "%")))
    (reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "V"))])
    (item-processing/process-player-items-batch)
    (should-not @atoms/waiting-for-input))

  (it "satellite.txt:27 - Satellite spawns with turns-remaining 50"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V#"]))
    (should= 50 (:turns-remaining (:unit (get-test-unit atoms/game-map "V")))))

  (it "satellite.txt:35 - Satellite moves 10 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V~~~~~~~~~="]))
    (set-test-unit atoms/game-map "V" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "V"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "V")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "satellite.txt:46 - Satellite can move over land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["V#########="]))
    (set-test-unit atoms/game-map "V" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "V"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "V")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos))))
