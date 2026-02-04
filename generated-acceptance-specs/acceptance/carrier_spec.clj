(ns acceptance.carrier-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell reset-all-atoms! make-initial-test-map]]
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

(defn- advance-until-unit-waiting [unit-label]
  (loop [n 100]
    (cond
      (and @atoms/waiting-for-input
           (let [u (get-test-unit atoms/game-map unit-label)]
             (and u (= (:pos u) (first @atoms/cells-needing-attention)))))
      :ok

      (zero? n) :timeout

      @atoms/waiting-for-input
      (let [coords (first @atoms/cells-needing-attention)
            cell (get-in @atoms/game-map coords)
            k (if (= :city (:type cell)) :x :space)]
        (with-redefs [q/mouse-x (constantly 0)
                      q/mouse-y (constantly 0)]
          (reset! atoms/last-key nil)
          (input/key-down k))
        (game-loop/advance-game)
        (recur (dec n)))

      :else
      (do (game-loop/advance-game)
          (recur (dec n))))))

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

  (it "carrier.txt:18 - Sleep fighters on carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry)
    (set-test-unit atoms/game-map "C" :awake-fighters 2 :fighter-count 2)
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
      (input/key-down :s))
    (should= 0 (:awake-fighters (:unit (get-test-unit atoms/game-map "C"))))
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "C")))))

  (it "carrier.txt:30 - Carrier with awake fighters needs attention"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry)
    (set-test-unit atoms/game-map "C" :fighter-count 1 :awake-fighters 1)
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (should= :ok (advance-until-unit-waiting "C"))
    (should-contain "carrier" @atoms/attention-message))

  (it "carrier.txt:41 - Launch fighter from carrier"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C=~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry)
    (set-test-unit atoms/game-map "C" :awake-fighters 1 :fighter-count 1)
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
      (input/key-down :d))
    (dotimes [_ 1] (game-loop/advance-game))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))
          f-result (get-test-unit atoms/game-map "F")]
      (should-not-be-nil f-result)
      (should= target-pos (:pos f-result)))
    (should= 0 (:fighter-count (:unit (get-test-unit atoms/game-map "C")))))

  (it "carrier.txt:53 - Carrier attention message shows fighter count"
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

  (it "carrier.txt:64 - Damaged carrier attention message"
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
    (should-contain "Damaged" @atoms/attention-message))

  (it "carrier.txt:75 - Carrier speed is 2 per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C~~=~"]))
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
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "C")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "carrier.txt:86 - Carrier cannot move onto land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["C#"]))
    (set-test-unit atoms/game-map "C" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:ships-cant-drive-on-land config/messages))
    (should-contain (:ships-cant-drive-on-land config/messages) @atoms/attention-message)))
