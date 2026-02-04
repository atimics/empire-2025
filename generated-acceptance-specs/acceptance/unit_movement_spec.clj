(ns acceptance.unit-movement-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell reset-all-atoms! message-matches? make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
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

(describe "unit-movement.txt"

  (it "unit-movement.txt:6 - Army moves northwest when player presses q"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%#" "#A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :q)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:18 - Army moves north when player presses w"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%" "#A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :w)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:30 - Army moves northeast when player presses e"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%" "A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :e)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:42 - Army moves west when player presses a"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :a)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:53 - Army moves east when player presses d"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A%"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:64 - Army moves southwest when player presses z"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#A" "%#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :z)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:76 - Army moves south when player presses x"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#" "%#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :x)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:88 - Army moves southeast when player presses c"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A#" "#%"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :c)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:100 - Army extended move northwest"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%##" "###" "##A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :Q))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:113 - Army extended move north"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#%#" "###" "#A#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :W))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:126 - Army extended move northeast"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##%" "###" "A##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :E))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:139 - Army extended move west"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["%##A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :A))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:150 - Army extended move east"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A##%"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:161 - Army extended move southwest"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##A" "###" "%##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :Z))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:174 - Army extended move south"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#A#" "###" "#%#"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :X))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:187 - Army extended move southeast"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A##" "###" "##%"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :C))
    (let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (loop [n 0]
        (when (< n 20)
          (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
            (when (not= target-pos pos)
              (game-loop/advance-game)
              (recur (inc n))))))
      (should= target-pos (:pos (get-test-unit atoms/game-map "A")))))

  (it "unit-movement.txt:200 - Destroyer moves east on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["D="]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "unit-movement.txt:211 - Fighter moves east over sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F="]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "unit-movement.txt:222 - Fighter moves east over land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["F%"]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")
          target-pos (:pos (get-test-cell atoms/game-map "%"))]
      (should= target-pos pos)))

  (it "unit-movement.txt:233 - Army cannot move onto sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A~"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:cant-move-into-water config/messages))
    (should (message-matches? (:cant-move-into-water config/messages) @atoms/attention-message)))

  (it "unit-movement.txt:244 - Army conquers computer city when conquest succeeds"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AX"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 0.0)]
      (input/handle-key :d))
    (should-be-nil (get-test-unit atoms/game-map "A"))
    (should= :player (:city-status (get-in @atoms/game-map [1 0]))))

  (it "unit-movement.txt:256 - Army destroyed when conquest fails"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["AX"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [rand (constantly 1.0)]
      (input/handle-key :d))
    (should-be-nil (get-test-unit atoms/game-map "A"))
    (should= :computer (:city-status (get-in @atoms/game-map [1 0]))))

  (it "unit-movement.txt:268 - Fighter shot down over computer city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["FX"]))
    (set-test-unit atoms/game-map "F" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:fighter-destroyed-by-city config/messages))
    (should (message-matches? (:fighter-destroyed-by-city config/messages) @atoms/error-message))))
