(ns empire.computer.ship-spec
  "Tests for VMS Empire style computer ship movement."
  (:require [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-unit]]))

(describe "process-ship"
  (before (reset-all-atoms!))

  (describe "attack behavior"
    (it "attacks adjacent player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [_result (ship/process-ship [0 0] :destroyer)]
        ;; Combat should have occurred
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (describe "escort behavior"
    (it "destroyer moves toward transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have moved toward transport
      (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type]))))

  (describe "exploration behavior"
    (it "explores toward unexplored sea"
      (reset! atoms/computer-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                    {:type :sea}
                                    nil]])
      (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                {:type :sea}
                                {:type :sea}]])
      (ship/process-ship [0 0] :patrol-boat)
      ;; Ship should have moved toward unexplored
      (should= :patrol-boat (get-in @atoms/game-map [0 1 :contents :type])))

    (it "stays put when all sea is explored"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      ;; Ship stays put - no unexplored territory
      (should= :submarine (get-in @atoms/game-map [0 0 :contents :type])))

    (it "explores toward unexplored sea without NW bias"
      ;; 5x5 all-sea map. Ship at center, unexplored in SE corner.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~~"
                                                    "~~~~-"]))
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :destroyer :owner :computer :hits 3})
        (ship/process-ship [2 2] :destroyer)
        ;; Should have moved
        (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
        ;; Find where ship moved
        (let [new-pos (first (for [r (range 5) c (range 5)
                                   :when (= :destroyer (get-in @atoms/game-map [r c :contents :type]))]
                               [r c]))]
          ;; Should move toward SE, not NW
          (should-not= [1 1] new-pos)
          (should (or (> (first new-pos) 2)
                      (> (second new-pos) 2)))))))

  (describe "hunting behavior"
    (it "moves toward visible player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have moved toward player ship
      (should= :battleship (get-in @atoms/game-map [0 1 :contents :type]))))

  (describe "ignores non-computer ships"
    (it "returns nil for player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :destroyer)))

    (it "returns nil for wrong ship type"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :patrol-boat))))

  (describe "patrol boat behavior"
    (it "patrol boat moves along coastline"
      ;; 3x3 map: land in center, sea around it. Patrol boat at [0 1] (sea, adjacent to land).
      ;; It should move to another sea cell that is also adjacent to land.
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~#~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :patrol-boat :owner :computer :hits 1
              :patrol-country-id 1 :patrol-direction :clockwise :patrol-mode :patrolling})
      (ship/process-ship [0 1] :patrol-boat)
      ;; Patrol boat should have moved
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      ;; Find where it moved
      (let [new-pos (first (for [r (range 3) c (range 3)
                                 :when (= :patrol-boat (get-in @atoms/game-map [r c :contents :type]))]
                             [r c]))
            ;; The new position should be sea and adjacent to land [1 1]
            adj-to-land? (some (fn [[dr dc]]
                                 (let [nr (+ (first new-pos) dr)
                                       nc (+ (second new-pos) dc)]
                                   (= :land (:type (get-in @atoms/game-map [nr nc])))))
                               [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])]
        (should-not-be-nil new-pos)
        (should adj-to-land?)))

    (it "patrol boat attacks adjacent transport"
      ;; Patrol boat next to a player transport - should attack it
      (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-country-id 1 :patrol-direction :clockwise
                                                       :patrol-mode :patrolling}}
                                {:type :sea :contents {:type :transport :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :patrol-boat)
      ;; Combat should have occurred - either patrol boat moved to [0 1] or died
      (let [cell0 (get-in @atoms/game-map [0 0])
            cell1 (get-in @atoms/game-map [0 1])]
        (should (or (nil? (:contents cell0))
                    (= :computer (:owner (:contents cell1)))))))

    (it "patrol boat flees from non-transport enemy"
      ;; Patrol boat at [0 1], destroyer at [0 2] -- should move away to [0 0]
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-country-id 1 :patrol-direction :clockwise
                                                       :patrol-mode :patrolling}}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 1] :patrol-boat)
      ;; Patrol boat should have fled to [0 0] (away from destroyer at [0 2])
      (should= :patrol-boat (get-in @atoms/game-map [0 0 :contents :type])))))
