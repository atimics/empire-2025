(ns empire.computer.production-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn- satisfy-inland-per-country
  "Stamp city with country-id and add 10 armies + 2 fighters to satisfy per-country priorities."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  (doseq [j [1 3 5 7 9 11 13 15 17 19]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :army :owner :computer :mode :awake :hits 1 :country-id 1}))
  (doseq [j [21 23]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})))

(defn- satisfy-coastal-per-country
  "Stamp coastal city with country-id and add units to satisfy all per-country priorities."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  (doseq [j [1 3 5 7 9 11 13 15 17 19]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :army :owner :computer :mode :awake :hits 1 :country-id 1}))
  (swap! atoms/game-map assoc-in [0 20 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  (swap! atoms/game-map assoc-in [0 21 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  (swap! atoms/game-map assoc-in [0 24 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 0 :hits 3})
  (swap! atoms/game-map assoc-in [0 26 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 2
          :escort-destroyer-id 2 :army-count 0 :hits 3})
  (swap! atoms/game-map assoc-in [0 28 :contents]
         {:type :patrol-boat :owner :computer :patrol-country-id 1 :hits 1}))

(describe "city-is-coastal?"
  (before (reset-all-atoms!))

  (it "returns true when city has adjacent sea"
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (should (production/city-is-coastal? [1 0])))

  (it "returns false when city has no adjacent sea"
    (reset! atoms/game-map (build-test-map ["#X#"]))
    (should-not (production/city-is-coastal? [1 0]))))

(describe "count-computer-units"
  (before (reset-all-atoms!))

  (it "counts computer units by type"
    (reset! atoms/game-map (build-test-map ["aad"]))
    (let [counts (production/count-computer-units)]
      (should= 2 (get counts :army))
      (should= 1 (get counts :destroyer))))

  (it "ignores player units"
    (reset! atoms/game-map (build-test-map ["aAD"]))
    (let [counts (production/count-computer-units)]
      (should= 1 (get counts :army))
      (should-be-nil (get counts :destroyer)))))

(describe "count-computer-cities"
  (before (reset-all-atoms!))

  (it "counts computer cities"
    (reset! atoms/game-map (build-test-map ["X#X~O"]))
    (should= 2 (production/count-computer-cities)))

  (it "ignores player and free cities"
    (reset! atoms/game-map (build-test-map ["O+X"]))
    (should= 1 (production/count-computer-cities))))

(describe "priority-based production"
  (before (reset-all-atoms!))

  (it "country city produces fighter via per-country priority when 0 fighters exist"
    ;; Coastal city with country-id 1, 2 transports (with escorts), 10 armies, 1 patrol boat
    ;; All per-country priorities met except fighters (0 < 2). Per-country fighter priority fires.
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    (swap! atoms/game-map assoc-in [18 0 :contents :patrol-country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "inland country city skips coastal priorities and produces army"
    ;; Landlocked city with country-id 1, 2 transports (elsewhere), 5 armies
    ;; Transport priority needs coastal. Army priority: < 10 armies, no other producing.
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 1 :country-id] 1)
    (should= :army (production/decide-production [1 1]))))

(describe "decide-production"
  (before (reset-all-atoms!))

  (it "coastal country city with 6+ armies produces transport"
    ;; Coastal city with country-id 1, 6 armies with country-id 1, 0 transports
    (reset! atoms/game-map (build-test-map ["~X#aaaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaa"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 9)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should= :transport (production/decide-production [1 0]))))

(describe "country-aware production"
  (before (reset-all-atoms!))

  (it "coastal city produces transport when country has fewer than 2 and at least 6 armies"
    ;; Coastal city with country-id 1, no transports, 6 armies on map
    (reset! atoms/game-map (build-test-map ["~X#aaaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaa"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 9)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should= :transport (production/decide-production [1 0])))

  (it "coastal city does not produce transport when country has fewer than 6 armies"
    ;; Coastal city with country-id 1, no transports, only 5 armies
    (reset! atoms/game-map (build-test-map ["~X#aaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaa"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 8)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (should-not= :transport (production/decide-production [1 0])))

  (it "does not produce transport when country already has 2"
    ;; Coastal city with country-id 1, two transports with same country-id on map
    (reset! atoms/game-map (build-test-map ["~X~~tt"]))
    (reset! atoms/computer-map (build-test-map ["~X~~tt"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :country-id] 1)
    (should-not= :transport (production/decide-production [1 0])))

  (it "landlocked city does not produce transport even when country needs one"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "###"]))
    (reset! atoms/computer-map (build-test-map ["###"
                                                 "#X#"
                                                 "###"]))
    (swap! atoms/game-map assoc-in [1 1 :country-id] 1)
    (should-not= :transport (production/decide-production [1 1])))

  (it "produces army when country has fewer than 10"
    ;; City with country-id 1, 2 transports (priority met), 1 patrol boat (priority met), 2 armies (below 10)
    ;; Map: ~ X ~ ~ t t ~ a a ~ p
    ;; Idx: 0 1 2 3 4 5 6 7 8 9 10
    (reset! atoms/game-map (build-test-map ["~X~~tt~aa~p"]))
    (reset! atoms/computer-map (build-test-map ["~X~~tt~aa~p"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [7 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [8 0 :contents :country-id] 1)
    ;; Patrol boat with matching patrol-country-id
    (swap! atoms/game-map assoc-in [10 0 :contents :patrol-country-id] 1)
    (should= :army (production/decide-production [1 0])))

  (it "does not produce army when another city in country is already producing armies"
    ;; City 1 at [1,0] already producing army, city 2 at [3,0] should not also produce army
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    ;; Give 2 transports so transport priority is met
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :transport :owner :computer :country-id 1})
    (swap! atoms/game-map assoc-in [4 0 :contents]
           {:type :transport :owner :computer :country-id 1})
    (reset! atoms/production {[1 0] {:item :army :remaining-rounds 3}})
    (should-not= :army (production/decide-production [3 0])))

)

(describe "army overproduction fix"
  (before (reset-all-atoms!))

  (it "count-country-armies includes armies aboard transports"
    ;; 2 armies on map + transport with 3 armies aboard = 5 total
    (reset! atoms/game-map (build-test-map ["aa~t"]))
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :army-count] 3)
    (should= 5 (production/count-country-armies 1)))

  (it "count-country-armies does not count transport cargo from different country"
    ;; 2 armies country 1 + transport country 2 with 3 armies = 2 for country 1
    (reset! atoms/game-map (build-test-map ["aa~t"]))
    (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [1 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :contents :country-id] 2)
    (swap! atoms/game-map assoc-in [3 0 :contents :army-count] 3)
    (should= 2 (production/count-country-armies 1)))

)

(describe "satellite production gate"
  (before (reset-all-atoms!))

  (it "produces satellite when >15 cities and none alive"
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (satisfy-inland-per-country 0)
      (should= :satellite (production/decide-production [0 0]))))

  (it "does not produce satellite when one already alive"
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          sat-row [{:type :land :contents {:type :satellite :owner :computer :direction [1 0] :turns-remaining 50}}
                   {:type :land}]
          game-map (vec [city-row sat-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (satisfy-inland-per-country 0)
      (should-be-nil (production/decide-production [0 0]))))

  (it "does not produce satellite when <=15 cities"
    (let [city-row (vec (for [i (range 30)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (satisfy-inland-per-country 0)
      (should-be-nil (production/decide-production [0 0])))))

(describe "process-computer-city"
  (before (reset-all-atoms!))

  (it "sets production when none exists and city has a country-id"
    (reset! atoms/game-map (build-test-map ["X+#"]))
    (reset! atoms/computer-map (build-test-map ["X+#"]))
    (swap! atoms/game-map assoc-in [0 0 :country-id] 1)
    (reset! atoms/production {})
    (production/process-computer-city [0 0])
    (should-not-be-nil (get @atoms/production [0 0])))

  (it "does not change existing production"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/computer-map (build-test-map ["X#"]))
    (reset! atoms/production {[0 0] {:item :fighter :remaining-rounds 10}})
    (production/process-computer-city [0 0])
    (should= :fighter (:item (get @atoms/production [0 0])))))

(describe "patrol boat production"
  (before (reset-all-atoms!))

  (it "produces patrol boat when country has none"
    ;; Coastal computer city with country-id 1, no patrol boats on map
    ;; Give 2 transports (with escorts) and 10 armies so transport/army/destroyer priorities are met
    ;; Map: ~ X # a a a a a a a a a a t t d d
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaaaaaattdd"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; Assign country-id to armies (positions 3-12)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    ;; Assign country-id and transport-id to transports, with escort-destroyer-id
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    (should= :patrol-boat (production/decide-production [1 0])))

  (it "does not produce patrol boat when country already has one"
    ;; Coastal computer city with country-id 1, one patrol boat with matching patrol-country-id
    ;; Give 2 transports (with escorts) and 10 armies so transport/army/destroyer priorities are met
    ;; Map: ~ X # a a a a a a a a a a t t d d ~ p
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; Assign country-id to armies (positions 3-12)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    ;; Assign country-id and escort to transports
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    ;; Give patrol boat matching patrol-country-id
    (swap! atoms/game-map assoc-in [18 0 :contents :patrol-country-id] 1)
    (should-not= :patrol-boat (production/decide-production [1 0]))))

(describe "destroyer escort production"
  (before (reset-all-atoms!))

  (it "produces destroyer when country has unadopted transport and global cap allows"
    ;; Coastal city, country with 10 armies, 2 transports (one unadopted), 0 destroyers, 1 patrol boat
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaatt~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [16 0 :contents :patrol-country-id] 1)
    (should= :destroyer (production/decide-production [1 0])))

  (it "does not produce destroyer when global cap reached"
    ;; Same setup but add a destroyer already on the map (2 transports, 2 destroyers)
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattd~pd"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [16 0 :contents :patrol-country-id] 1)
    ;; 2 destroyers and 2 transports: cap reached (destroyers >= transports)
    (should-not= :destroyer (production/decide-production [1 0]))))

(describe "carrier production gate"
  (before (reset-all-atoms!))

  (it "produces carrier when >10 cities, <2 producing, valid position exists"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (should= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when <=10 cities"
    (let [cells (vec (for [j (range 50)]
                       (cond
                         (and (even? j) (<= j 18)) {:type :city :city-status :computer}
                         (<= j 18) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 18)
      (should-not= :carrier (production/decide-production [0 18]))))

  (it "does not produce carrier when 2 already producing"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when 8 already exist"
    (let [cells (vec (for [j (range 80)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (should-not= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when no valid position exists"
    (let [cells (vec (for [j (range 44)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (should-not= :carrier (production/decide-production [0 22])))))

(describe "battleship production gate"
  (before (reset-all-atoms!))

  (it "produces battleship when battleships < carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 48) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                          :carrier-id 1 :carrier-mode :holding
                                                          :group-battleship-id nil
                                                          :group-submarine-ids []}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :battleship (production/decide-production [0 22]))))

  (it "does not produce battleship when battleships >= carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :battleship (production/decide-production [0 22])))))

(describe "submarine production gate"
  (before (reset-all-atoms!))

  (it "produces submarine when submarines < 2 * carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :submarine (production/decide-production [0 22]))))

  (it "does not produce submarine when submarines >= 2 * carriers"
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         (= j 32) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                         (= j 33) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 22)
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :submarine (production/decide-production [0 22])))))

(describe "fighter country production limit"
  (before (reset-all-atoms!))

  (it "produces fighter when country has 0 fighters and all other per-country priorities met"
    ;; Coastal city with country-id 1, 2 transports (with escorts), 10 armies, 1 patrol boat, 1 destroyer
    ;; All per-country priorities met. No carriers, so global priorities don't fire except fighter fallback.
    ;; With 0 country fighters, per-country fighter priority (< 2) should fire.
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    (swap! atoms/game-map assoc-in [18 0 :contents :patrol-country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "produces fighter when country has 1 fighter and all other per-country priorities met"
    ;; Same as above but add 1 fighter with matching country-id
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~pf"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    (swap! atoms/game-map assoc-in [18 0 :contents :patrol-country-id] 1)
    ;; Assign country-id to the fighter at col 19
    (swap! atoms/game-map assoc-in [19 0 :contents :country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "produces nil when country already has 2 fighters and all priorities met"
    ;; Same setup but with 2 fighters with matching country-id
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~pff"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [13 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [13 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [14 0 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [14 0 :contents :escort-destroyer-id] 2)
    (swap! atoms/game-map assoc-in [18 0 :contents :patrol-country-id] 1)
    ;; Assign country-id to both fighters at cols 19 and 20
    (swap! atoms/game-map assoc-in [19 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [20 0 :contents :country-id] 1)
    ;; Per-country fighter priority should NOT fire (2 fighters >= limit of 2).
    ;; Global fallback is nil — city stays idle to prevent overproduction.
    (should-be-nil (production/decide-production [1 0]))))
