(ns acceptance.computer-production-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map build-sparse-test-map
                                       set-test-unit get-test-unit
                                       get-test-city reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.computer.production :as computer-production]))

(describe "computer-production.txt"

  ;; computer-production.txt:6 - Computer city with no country produces army when continent has free city.
  (it "computer-production.txt:6 - Computer city with no country produces army when continent has free city"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["X+"
                                            "##"]))
    ;; Computer needs a computer-map for continent flood-fill
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/computer-map (vec (for [c (range cols)]
                                        (vec (for [r (range rows)]
                                               (get-in @atoms/game-map [c r])))))))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is army
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))))

  ;; computer-production.txt:15 - Country produces army first when zero armies.
  (it "computer-production.txt:15 - Country produces army first when zero armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#"
                                            "##"]))
    ;; GIVEN cell [0 0] has country-id 1
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is army
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should= :army (:item prod))))

  ;; computer-production.txt:29 - Country stops armies at cap of 10.
  (it "computer-production.txt:29 - Country stops armies at cap of 10"
    (reset-all-atoms!)
    ;; GIVEN game map: X with 10 computer armies
    ;; "X#########" row 0, "aaaaaaaaaa" row 1
    (reset! atoms/game-map (build-test-map ["X#########"
                                            "aaaaaaaaaa"]))
    ;; GIVEN cell [0 0] has country-id 1
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    ;; GIVEN all computer armies have country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is not army
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should-not= :army (:item prod))))

  ;; computer-production.txt:41 - Country produces transport after 6 armies at coastal city.
  (it "computer-production.txt:41 - Country produces transport after 6 armies at coastal city"
    (reset-all-atoms!)
    ;; GIVEN game map: "~X" "aa" "aa" "aa"
    (reset! atoms/game-map (build-test-map ["~X"
                                            "aa"
                                            "aa"
                                            "aa"]))
    ;; GIVEN cell [1 0] has country-id 1
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; GIVEN all computer armies have country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is transport
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should= :transport (:item prod))))

  ;; computer-production.txt:55 - Country transport cap is 2.
  (it "computer-production.txt:55 - Country transport cap is 2"
    (reset-all-atoms!)
    ;; GIVEN game map: "~X~" "aaa" "aaa" "ttt"
    ;; This gives 6 armies and 3 transports. But we only need >=2 transports with country-id 1.
    (reset! atoms/game-map (build-test-map ["~X~"
                                            "aaa"
                                            "aaa"
                                            "ttt"]))
    ;; GIVEN cell [1 0] has country-id 1
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; GIVEN all computer armies have country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; GIVEN all computer transports have country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :transport (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is not transport
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should-not= :transport (:item prod))))

  ;; computer-production.txt:72 - Country produces patrol-boat at coastal city.
  ;; Priority order: transport > army > patrol-boat. Need transport cap (2) met and army cap (10) met.
  (it "computer-production.txt:72 - Country produces patrol-boat at coastal city"
    (reset-all-atoms!)
    ;; Build map with: coastal city X, 10 armies, 2 transports (all with country-id 1)
    (reset! atoms/game-map (build-sparse-test-map 12 3
                                                   {[0 0] \~  [0 1] \X  [0 2] \~
                                                    [1 0] \a  [1 1] \a  [1 2] \t
                                                    [2 0] \a  [2 1] \a  [2 2] \t
                                                    [3 0] \a  [3 1] \a
                                                    [4 0] \a  [4 1] \a
                                                    [5 0] \a  [5 1] \a}))
    ;; Set country-id on the city
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; Set country-id on all computer armies and transports
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit))
                         (#{:army :transport} (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is patrol-boat
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should= :patrol-boat (:item prod))))

  ;; computer-production.txt:85 - Country patrol-boat cap is 1.
  (it "computer-production.txt:85 - Country patrol-boat cap is 1"
    (reset-all-atoms!)
    ;; GIVEN game map: "~X" "p#"
    (reset! atoms/game-map (build-test-map ["~X"
                                            "p#"]))
    ;; GIVEN cell [1 0] has country-id 1
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; GIVEN 10 computer armies with country-id 1 exist
    ;; Expand the map to accommodate 10 armies.
    (reset! atoms/game-map (build-sparse-test-map 12 2
                                                   {[0 0] \~  [0 1] \X
                                                    [1 0] \p  [1 1] \#
                                                    [2 0] \a  [2 1] \a
                                                    [3 0] \a  [3 1] \a
                                                    [4 0] \a  [4 1] \a
                                                    [5 0] \a  [5 1] \a
                                                    [6 0] \a  [6 1] \a}))
    ;; Set country-id on the city and all armies
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; GIVEN p has patrol-country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :patrol-boat (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :patrol-country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is not patrol-boat
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should-not= :patrol-boat (:item prod))))

  ;; computer-production.txt:100 - Country produces fighter when other needs met.
  (it "computer-production.txt:100 - Country produces fighter when other needs met"
    (reset-all-atoms!)
    ;; GIVEN game map: "X#" "##" — inland city, country-id 1, 10+ armies
    (reset! atoms/game-map (build-sparse-test-map 12 2
                                                   {[0 0] \X  [0 1] \#
                                                    [1 0] \a  [1 1] \a
                                                    [2 0] \a  [2 1] \a
                                                    [3 0] \a  [3 1] \a
                                                    [4 0] \a  [4 1] \a
                                                    [5 0] \a  [5 1] \a}))
    ;; GIVEN cell [0 0] has country-id 1
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    ;; GIVEN 10 computer armies with country-id 1 exist
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is fighter
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should= :fighter (:item prod))))

  ;; computer-production.txt:113 - Country fighter cap is 2.
  (it "computer-production.txt:113 - Country fighter cap is 2"
    (reset-all-atoms!)
    ;; GIVEN game map: "X#" "ff" — city with 2 fighters, 10+ armies
    (reset! atoms/game-map (build-sparse-test-map 12 2
                                                   {[0 0] \X  [0 1] \#
                                                    [1 0] \f  [1 1] \f
                                                    [2 0] \a  [2 1] \a
                                                    [3 0] \a  [3 1] \a
                                                    [4 0] \a  [4 1] \a
                                                    [5 0] \a  [5 1] \a
                                                    [6 0] \a  [6 1] \a}))
    ;; GIVEN cell [0 0] has country-id 1
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    ;; GIVEN 10 computer armies with country-id 1 exist
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; GIVEN all computer fighters have country-id 1
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :fighter (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; WHEN computer city at X is processed
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (computer-production/process-computer-city city-coords))
    ;; THEN production at X is not fighter (cap of 2 reached)
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should-not= :fighter (:item prod))))

  ;; computer-production.txt:126 - Carrier needs more than 10 cities.
  ;; This test requires 11 computer cities and a coastal city.
  ;; We use build-sparse-test-map to create a large map with the needed cities.
  (it "computer-production.txt:126 - Carrier needs more than 10 cities"
    (reset-all-atoms!)
    ;; Build a map with 11 computer cities. The coastal city X is at [0 0] with sea at [0 1].
    ;; Place 10 more computer cities elsewhere on the map.
    ;; Also need: country-id 1, 10+ armies, 2+ fighters (to satisfy per-country needs).
    (let [overlays (merge
                     {[0 0] \~ [0 1] \X}            ;; Coastal city at col 1, row 0
                     ;; 10 more computer cities at rows 2-11, col 0
                     (into {} (for [r (range 2 12)] [[r 0] \X]))
                     ;; 10 armies at rows 2-11, col 1
                     (into {} (for [r (range 2 12)] [[r 1] \a])))]
      (reset! atoms/game-map (build-sparse-test-map 14 3 overlays)))
    ;; Set country-id on the coastal city and all armies
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (let [game-map @atoms/game-map
          cols (count game-map)
          rows (count (first game-map))]
      (doseq [c (range cols)
              r (range rows)
              :let [cell (get-in game-map [c r])
                    unit (:contents cell)]
              :when (and unit (= :computer (:owner unit)) (= :army (:type unit)))]
        (swap! atoms/game-map assoc-in [c r :contents :country-id] 1)))
    ;; Need fighters at cap (2) so fighter production is skipped, reaching global production
    ;; Also need patrol-boat at cap (1) so patrol-boat is skipped
    ;; For simplicity, set country-id on the coastal city to trigger country-based decisions,
    ;; then ensure all per-country needs are met (10 armies, 2 fighters, 1 patrol)
    ;; But carrier decision is in decide-global-production which runs after per-country.
    ;; We need: 10+ armies, 2 fighters, 1 patrol-boat, 2 transports — all with country-id 1.
    ;; This is complex. A simpler approach: city without country-id triggers non-country then global.
    ;; Remove country-id from city so it goes through non-country path (army cap met) then global.
    (swap! atoms/game-map update-in [1 0] dissoc :country-id)
    ;; WHEN computer city at coastal X is processed
    ;; The first X found by get-test-city will be at [1 0] (the coastal one after sorting)
    ;; Actually get-test-city scans column-major, so it finds [0 2] first.
    ;; Let's use the coastal city coordinates directly.
    (let [city-coords [1 0]]
      ;; Mock find-carrier-position to return a valid position
      (with-redefs [empire.computer.ship/find-carrier-position (constantly [0 0])]
        (computer-production/process-computer-city city-coords))
      ;; THEN production at X is carrier
      (let [prod (get @atoms/production city-coords)]
        (should= :carrier (:item prod)))))

  ;; computer-production.txt:134 - Carrier blocked under 10 cities.
  (it "computer-production.txt:134 - Carrier blocked under 10 cities"
    (reset-all-atoms!)
    ;; Build a map with 5 computer cities and a coastal city.
    (let [overlays (merge
                     {[0 0] \~ [0 1] \X}        ;; Coastal city
                     (into {} (for [r (range 2 6)] [[r 0] \X]))  ;; 4 more cities
                     (into {} (for [r (range 2 6)] [[r 1] \a]))) ;; 4 armies
          ]
      (reset! atoms/game-map (build-sparse-test-map 8 3 overlays)))
    ;; WHEN computer city at coastal X is processed
    (let [city-coords [1 0]]
      (with-redefs [empire.computer.ship/find-carrier-position (constantly [0 0])]
        (computer-production/process-computer-city city-coords))
      ;; THEN production at X is not carrier (only 5 cities, need >10)
      (let [prod (get @atoms/production city-coords)]
        (should-not= :carrier (:item prod)))))

  ;; computer-production.txt:144 - Satellite needs more than 15 cities.
  (it "computer-production.txt:144 - Satellite needs more than 15 cities"
    (reset-all-atoms!)
    ;; Build a map with 16 computer cities and an inland city.
    ;; Inland city: surrounded by land, no sea neighbors.
    (let [overlays (merge
                     {[0 0] \X [0 1] \#}         ;; Inland city at [0 0]
                     ;; 15 more computer cities
                     (into {} (for [r (range 2 17)] [[r 0] \X]))
                     ;; 10 armies for the country
                     (into {} (for [r (range 2 12)] [[r 1] \a])))]
      (reset! atoms/game-map (build-sparse-test-map 20 3 overlays)))
    ;; Set country-id on city and armies, then ensure per-country needs are met
    ;; City without country-id goes through non-country then global path
    ;; WHEN computer city at inland X is processed
    (let [city-coords [0 0]]
      (computer-production/process-computer-city city-coords)
      ;; THEN production at X is satellite
      (let [prod (get @atoms/production city-coords)]
        (should= :satellite (:item prod)))))

  ;; computer-production.txt:153 - Satellite cap is 1.
  (it "computer-production.txt:153 - Satellite cap is 1"
    (reset-all-atoms!)
    ;; Build a map with 16 computer cities, an inland city, and 1 satellite.
    (let [overlays (merge
                     {[0 0] \X [0 1] \#}         ;; Inland city
                     (into {} (for [r (range 2 17)] [[r 0] \X]))   ;; 15 more cities
                     (into {} (for [r (range 2 12)] [[r 1] \a]))   ;; 10 armies
                     {[12 1] \v})]                ;; 1 computer satellite
      (reset! atoms/game-map (build-sparse-test-map 20 3 overlays)))
    ;; WHEN computer city at inland X is processed
    (let [city-coords [0 0]]
      (computer-production/process-computer-city city-coords)
      ;; THEN production at X is not satellite (cap of 1 reached)
      (let [prod (get @atoms/production city-coords)]
        (should-not= :satellite (:item prod)))))

  ;; computer-production.txt:163 - Computer production does not repeat.
  (it "computer-production.txt:163 - Computer production does not repeat"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["X#"]))
    ;; GIVEN production at X is army with 1 round remaining
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (swap! atoms/production assoc city-coords {:item :army :remaining-rounds 1}))
    ;; WHEN a new round starts
    (game-loop/start-new-round)
    ;; THEN there is an a at [0 0]
    (let [{:keys [pos]} (get-test-unit atoms/game-map "a")]
      (should= [0 0] pos))
    ;; THEN there is no production at X (computer production dissoc'd after spawn)
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))
          prod (get @atoms/production city-coords)]
      (should (or (nil? prod) (= :none prod))))))
