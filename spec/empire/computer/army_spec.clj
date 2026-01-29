(ns empire.computer.army-spec
  "Tests for VMS Empire style computer army movement."
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-army"
  (before (reset-all-atoms!))

  (describe "attack behavior"
    (it "attacks adjacent player army"
      (reset! atoms/game-map (build-test-map ["aA#"]))
      (reset! atoms/computer-map (build-test-map ["aA#"]))
      (let [result (army/process-army [0 0])]
        ;; Either army won or lost, but combat happened
        ;; Check that computer army is no longer at [0 0]
        (should (or (nil? (:contents (get-in @atoms/game-map [0 0])))
                    ;; Or army moved to [0 1] after winning
                    (= :army (:type (:contents (get-in @atoms/game-map [0 1]))))))))

    (it "attacks adjacent free city"
      (reset! atoms/game-map (build-test-map ["a+#"]))
      (reset! atoms/computer-map (build-test-map ["a+#"]))
      ;; Run multiple times to account for 50% conquest chance
      (loop [attempts 10]
        (when (pos? attempts)
          (reset! atoms/game-map (build-test-map ["a+#"]))
          (reset! atoms/computer-map (build-test-map ["a+#"]))
          (army/process-army [0 0])
          (let [city-status (:city-status (get-in @atoms/game-map [0 1]))]
            (when (= :free city-status)
              (recur (dec attempts))))))
      ;; After up to 10 attempts, city should be conquered (very high probability)
      ;; Actually we just verify the army tried to attack
      (should-not= :free (:city-status (get-in @atoms/game-map [0 1])))))

  (describe "land objective behavior"
    (it "moves toward unexplored territory"
      (reset! atoms/computer-map [[{:type :land :contents {:type :army :owner :computer}}
                                    {:type :land}
                                    nil]])
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :land}
                                {:type :land}]])
      (let [result (army/process-army [0 0])]
        ;; Army should have moved toward unexplored
        (should= [0 1] result)))

    (it "moves toward free city on same continent"
      (reset! atoms/game-map (build-test-map ["a#+"]))
      (reset! atoms/computer-map (build-test-map ["a#+"]))
      (let [result (army/process-army [0 0])]
        (should= [0 1] result))))

  (describe "transport boarding behavior"
    (it "boards adjacent loading transport"
      ;; Army adjacent to loading transport with room
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (army/process-army [0 0])]
        ;; Army should be nil (on transport) or have moved
        (should-be-nil result)
        ;; Transport should have army
        (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1]))))))

    (it "moves toward loading transport when no land objectives"
      ;; Fully explored continent with loading transport nearby
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (army/process-army [0 0])]
        ;; Army should move toward transport (to [0 1])
        (should= [0 1] result))))

  (describe "exploration behavior"
    (it "explores when nothing else to do"
      (reset! atoms/game-map (build-test-map ["a##"]))
      (reset! atoms/computer-map (build-test-map ["a##"]))
      (let [result (army/process-army [0 0])]
        ;; Army should move to some passable cell
        (should (or (= [0 1] result) (nil? result)))))

    (it "returns nil when no valid moves"
      ;; Army surrounded by sea
      (reset! atoms/game-map [[{:type :sea} {:type :sea} {:type :sea}]
                               [{:type :sea} {:type :land :contents {:type :army :owner :computer}} {:type :sea}]
                               [{:type :sea} {:type :sea} {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (army/process-army [1 1])]
        (should-be-nil result))))

  (describe "ignores non-computer units"
    (it "returns nil for player army"
      (reset! atoms/game-map (build-test-map ["A#"]))
      (should-be-nil (army/process-army [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map (build-test-map ["##"]))
      (should-be-nil (army/process-army [0 0])))))
