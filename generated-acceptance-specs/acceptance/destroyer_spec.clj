(ns acceptance.destroyer-spec
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

(describe "destroyer.txt"

  (it "destroyer.txt:6 - Destroyer moves 2 cells in one round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~~="]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "destroyer.txt:17 - Destroyer attacks enemy ship and wins"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ds"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "s")))

  (it "destroyer.txt:28 - Destroyer attacks enemy ship and loses"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["Ds"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d)
      (game-loop/advance-game))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "s")]
      (should= [1 0] pos))
    (should-be-nil (get-test-unit atoms/game-map "D"))
    (should-contain "Destroyer destroyed" @atoms/turn-message))

  (it "destroyer.txt:41 - Destroyer put to sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "D"))))))
