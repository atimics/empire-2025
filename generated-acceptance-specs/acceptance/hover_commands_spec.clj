(ns acceptance.hover-commands-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms! make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "hover-commands.txt"

  (it "hover-commands.txt:6 - Set destination at mouse position"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#" "##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (let [map-w 22 map-h 16
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          cell-w (/ map-w cols) cell-h (/ map-h rows)
          px (int (+ (* 0 cell-w) (/ cell-w 2)))
          py (int (+ (* 1 cell-h) (/ cell-h 2)))]
      (reset! atoms/map-screen-dimensions [map-w map-h])
      (with-redefs [q/mouse-x (constantly px)
                    q/mouse-y (constantly py)]
        (input/key-down (keyword "."))))
    (should= [0 1] @atoms/destination))

  (it "hover-commands.txt:18 - Wake sentry unit at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (let [map-w 22 map-h 16
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          cell-w (/ map-w cols) cell-h (/ map-h rows)
          px (int (+ (* 0 cell-w) (/ cell-w 2)))
          py (int (+ (* 0 cell-h) (/ cell-h 2)))]
      (reset! atoms/map-screen-dimensions [map-w map-h])
      (with-redefs [q/mouse-x (constantly px)
                    q/mouse-y (constantly py)]
        (input/key-down :u)))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "A")))))

  (it "hover-commands.txt:29 - Set lookaround on city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :army}))
    (let [map-w 22 map-h 16
          cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          cell-w (/ map-w cols) cell-h (/ map-h rows)
          px (int (+ (* 0 cell-w) (/ cell-w 2)))
          py (int (+ (* 0 cell-h) (/ cell-h 2)))]
      (reset! atoms/map-screen-dimensions [map-w map-h])
      (with-redefs [q/mouse-x (constantly px)
                    q/mouse-y (constantly py)]
        (input/key-down :l)))
    (should= :lookaround (:marching-orders (get-in @atoms/game-map [0 0])))))
