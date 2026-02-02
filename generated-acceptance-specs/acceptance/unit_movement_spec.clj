(ns acceptance.unit-movement-spec
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

(defn- advance-to-next-round
  "Drains any leftover player-items from the current turn,
   starts a new round, then processes the full round."
  []
  (dotimes [_ 3] (game-loop/advance-game)))

(defn- assert-at-next-round [unit-spec cell-spec]
  (advance-to-next-round)
  (let [{:keys [pos]} (get-test-unit atoms/game-map unit-spec)
        target-pos (:pos (get-test-cell atoms/game-map cell-spec))]
    (should= target-pos pos)))

(defn- assert-eventually [unit-spec cell-spec]
  (dotimes [_ 20] (game-loop/advance-game))
  (let [{:keys [pos]} (get-test-unit atoms/game-map unit-spec)
        target-pos (:pos (get-test-cell atoms/game-map cell-spec))]
    (should= target-pos pos)))

(describe "unit-movement.txt"

  ;; --- Single-step army movement (all 8 directions) ---

  (it "unit-movement.txt:6 - Army moves northwest when player presses q"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%#" "#A"]))
    (setup-waiting-for-input "A")
    (input/handle-key :q)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:18 - Army moves north when player presses w"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%" "#A"]))
    (setup-waiting-for-input "A")
    (input/handle-key :w)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:30 - Army moves northeast when player presses e"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%" "A#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :e)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:42 - Army moves west when player presses a"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%A"]))
    (setup-waiting-for-input "A")
    (input/handle-key :a)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:53 - Army moves east when player presses d"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A%"]))
    (setup-waiting-for-input "A")
    (input/handle-key :d)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:64 - Army moves southwest when player presses z"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#A" "%#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :z)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:76 - Army moves south when player presses x"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#" "%#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :x)
    (assert-at-next-round "A" "%"))

  (it "unit-movement.txt:88 - Army moves southeast when player presses c"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#" "#%"]))
    (setup-waiting-for-input "A")
    (input/handle-key :c)
    (assert-at-next-round "A" "%"))

  ;; --- Extended army movement (all 8 directions) ---

  (it "unit-movement.txt:100 - Army extended move northwest"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%##" "###" "##A"]))
    (setup-waiting-for-input "A")
    (input/handle-key :Q)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:113 - Army extended move north"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%#" "###" "#A#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :W)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:126 - Army extended move northeast"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##%" "###" "A##"]))
    (setup-waiting-for-input "A")
    (input/handle-key :E)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:139 - Army extended move west"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%##A"]))
    (setup-waiting-for-input "A")
    (input/handle-key :A)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:150 - Army extended move east"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A##%"]))
    (setup-waiting-for-input "A")
    (input/handle-key :D)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:161 - Army extended move southwest"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##A" "###" "%##"]))
    (setup-waiting-for-input "A")
    (input/handle-key :Z)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:174 - Army extended move south"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#A#" "###" "#%#"]))
    (setup-waiting-for-input "A")
    (input/handle-key :X)
    (assert-eventually "A" "%"))

  (it "unit-movement.txt:187 - Army extended move southeast"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A##" "###" "##%"]))
    (setup-waiting-for-input "A")
    (input/handle-key :C)
    (assert-eventually "A" "%"))

  ;; --- Destroyer movement ---

  (it "unit-movement.txt:200 - Destroyer moves east on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D="]))
    (setup-waiting-for-input "D")
    (input/handle-key :d)
    (assert-at-next-round "D" "="))

  (it "unit-movement.txt:211 - Destroyer moves 2 cells in one round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D~~="]))
    (setup-waiting-for-input "D")
    (input/handle-key :D)
    (assert-at-next-round "D" "="))

  ;; --- Fighter movement ---

  (it "unit-movement.txt:222 - Fighter moves east over sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F="]))
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (assert-at-next-round "F" "="))

  (it "unit-movement.txt:233 - Fighter moves east over land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F%"]))
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (assert-at-next-round "F" "%"))

  (it "unit-movement.txt:244 - Fighter moves 8 cells in one round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F~~~~~~~~="]))
    (setup-waiting-for-input "F")
    (input/handle-key :D)
    (assert-at-next-round "F" "="))

  ;; --- Movement restrictions ---

  (it "unit-movement.txt:255 - Army cannot move onto sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A~"]))
    (setup-waiting-for-input "A")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    (should-contain "Can't move into water" @atoms/message))

  (it "unit-movement.txt:267 - Destroyer cannot move onto land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D#"]))
    (setup-waiting-for-input "D")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")]
      (should= [0 0] pos))
    (should-contain "Ships don't drive on land" @atoms/message))

  (it "unit-movement.txt:279 - Friendly army cannot move onto friendly army"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (setup-waiting-for-input "A1")
    (input/handle-key :d)
    (advance-to-next-round)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A1")]
      (should= [0 0] pos))
    (should-contain "Something's in the way" @atoms/message))

  ;; --- Combat ---

  (it "unit-movement.txt:292 - Army conquers computer city when conquest succeeds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AX"]))
    (setup-waiting-for-input "A")
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    (should-be-nil (get-test-unit atoms/game-map "A"))
    (let [city-cell (get-in @atoms/game-map [1 0])]
      (should= :player (:city-status city-cell))))

  (it "unit-movement.txt:304 - Army destroyed when conquest fails"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AX"]))
    (setup-waiting-for-input "A")
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d))
    (should-be-nil (get-test-unit atoms/game-map "A"))
    (let [city-cell (get-in @atoms/game-map [1 0])]
      (should= :computer (:city-status city-cell))))

  (it "unit-movement.txt:316 - Fighter shot down over computer city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FX"]))
    (setup-waiting-for-input "F")
    (input/handle-key :d)
    (should-contain "Fighter destroyed" @atoms/line3-message)))
