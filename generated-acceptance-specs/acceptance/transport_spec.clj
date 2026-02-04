(ns acceptance.transport-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-cell get-test-city reset-all-atoms! message-matches? make-initial-test-map]]
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

(describe "transport.txt"

  (it "transport.txt:6 - Transport produced at player city is awake"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"]))
    (let [o-pos (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc o-pos {:item :transport :remaining-rounds 1}))
    (game-loop/advance-game)
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "T"))))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "T")))))

  (it "transport.txt:18 - Transport does not have transport-mission after creation"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["T~"]))
    (should-be-nil (:transport-mission (:unit (get-test-unit atoms/game-map "T")))))

  (it "transport.txt:26 - Wake armies command puts transport to sentry and wakes armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~" "T#" "~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :army-count 2)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :u))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "T"))))
    (should= 2 (:army-count (:unit (get-test-unit atoms/game-map "T")))))

  (it "transport.txt:41 - Disembarking army removes it from transport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~" "T#" "~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :army-count 3)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :u))
    (should= :ok (advance-until-unit-waiting "T"))
    (input/handle-key :d)
    (should= 2 (:army-count (:unit (get-test-unit atoms/game-map "T"))))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [1 1] pos)))

  (it "transport.txt:57 - Transport wakes when last army disembarks"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~" "T#" "~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :army-count 1)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :u))
    (should= :ok (advance-until-unit-waiting "T"))
    (input/handle-key :d)
    (should= :awake (:mode (:unit (get-test-unit atoms/game-map "T"))))
    (should= 0 (:army-count (:unit (get-test-unit atoms/game-map "T")))))

  (it "transport.txt:73 - Transport has 1 hit"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["T~"]))
    (should= 1 (:hits (:unit (get-test-unit atoms/game-map "T")))))

  (it "transport.txt:81 - Transport moves 2 cells per round"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["T~~="]))
    (set-test-unit atoms/game-map "T" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :D))
    (should= :ok (advance-until-next-round))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "T")
          target-pos (:pos (get-test-cell atoms/game-map "="))]
      (should= target-pos pos)))

  (it "transport.txt:92 - Transport blocked by land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["T#"]))
    (set-test-unit atoms/game-map "T" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:ships-cant-drive-on-land config/messages))
    (should (message-matches? (:ships-cant-drive-on-land config/messages) @atoms/attention-message)))

  (it "transport.txt:103 - Transport blocked by friendly ship"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["TD~"]))
    (set-test-unit atoms/game-map "T" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (game-loop/advance-game)
    (should-not-be-nil (:somethings-in-the-way config/messages))
    (should (message-matches? (:somethings-in-the-way config/messages) @atoms/attention-message)))

  (it "transport.txt:114 - Transport sentry mode"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["T~"]))
    (set-test-unit atoms/game-map "T" :mode :awake)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (reset! atoms/last-key nil)
      (input/key-down :s))
    (should= :sentry (:mode (:unit (get-test-unit atoms/game-map "T"))))))
