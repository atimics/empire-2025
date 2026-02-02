(ns acceptance.hover-commands-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]
            [quil.core :as q]))

(defn- setup-waiting-for-input-unit [unit-spec]
  (set-test-unit atoms/game-map unit-spec :mode :awake)
  (let [pos (:pos (get-test-unit atoms/game-map unit-spec))
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))]
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

(defn- setup-map-screen-dimensions
  "Sets map-screen-dimensions consistent with config/cell-size and current game-map."
  []
  (let [[cw ch] config/cell-size
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))]
    (reset! atoms/map-screen-dimensions [(* cols cw) (* rows ch)])))

(describe "hover-commands.txt"

  ;; ===============================================================
  ;; Set destination at mouse position.
  ;; ===============================================================
  (it "hover-commands.txt:7 - Set destination at mouse position"
    (reset-all-atoms!)
    ;; Map needs 2 rows so cell [0 1] is on-map.
    (reset! atoms/game-map (build-test-map ["A#" "##"]))
    (setup-waiting-for-input-unit "A")
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 1] and the player presses period.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 1 ch))]
        (input/key-down (keyword "."))))
    ;; THEN destination is [0 1].
    (should= [0 1] @atoms/destination))

  ;; ===============================================================
  ;; Set marching orders on city from destination.
  ;; ===============================================================
  (it "hover-commands.txt:18 - Set marching orders on city from destination"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is army.
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds (config/item-cost :army)}))
    ;; GIVEN destination [1 0].
    (reset! atoms/destination [1 0])
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses m.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down :m)))
    ;; THEN cell [0 0] has marching-orders [1 0].
    (should= [1 0] (:marching-orders (get-in @atoms/game-map [0 0])))
    ;; THEN destination is nil.
    (should-be-nil @atoms/destination))

  ;; ===============================================================
  ;; Set flight path on city from destination.
  ;; ===============================================================
  (it "hover-commands.txt:31 - Set flight path on city from destination"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is fighter.
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :fighter :remaining-rounds (config/item-cost :fighter)}))
    ;; GIVEN destination [1 0].
    (reset! atoms/destination [1 0])
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses f.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down :f)))
    ;; THEN cell [0 0] has flight-path [1 0].
    (should= [1 0] (:flight-path (get-in @atoms/game-map [0 0])))
    ;; THEN destination is nil.
    (should-be-nil @atoms/destination))

  ;; ===============================================================
  ;; Wake sentry unit at mouse.
  ;; ===============================================================
  (it "hover-commands.txt:44 - Wake sentry unit at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; A is sentry.
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses u.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down :u)))
    ;; THEN A has mode awake.
    (let [{:keys [unit]} (get-test-unit atoms/game-map "A")]
      (should= :awake (:mode unit))))

  ;; ===============================================================
  ;; Set lookaround on city at mouse.
  ;; ===============================================================
  (it "hover-commands.txt:55 - Set lookaround on city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O#"]))
    ;; GIVEN production at O is army.
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds (config/item-cost :army)}))
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses l.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down :l)))
    ;; THEN cell [0 0] has marching-orders lookaround.
    (should= :lookaround (:marching-orders (get-in @atoms/game-map [0 0]))))

  ;; ===============================================================
  ;; Set waypoint at cell.
  ;; ===============================================================
  (it "hover-commands.txt:66 - Set waypoint at cell"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses *.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "*"))))
    ;; THEN cell [0 0] has waypoint.
    (should (:waypoint (get-in @atoms/game-map [0 0]))))

  ;; ===============================================================
  ;; Remove waypoint at cell.
  ;; ===============================================================
  (it "hover-commands.txt:76 - Remove waypoint at cell"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["*#"]))
    (setup-map-screen-dimensions)
    ;; WHEN the mouse is at cell [0 0] and the player presses *.
    (let [[cw ch] config/cell-size]
      (with-redefs [quil.core/mouse-x (constantly (* 0 cw))
                    quil.core/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "*"))))
    ;; THEN cell [0 0] does not have waypoint.
    (should-not (:waypoint (get-in @atoms/game-map [0 0]))))

  ;; ===============================================================
  ;; Toggle pause requests pause.
  ;; ===============================================================
  (it "hover-commands.txt:86 - Toggle pause requests pause"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; WHEN the player presses P.
    (input/key-down :P)
    ;; THEN pause-requested.
    (should @atoms/pause-requested))

  ;; ===============================================================
  ;; Unpause from paused state.
  ;; ===============================================================
  (it "hover-commands.txt:96 - Unpause from paused state"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN paused.
    (reset! atoms/paused true)
    ;; WHEN the player presses P.
    (input/key-down :P)
    ;; THEN not paused.
    (should-not @atoms/paused))

  ;; ===============================================================
  ;; Step one round when paused.
  ;; ===============================================================
  (it "hover-commands.txt:107 - Step one round when paused"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN paused.
    (reset! atoms/paused true)
    ;; GIVEN round 1.
    (reset! atoms/round-number 1)
    ;; WHEN the player presses space.
    (input/key-down :space)
    ;; THEN not paused.
    (should-not @atoms/paused)
    ;; THEN pause-requested.
    (should @atoms/pause-requested))

  ;; ===============================================================
  ;; Toggle map display from player-map to computer-map.
  ;; ===============================================================
  (it "hover-commands.txt:120 - Toggle map display from player-map to computer-map"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; WHEN the player presses +.
    (input/key-down :+)
    ;; THEN map-to-display is computer-map.
    (should= :computer-map @atoms/map-to-display))

  ;; ===============================================================
  ;; Cycle map display from computer-map to actual-map.
  ;; ===============================================================
  (it "hover-commands.txt:130 - Cycle map display from computer-map to actual-map"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN map-to-display computer-map.
    (reset! atoms/map-to-display :computer-map)
    ;; WHEN the player presses +.
    (input/key-down :+)
    ;; THEN map-to-display is actual-map.
    (should= :actual-map @atoms/map-to-display))

  ;; ===============================================================
  ;; Cycle map display from actual-map back to player-map.
  ;; ===============================================================
  (it "hover-commands.txt:141 - Cycle map display from actual-map back to player-map"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#"]))
    ;; GIVEN map-to-display actual-map.
    (reset! atoms/map-to-display :actual-map)
    ;; WHEN the player presses +.
    (input/key-down :+)
    ;; THEN map-to-display is player-map.
    (should= :player-map @atoms/map-to-display)))
