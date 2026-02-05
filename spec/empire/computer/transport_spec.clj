(ns empire.computer.transport-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.production :as production]
            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
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

    (it "does not pick up armies on non-origin continent"
      ;; Two continents: origin (rows 0-1) and other (rows 4-5).
      ;; Armies only on the other continent. Transport in loading mode
      ;; with pickup-continent-pos set to origin continent.
      ;; Transport should NOT target those armies.
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "~t~"
                                      "~~~"
                                      "a#a"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 2])
        ;; Transport should NOT have moved toward armies on the other continent.
        ;; With no armies on origin continent, it should fall back to explore-sea
        ;; or stay put. It should NOT pick up the non-origin armies.
        (let [transport-pos (first (for [r (range 6) c (range 3)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))]
          ;; Armies on other continent should still be there (not picked up)
          (should= :army (get-in @atoms/game-map [0 4 :contents :type]))
          (should= :army (get-in @atoms/game-map [2 4 :contents :type]))
          ;; Transport should not have moved toward row 4
          (should (< (second transport-pos) 4)))))

    (it "picks up armies on origin continent"
      ;; Origin continent (rows 0-1) has an army. Transport in loading mode
      ;; with pickup-continent-pos set. Should move toward that army.
      (let [game-map (build-test-map ["a##"
                                      "~t~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 1])
        ;; Transport should have moved toward the army on origin continent
        (should= :transport (get-in @atoms/game-map [0 1 :contents :type]))))

    (it "picks up any army when no pickup-continent-pos"
      ;; No pickup-continent-pos set — first load cycle.
      ;; Should find nearest army regardless of continent.
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "~t~"
                                      "~~~"
                                      "a##"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0})
        (transport/process-transport [1 2])
        ;; Transport should have moved toward the army (south)
        (let [transport-pos (first (for [r (range 5) c (range 3)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))]
          (should (> (second transport-pos) 2)))))

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
                                   :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                               [c r]))]
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
    (it "records pickup-continent-pos when transport becomes full"
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
                        (let [contents (get-in @atoms/game-map [c r :contents])]
                          (when (= :transport (:type contents)) contents)))
                      (for [r (range 5) c (range 3)] [c r]))]
          (should-not-be-nil (:pickup-continent-pos t)))))

    (it "updates pickup-continent-pos after full unload to nearest qualifying continent"
      ;; Two continents: unload continent (rows 0-1) and army continent (rows 4-5).
      ;; Army continent has >3 computer armies.
      ;; After unloading, transport should update pickup-continent-pos to army continent.
      (let [game-map (build-test-map ["##~"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "aaa"
                                      "a##"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [2 5]})
        (transport/process-transport [1 1])
        (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :loading (:transport-mission transport))
          ;; pickup-continent-pos should be on the army continent (rows 4-5), not old value
          (should (>= (second (:pickup-continent-pos transport)) 4)))))

    (it "sets pickup-continent-pos to nil when no continent has >3 armies"
      ;; Only 2 armies exist on one continent - below threshold
      (let [game-map (build-test-map ["##~"
                                      "~t~"
                                      "~~~"
                                      "a#a"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [1 4]})
        (transport/process-transport [1 1])
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :loading (:transport-mission transport))
          (should-be-nil (:pickup-continent-pos transport)))))

    (it "excludes unload continent when finding next pickup"
      ;; Transport at [2,0] (sea), unload continent (rows 0-1), army continent (rows 4-5)
      ;; Unload continent also has armies, but it should be excluded.
      (let [game-map (build-test-map ["aaaa#"
                                      "a####"
                                      "t~~~~"
                                      "~~~~~"
                                      "aaaa#"
                                      "a####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [4 5]})
        (transport/process-transport [0 2])
        (let [transport-pos (first (for [r (range 6) c (range 5)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should= :loading (:transport-mission transport))
          ;; pickup-continent-pos should be on the OTHER army continent (rows 4-5),
          ;; not on the unload continent (rows 0-1)
          (should (>= (second (:pickup-continent-pos transport)) 4))))))

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
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 3])]
          ;; Should return the city on continent B, not continent A
          (should-not-be-nil target)
          (should= [0 5] target))))

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
                :pickup-continent-pos [0 0]})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])]
          (transport/unload-armies [1 1] pickup-continent)
          ;; Armies should NOT appear on origin continent (col 0)
          (let [origin-armies (count (for [r (range 3)
                                          :let [cell (get-in @atoms/game-map [0 r])]
                                          :when (= :army (:type (:contents cell)))]
                                      true))
                other-armies (count (for [r (range 3)
                                         :let [cell (get-in @atoms/game-map [2 r])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
            (should= 0 origin-armies)
            (should (pos? other-armies)))))))

  (describe "coastline exploration priority"
    (it "idle transport moves toward unexplored coastline over open sea"
      ;; Known land at row 4, unexplored cells near it. Also unexplored open sea at [0,2].
      ;; Transport should prefer coastline frontier (near row 3-4) over open sea [0,2].
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~~~"
                                              "~t~"
                                              "~~~"
                                              "###"]))
      (reset! atoms/computer-map [[nil {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} nil nil]])
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [1 2])
      ;; Transport should have moved toward the coastline, not toward [0,0]
      (let [transport-pos (first (for [r (range 5) c (range 3)
                                       :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                   [c r]))]
        (should-not-be-nil transport-pos)
        ;; Should move south toward coastline
        (should (>= (second transport-pos) 2))))

    (it "falls back to open sea when no coastline frontier"
      ;; No known land, only unexplored open sea
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~t~"
                                              "~~~"]))
      (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} nil]])
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [1 1])
      ;; Transport should still explore toward the unexplored cell
      (let [transport-pos (first (for [r (range 3) c (range 3)
                                       :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                   [c r]))]
        (should-not-be-nil transport-pos)
        (should-not= [1 1] transport-pos))))

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
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 3])
        ;; Transport should stay put - no unexplored territory
        (should= :transport (get-in @atoms/game-map [1 3 :contents :type]))))

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
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 3])
        ;; Transport should have moved toward unexplored
        (should-be-nil (:contents (get-in @atoms/game-map [1 3])))
        (let [moved (first (for [r (range 5) c (range 3)
                                 :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                             [c r]))]
          (should-not-be-nil moved)
          ;; Should move south toward unexplored, not north
          (should (>= (second moved) 3)))))

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
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 2])
        ;; No armies should be unloaded onto the origin continent
        (let [armies-on-land (count (for [r (range 2) c (range 3)
                                         :let [cell (get-in @atoms/game-map [c r])]
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
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [1 2])
              target2 (transport/find-unload-target pickup-continent [1 3])]
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
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [2 1])
              target2 (transport/find-unload-target pickup-continent [3 1])]
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
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [2 1])]
          ;; Should pick the city on continent without computer presence
          (should= [0 6] target))))

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
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              ;; Transport at row 1 is closer to city at row 2 than row 7
              target (transport/find-unload-target pickup-continent [1 1])]
          (should= [0 2] target)))))

  (describe "unload target persistence"
    (it "transport reuses stored unload-target-city"
      ;; Two player cities on different continents. Transport has stored target [10,0].
      ;; Even though [5,0] is closer, transport should use stored target.
      ;; Transport at [3,2] with no adjacent land (surrounded by sea).
      (let [game-map (build-test-map ["X####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "O####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "O####"
                                      "#####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]
                :unload-target-city [0 10]})
        (transport/process-transport [3 2])
        ;; Transport should still have [0,10] as its unload-target-city (not re-picked)
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should= [0 10] (:unload-target-city transport)))))

    (it "transport re-evaluates when stored target is computer-owned"
      ;; Stored target city is now computer-owned — should re-pick a valid target.
      ;; Transport at [3,2] with no adjacent land.
      (let [game-map (build-test-map ["X####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "X####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "O####"
                                      "#####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]
                :unload-target-city [0 5]})  ;; now computer-owned
        (transport/process-transport [3 2])
        ;; Transport should have re-picked to [0,10] (the only player city)
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should= [0 10] (:unload-target-city transport)))))

    (it "unload-target-city cleared when transport transitions to loading"
      ;; Transport with 1 army unloads completely, transitioning to loading.
      ;; The stored unload-target-city should be cleared.
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :unload-target-city [0 0]}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport))
        (should-be-nil (:unload-target-city transport)))))

  (describe "find-unload-position bias fix"
    (it "picks sea cell closest to transport, not NW-biased"
      ;; Target city at [3,0]. Only row 1 has sea adjacent to land.
      ;; Transport at [6,2] - closest candidate is [6,1] (distance 1), not [0,1].
      (let [game-map (build-test-map ["###O###"
                                      "~~~~~~~"
                                      "~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [6 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6})
        (let [result (#'empire.computer.transport/find-unload-position [3 0] nil [6 2])]
          ;; Should pick [6,1] (distance 1) not [0,1] (distance 7)
          (should= [6 1] result)))))

  (describe "global BFS unload (VMS-consistent)"
    (it "transport targets continent with enemy city via global BFS"
      ;; Origin continent at row 0 (computer). Row 6 land has NO city.
      ;; Player city at row 10 - transport should target row 10's continent.
      ;; Map is 5 cols wide so sea channels exist around intermediate land.
      (let [game-map (build-test-map ["X##~~"
                                      "###~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~###"
                                      "~~###"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~O##"
                                      "~~###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [3 2])
        ;; Transport should have moved (not stuck)
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))]
          (should-not-be-nil transport-pos)
          (should-not= [3 2] transport-pos))))

    (it "transport ignores continent with only computer cities"
      ;; Origin at row 0. Computer city at row 6 (shifted right). Player city at row 10.
      ;; Transport should head toward row 10 (player), not row 6 (computer).
      ;; Extra sea rows ensure transport isn't adjacent to computer city land.
      (let [game-map (build-test-map ["X##~~"
                                      "###~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~X#"
                                      "~~~##"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~O##"
                                      "~~###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [3 2])
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))]
          (should-not-be-nil transport-pos)
          ;; Should move south toward player city (row 10), not stay near computer city (row 6)
          (should (> (second transport-pos) 2)))))

    (it "transport explores when no enemy cities visible"
      ;; Only origin continent and computer cities exist. No player/free cities.
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
                :pickup-continent-pos [0 1]})
        (transport/process-transport [2 1])
        ;; Transport should have moved (explore fallback), not stuck
        (let [transport-pos (first (for [r (range 5) c (range 3)
                                        :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                    [r c]))]
          (should-not-be-nil transport-pos)
          (should-not= [2 1] transport-pos)))))

  (describe "no-reload from recently unloaded country"
    (it "transport avoids armies from recently unloaded country"
      ;; Army country-1 at [0,0], army country-2 at [0,9]. Transport at [1,3].
      ;; Without filtering, transport would pick [0,0] (closer, distance 4).
      ;; With country-1 filtered, transport should move right toward [0,9] (distance 7).
      (reset! atoms/round-number 10)
      (let [cells (vec (repeat 10 {:type :sea}))]
        (reset! atoms/game-map [(assoc cells
                                       0 {:type :land :contents {:type :army :owner :computer :country-id 1}}
                                       9 {:type :land :contents {:type :army :owner :computer :country-id 2}})
                                cells])
        (reset! atoms/computer-map @atoms/game-map)
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :unloaded-countries {1 5}})
        (transport/process-transport [1 3])
        ;; Transport should have moved right (toward country-2 army at [0,9])
        (let [transport-pos (first (for [r (range 2) c (range 10)
                                         :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                     [r c]))]
          (should-not-be-nil transport-pos)
          (should (> (second transport-pos) 3)))))

    (it "exclusion expires after 10 rounds"
      ;; Same setup but round 20 (15 rounds since unload at round 5 - expired).
      ;; Country-1 army at [0,0] is now the closest candidate again.
      (reset! atoms/round-number 20)
      (let [cells (vec (repeat 10 {:type :sea}))]
        (reset! atoms/game-map [(assoc cells
                                       0 {:type :land :contents {:type :army :owner :computer :country-id 1}}
                                       9 {:type :land :contents {:type :army :owner :computer :country-id 2}})
                                cells])
        (reset! atoms/computer-map @atoms/game-map)
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :unloaded-countries {1 5}})
        (transport/process-transport [1 3])
        ;; After expiration, transport should move left toward closer army at [0,0]
        (let [transport-pos (first (for [r (range 2) c (range 10)
                                         :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                     [r c]))]
          (should-not-be-nil transport-pos)
          (should (< (second transport-pos) 3)))))

    (it "armies with no country-id are not filtered"
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 2])
      ;; Should move toward army (no country-id, not filtered)
      (let [transport-pos (first (for [c (range 4)
                                       :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                                   [0 c]))]
        (should= [0 1] transport-pos)))

    (it "records unloaded country-id on unload"
      (reset! atoms/round-number 15)
      (reset! atoms/game-map [[{:type :land :country-id 3}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= 15 (get-in transport [:unloaded-countries 3]))))

    (it "transport falls back to explore when all armies filtered"
      (reset! atoms/round-number 10)
      (let [game-map (build-test-map ["a~a"
                                      "~t~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["a~a"
                                                    "~t~"
                                                    "~~-"]))
        (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
        (swap! atoms/game-map assoc-in [0 2 :contents :country-id] 1)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :unloaded-countries {1 5}})
        (transport/process-transport [1 1])
        (let [transport-pos (first (for [r (range 3) c (range 3)
                                         :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                     [r c]))]
          (should-not-be-nil transport-pos)
          (should-not= [1 1] transport-pos)))))

  (describe "unload-event-id filtering"
    (it "find-nearest-army skips armies with matching unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 42)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should-be-nil result))))

    (it "find-nearest-army finds armies with different unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 99)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should= [0 0] result))))

    (it "find-nearest-army finds armies with no unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should= [0 0] result))))

    (it "mint-unload-event-id always mints new ID even when one exists"
      (reset-all-atoms!)
      (reset! atoms/next-unload-event-id 100)
      (let [game-map (build-test-map ["t~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 42)
        (let [transport (get-in @atoms/game-map [0 0 :contents])]
          (#'transport/mint-unload-event-id [0 0] transport)
          (should= 100 (get-in @atoms/game-map [0 0 :contents :unload-event-id]))
          (should= 101 @atoms/next-unload-event-id)))))

  (describe "stuck transport scuttle"
    (it "transport scuttles after 10 rounds stuck"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :stuck-since-round 10}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be gone (scuttled)
      (should-be-nil (:contents (get-in @atoms/game-map [0 1]))))

    (it "unloads armies to adjacent land on scuttle"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 2
                                                        :stuck-since-round 10}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be gone
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      ;; Army should be on land
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0])))))

    (it "marks producing city as landlocked"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :stuck-since-round 10
                                                        :produced-at [0 0]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; City should be marked landlocked
      (should (:landlocked (get-in @atoms/game-map [0 0]))))

    (it "landlocked city produces no ships"
      (reset! atoms/game-map (build-test-map ["~X+"]))
      (reset! atoms/computer-map (build-test-map ["~X+"]))
      (swap! atoms/game-map assoc-in [0 1 :landlocked] true)
      (should-not (production/city-is-coastal? [0 1])))

    (it "stuck counter resets on move"
      ;; Transport that is not stuck (stuck-since-round was 5 rounds ago)
      (reset! atoms/round-number 10)
      (let [cells (vec (repeat 5 {:type :sea}))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} nil]])
        (swap! atoms/game-map assoc-in [0 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :stuck-since-round 8})
        (transport/process-transport [0 2])
        ;; Transport moved, stuck-since-round should be reset
        (let [transport-pos (first (for [c (range 5)
                                         :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                                     [0 c]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should-not-be-nil transport)
          (should= 10 (:stuck-since-round transport)))))

    (it "transport spawned with stuck-since-round and produced-at"
      ;; Test that new transports get stuck tracking fields
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/production {[0 0] {:item :transport :remaining-rounds 1}})
      ;; Run production to spawn the transport
      (player-prod/update-production)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :transport (:type unit))
        (should= 5 (:stuck-since-round unit))
        (should= [0 0] (:produced-at unit))))))
