(ns acceptance.carrier-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "carrier.txt"

  (it "carrier.txt:6 - Wake fighters on carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry)
    (set-test-unit atoms/game-map "C" :fighter-count 2 :awake-fighters 0)
    (set-test-unit atoms/game-map "C" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :u))
    (should= 2 (:awake-fighters (:unit (get-test-unit atoms/game-map "C"))))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "C")))))

  (it "carrier.txt:18 - Carrier attention message shows fighter count"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :fighter-count 3)
    (set-test-unit atoms/game-map "C" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (should-contain "carrier" @atoms/attention-message)
    (should-contain "3 fighters" @atoms/attention-message))

  (it "carrier.txt:29 - Damaged carrier attention message"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :hits 5)
    (set-test-unit atoms/game-map "C" :fighter-count 0)
    (set-test-unit atoms/game-map "C" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (should-contain "Damaged" @atoms/attention-message)))
