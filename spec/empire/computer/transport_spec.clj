(ns empire.computer.transport-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.continent :as continent]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (describe "loading behavior"
    (it "moves toward nearest army"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land :contents {:type :army :owner :computer}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Transport should have moved toward army (to [0 1])
      (should= :transport (get-in @atoms/game-map [0 1 :contents :type])))

    (it "stays put when loading with no armies and no unexplored territory"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Transport should stay put - no armies, no unexplored territory
      (should= :transport (get-in @atoms/game-map [0 0 :contents :type])))

    (it "explores toward unexplored territory when no armies"
      ;; 5x5 all-sea map. Transport at center, unexplored in SE corner.
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
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0})
        (transport/process-transport [2 2])
        ;; Transport should have moved
        (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
        ;; Find where transport moved
        (let [new-pos (first (for [r (range 5) c (range 5)
                                   :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                               [r c]))]
          ;; Should move toward SE (unexplored), not NW
          (should-not= [1 1] new-pos)
          (should (or (> (first new-pos) 2)
                      (> (second new-pos) 2)))))))

  (describe "unloading behavior"
    (it "unloads armies onto adjacent land"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Should have unloaded an army onto land
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
      ;; Transport should have fewer armies
      (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1])))))

    (it "changes to loading mode after full unload"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be in loading mode now
      (should= :loading (:transport-mission (:contents (get-in @atoms/game-map [0 1])))))

    (it "moves toward enemy city when full"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 6}}
                                {:type :sea}
                                {:type :sea}
                                {:type :land}
                                {:type :city :city-status :player}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Transport should have moved toward enemy city
      (should= :transport (get-in @atoms/game-map [0 1 :contents :type]))
      ;; Mission should change to unloading
      (should= :unloading (get-in @atoms/game-map [0 1 :contents :transport-mission]))))

  (describe "mission transitions"
    (it "sets idle transport to loading"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Should be in loading mode (stays put since no unexplored territory)
      (let [transport (:contents (get-in @atoms/game-map [0 0]))]
        (should= :loading (:transport-mission transport)))))

  (describe "ignores non-computer transports"
    (it "returns nil for player transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :player
                                                       :army-count 0}}]])
      (should-be-nil (transport/process-transport [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map [[{:type :sea}]])
      (should-be-nil (transport/process-transport [0 0]))))

  (describe "origin continent tracking"
    (it "records origin-continent-pos when transport becomes full"
      ;; Transport at [1,1] (sea) adjacent to land at [0,0..2]
      ;; Player city on separate continent at [4,1]
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~O~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        ;; Transport may have moved; find it
        (let [t (some (fn [[r c]]
                        (let [contents (get-in @atoms/game-map [r c :contents])]
                          (when (= :transport (:type contents)) contents)))
                      (for [r (range 5) c (range 3)] [r c]))]
          (should-not-be-nil (:origin-continent-pos t)))))

    (it "clears origin-continent-pos after full unload"
      (let [game-map (build-test-map ["#~"
                                      "~t"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :origin-continent-pos [5 5]})
        (transport/process-transport [1 1])
        (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :loading (:transport-mission transport))
          (should-be-nil (:origin-continent-pos transport))))))

  (describe "continent-aware unloading"
    (it "find-unload-target excludes origin continent cities"
      ;; Two continents separated by sea. Each has a player city.
      ;; Origin continent A (rows 0-1), continent B (rows 4-5)
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "###"
                                      "O##"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [origin-continent (continent/flood-fill-continent [0 0])
              target (transport/find-unload-target origin-continent [3 1])]
          ;; Should return the city on continent B, not continent A
          (should-not-be-nil target)
          (should= [5 0] target))))

    (it "unload-armies skips origin continent land"
      ;; Transport at [1,1] in 1-wide sea channel between two continents
      ;; Left continent (col 0), right continent (col 2)
      (let [game-map (build-test-map ["#~#"
                                      "#t#"
                                      "#~#"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 2
                :origin-continent-pos [0 0]})
        (let [origin-continent (continent/flood-fill-continent [0 0])]
          (transport/unload-armies [1 1] origin-continent)
          ;; Armies should NOT appear on origin continent (col 0)
          (let [origin-armies (count (for [r (range 3)
                                          :let [cell (get-in @atoms/game-map [r 0])]
                                          :when (= :army (:type (:contents cell)))]
                                      true))
                other-armies (count (for [r (range 3)
                                         :let [cell (get-in @atoms/game-map [r 2])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
            (should= 0 origin-armies)
            (should (pos? other-armies)))))))

  (describe "explore-sea fallback"
    (it "transport stays put when no target and no unexplored territory"
      ;; All visible cities on origin continent. No unexplored territory.
      ;; Transport should stay put per VMS behavior.
      (let [game-map (build-test-map ["O##"
                                      "#X#"
                                      "~~~"
                                      "~t~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :origin-continent-pos [0 1]})
        (transport/process-transport [3 1])
        ;; Transport should stay put - no unexplored territory
        (should= :transport (get-in @atoms/game-map [3 1 :contents :type]))))

    (it "transport explores toward unexplored sea when no cross-continent target"
      ;; All visible cities on origin continent. Unexplored sea to south.
      (let [game-map (build-test-map ["O##"
                                      "#X#"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        ;; Computer map has unexplored cells in bottom row
        (reset! atoms/computer-map (build-test-map ["O##"
                                                    "#X#"
                                                    "~~~"
                                                    "~~~"
                                                    "~--"]))
        (swap! atoms/game-map assoc-in [3 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :origin-continent-pos [0 1]})
        (transport/process-transport [3 1])
        ;; Transport should have moved toward unexplored
        (should-be-nil (:contents (get-in @atoms/game-map [3 1])))
        (let [moved (first (for [r (range 5) c (range 3)
                                 :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                             [r c]))]
          (should-not-be-nil moved)
          ;; Should move south toward unexplored, not north
          (should (>= (first moved) 3)))))

    (it "transport does NOT unload on same continent"
      ;; Integration test: full transport near origin continent, only city on origin continent
      ;; Transport should NOT unload, should explore instead
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :origin-continent-pos [0 1]})
        (transport/process-transport [2 1])
        ;; No armies should be unloaded onto the origin continent
        (let [armies-on-land (count (for [r (range 2) c (range 3)
                                         :let [cell (get-in @atoms/game-map [r c])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
          (should= 0 armies-on-land)))))

  (describe "transport target diversification"
    (it "two transports pick different cities"
      ;; Origin continent (rows 0-1), two target continents each with a player city
      ;; Continent B at row 4, Continent C at row 7
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [origin-continent (continent/flood-fill-continent [0 0])
              target1 (transport/find-unload-target origin-continent [2 1])
              target2 (transport/find-unload-target origin-continent [3 1])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should-not= target1 target2))))

    (it "falls back when all targets claimed"
      ;; Only one off-continent city - both transports must target it
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [origin-continent (continent/flood-fill-continent [0 0])
              target1 (transport/find-unload-target origin-continent [2 1])
              target2 (transport/find-unload-target origin-continent [3 1])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should= target1 target2))))

    (it "prefers continent without computer cities"
      ;; Two target continents: one with computer city, one without
      ;; Transport should prefer the one without computer presence
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "~~~"
                                      "O#X"  ;; continent with computer city
                                      "###"
                                      "~~~"
                                      "O##"  ;; continent without computer city
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [origin-continent (continent/flood-fill-continent [0 0])
              target (transport/find-unload-target origin-continent [2 1])]
          ;; Should pick the city on continent without computer presence
          (should= [6 0] target))))

    (it "prefers nearer target when continents are similar"
      ;; Two equidistant-ish continents, transport closer to one
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "O##"   ;; closer target
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "O##"   ;; farther target
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [origin-continent (continent/flood-fill-continent [0 0])
              ;; Transport at row 1 is closer to city at row 2 than row 7
              target (transport/find-unload-target origin-continent [1 1])]
          (should= [2 0] target)))))

  (describe "find-unload-position bias fix"
    (it "picks sea cell closest to transport, not NW-biased"
      ;; Target city at [0,3]. Only row 1 has sea adjacent to land.
      ;; Transport at [2,6] - closest candidate is [1,6] (distance 1), not [1,0].
      (let [game-map (build-test-map ["###O###"
                                      "~~~~~~~"
                                      "~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [2 6 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6})
        (let [result (#'empire.computer.transport/find-unload-position [0 3] nil [2 6])]
          ;; Should pick [1,6] (distance 1) not [1,0] (distance 7)
          (should= [1 6] result)))))

  (describe "global BFS unload (VMS-consistent)"
    (it "transport finds unload position far from any city via global BFS"
      ;; Origin continent at row 0. Target land at row 6 with no city nearby.
      ;; Player city far away at row 10. Old ±3 search would fail; global BFS succeeds.
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "###"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :origin-continent-pos [0 1]})
        (transport/process-transport [3 1])
        ;; Transport should have moved (not stuck)
        (let [transport-pos (first (for [r (range 12) c (range 3)
                                        :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                    [r c]))]
          (should-not-be-nil transport-pos)
          ;; Should have moved toward the nearest off-continent land (row 6)
          (should-not= [3 1] transport-pos))))

    (it "transport explores when no off-continent land reachable"
      ;; Only origin continent exists. No off-continent land at all.
      ;; Transport should fall back to explore-sea.
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        ;; Leave unexplored territory so explore-sea has somewhere to go
        (reset! atoms/computer-map (build-test-map ["X##"
                                                    "###"
                                                    "~~~"
                                                    "~~~"
                                                    "~~-"]))
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :origin-continent-pos [0 1]})
        (transport/process-transport [2 1])
        ;; Transport should have moved (explore fallback), not stuck
        (let [transport-pos (first (for [r (range 5) c (range 3)
                                        :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                    [r c]))]
          (should-not-be-nil transport-pos)
          ;; Should have moved toward unexplored territory
          (should-not= [2 1] transport-pos))))))
