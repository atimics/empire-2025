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
      ;; Note: This test must come before the destroyer escort tests
      ;; Patrol boat at [0 1], destroyer at [0 2] -- should move away to [0 0]
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :patrol-boat :owner :computer :hits 1
                                                       :patrol-country-id 1 :patrol-direction :clockwise
                                                       :patrol-mode :patrolling}}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 1] :patrol-boat)
      ;; Patrol boat should have fled to [0 0] (away from destroyer at [0 2])
      (should= :patrol-boat (get-in @atoms/game-map [0 0 :contents :type]))))

  (describe "destroyer escort behavior"
    (it "seeking destroyer adopts unadopted transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :transport-mission :loading
                                                       :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer should have adopted the transport and moved toward it
      (let [destroyer (first (for [c (range 4)
                                   :let [unit (get-in @atoms/game-map [0 c :contents])]
                                   :when (= :destroyer (:type unit))]
                               unit))
            transport (get-in @atoms/game-map [0 3 :contents])]
        (should= :intercepting (:escort-mode destroyer))
        (should= 1 (:escort-transport-id destroyer))
        (should= 1 (:escort-destroyer-id transport))))

    (it "intercepting destroyer transitions to escorting when adjacent"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :intercepting
                                                       :escort-transport-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should transition to escorting (already adjacent)
      (let [destroyer (get-in @atoms/game-map [0 0 :contents])]
        (should= :escorting (:escort-mode destroyer))))

    (it "escorting destroyer follows transport"
      ;; Destroyer at [0 0] escorting, transport at [0 2] (not adjacent)
      ;; Destroyer should move toward transport
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 1}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer :hits 3
                                                       :transport-id 1 :escort-destroyer-id 1
                                                       :transport-mission :loading :army-count 0}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should have moved toward transport
      (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type])))

    (it "destroyer reverts to seeking when transport is destroyed"
      ;; Destroyer escorting a transport that no longer exists
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3
                                                       :destroyer-id 1 :escort-mode :escorting
                                                       :escort-transport-id 99}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :destroyer)
      ;; Should revert to seeking
      (let [destroyer (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode destroyer))
        (should-be-nil (:escort-transport-id destroyer)))))

  (describe "carrier positioning behavior"
    (it "carrier in positioning mode moves toward target"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :positioning :carrier-target [0 3]}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :carrier)
      (should= :carrier (get-in @atoms/game-map [0 1 :contents :type])))

    (it "carrier transitions to holding when at target"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :positioning :carrier-target [0 0]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :carrier)
      (should= :holding (get-in @atoms/game-map [0 0 :contents :carrier-mode]))
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :carrier-target])))

    (it "carrier in holding mode stays put"
      (reset! atoms/game-map [[{:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-mode :holding}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :carrier)
      (should= :carrier (get-in @atoms/game-map [0 0 :contents :type]))
      (should= :holding (get-in @atoms/game-map [0 0 :contents :carrier-mode])))

    (it "positioning carrier without target finds valid position and moves"
      (let [cells (vec (concat [{:type :city :city-status :computer}
                                 {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                         :carrier-mode :positioning}}]
                                (repeat 38 {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (ship/process-ship [0 1] :carrier)
        ;; Carrier should have moved from [0,1] to [0,2]
        (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
        (should= :carrier (get-in @atoms/game-map [0 2 :contents :type]))
        ;; Should have a carrier-target in valid range [26, 32] from city
        (let [target (:carrier-target (get-in @atoms/game-map [0 2 :contents]))]
          (should-not-be-nil target)
          (should= 0 (first target))
          (should (>= (second target) 26))
          (should (<= (second target) 32))))))

  (describe "find-carrier-position"
    (it "finds valid position at correct spacing"
      (let [cells (vec (concat [{:type :city :city-status :computer}]
                                (repeat 39 {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [0 26] (ship/find-carrier-position))))

    (it "returns nil when no valid position exists"
      (let [cells (vec (concat [{:type :city :city-status :computer}]
                                (repeat 25 {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should-be-nil (ship/find-carrier-position))))

    (it "holding carrier counts as refueling site"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 26) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                             :carrier-mode :holding}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [0 52] (ship/find-carrier-position)))))

  (describe "carrier group escort behavior"
    (it "seeking battleship adopts carrier with open slot"
      ;; Battleship at [0,0] seeking, carrier at [0,3] holding with no BB
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Battleship should have adopted carrier and moved toward it
      (let [bb (get-in @atoms/game-map [0 1 :contents])
            carrier (get-in @atoms/game-map [0 3 :contents])]
        (should= :battleship (:type bb))
        (should= :intercepting (:escort-mode bb))
        (should= 1 (:escort-carrier-id bb))
        (should= 1 (:group-battleship-id carrier))))

    (it "intercepting escort transitions to orbiting when at radius 2"
      ;; Battleship at [0,0], carrier at [0,2] (Chebyshev distance 2)
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :intercepting
                                                       :escort-carrier-id 1 :orbit-angle 0}}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id 1
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      ;; Should transition to orbiting
      (let [bb (get-in @atoms/game-map [0 0 :contents])]
        (should= :orbiting (:escort-mode bb))))

    (it "orbiting escort advances along ring"
      ;; 5x5 all-sea map. Carrier at [2,2], battleship at [0,0] (orbit angle 0 = [-2,-2])
      (let [game-map (build-test-map ["~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"
                                       "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :carrier :owner :computer :hits 8
                :carrier-id 1 :carrier-mode :holding
                :group-battleship-id 1 :group-submarine-ids []})
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :battleship :owner :computer :hits 8
                :escort-id 1 :escort-mode :orbiting
                :escort-carrier-id 1 :orbit-angle 0})
        (ship/process-ship [0 0] :battleship)
        ;; Should have moved from [0,0] to next orbit position
        ;; Orbit angle 0 = [-2,-2] = [0,0] relative to carrier at [2,2]
        ;; Next valid angle 1 = [-2,-1] = [0,1]
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :battleship (get-in @atoms/game-map [0 1 :contents :type]))
        (should= 1 (get-in @atoms/game-map [0 1 :contents :orbit-angle]))))

    (it "escort reverts to seeking when carrier is destroyed"
      ;; Battleship orbiting, no carrier on map
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8
                                                       :escort-id 1 :escort-mode :orbiting
                                                       :escort-carrier-id 99 :orbit-angle 3}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :battleship)
      (let [bb (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode bb))
        (should-be-nil (:escort-carrier-id bb))))

    (it "seeking submarine adopts carrier with open submarine slot"
      ;; Submarine at [0,0], carrier at [0,3] with 0 subs
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2
                                                       :escort-id 2 :escort-mode :seeking}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                       :carrier-id 1 :carrier-mode :holding
                                                       :group-battleship-id nil
                                                       :group-submarine-ids []}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (ship/process-ship [0 0] :submarine)
      (let [sub (get-in @atoms/game-map [0 1 :contents])
            carrier (get-in @atoms/game-map [0 3 :contents])]
        (should= :submarine (:type sub))
        (should= :intercepting (:escort-mode sub))
        (should= 1 (:escort-carrier-id sub))
        (should= [2] (:group-submarine-ids carrier)))))

  (describe "find-positioning-carrier-targets"
    (it "returns targets of positioning carriers"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 26]}}
                           (= j 10) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :positioning
                                                            :carrier-target [0 52]}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [[0 26] [0 52]] (vec (ship/find-positioning-carrier-targets)))))

    (it "excludes positioning carriers without target"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 26]}}
                           (= j 10) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :positioning}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [[0 26]] (vec (ship/find-positioning-carrier-targets)))))

    (it "excludes holding carriers"
      (let [cells (vec (for [j (range 60)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :holding}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [] (vec (ship/find-positioning-carrier-targets))))))

  (describe "carrier clustering fix"
    (it "positioning target treated as spacing point"
      ;; City at [0,0], positioning carrier targeting [0,26], holding carrier at [0,58].
      ;; Without fix: first valid is [0,26]. With fix: [0,26] blocked, result is [0,84].
      (let [cells (vec (for [j (range 120)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 26]}}
                           (= j 58) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :holding}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [0 84] (ship/find-carrier-position))))

    (it "positioning carrier without target has no effect"
      ;; Positioning carrier without :carrier-target should not affect results.
      (let [cells (vec (for [j (range 40)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [0 26] (ship/find-carrier-position))))

    (it "multiple positioning targets block multiple zones"
      ;; Two positioning carriers target [0,26] and [0,52]. Holding carrier at [0,84].
      ;; Result pushed to [0,110].
      (let [cells (vec (for [j (range 120)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 3) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 26]}}
                           (= j 7) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 52]}}
                           (= j 84) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                            :carrier-mode :holding}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should= [0 110] (ship/find-carrier-position))))

    (it "returns nil when positioning target blocks only valid zone"
      ;; Single city, narrow map. Only valid zone is [0,26] but positioning carrier targets it.
      (let [cells (vec (for [j (range 40)]
                         (cond
                           (= j 0) {:type :city :city-status :computer}
                           (= j 5) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                           :carrier-mode :positioning
                                                           :carrier-target [0 26]}}
                           :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (should-be-nil (ship/find-carrier-position))))))
