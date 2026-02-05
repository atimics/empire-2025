(ns empire.computer.production-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn- satisfy-inland-per-country
  "Stamp city with country-id and add units to satisfy per-country priorities.
   Uses a transport with armies aboard to fit in smaller test maps."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  ;; Transport at position 1 with 20 armies aboard
  (swap! atoms/game-map assoc-in [0 1 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 20 :hits 3})
  ;; Two fighters
  (doseq [j [3 5]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})))

(defn- satisfy-coastal-per-country
  "Stamp coastal city with country-id and add units to satisfy all per-country priorities.
   Uses a transport with armies aboard to fit in smaller test maps."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  ;; Two fighters
  (swap! atoms/game-map assoc-in [0 1 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  (swap! atoms/game-map assoc-in [0 3 :contents]
         {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20 :country-id 1})
  ;; 1 transport with 20 armies aboard to satisfy army limit
  (swap! atoms/game-map assoc-in [0 5 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 20 :hits 3})
  ;; 1 patrol boat
  (swap! atoms/game-map assoc-in [0 7 :contents]
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
    ;; Coastal city with country-id 1, 1 transport (with escort), 20 armies, 1 patrol boat
    ;; All per-country priorities met except fighters (0 < 2). Per-country fighter priority fires.
    ;; Map: ~ X # a(x20) t d ~ p
    ;; Idx: 0 1 2 3-22    23 24 25 26
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [26 0 :contents :patrol-country-id] 1)
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

  (it "does not produce transport when country already has 1"
    ;; Coastal city with country-id 1, one transport with same country-id on map
    (reset! atoms/game-map (build-test-map ["~X~~t"]))
    (reset! atoms/computer-map (build-test-map ["~X~~t"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
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

  (it "produces army when country has fewer than 20"
    ;; City with country-id 1, 1 transport (priority met), 1 patrol boat (priority met), 2 armies (below 20)
    ;; Map: ~ X ~ ~ t ~ a a ~ p
    ;; Idx: 0 1 2 3 4 5 6 7 8 9
    (reset! atoms/game-map (build-test-map ["~X~~t~aa~p"]))
    (reset! atoms/computer-map (build-test-map ["~X~~t~aa~p"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [6 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [7 0 :contents :country-id] 1)
    ;; Patrol boat with matching patrol-country-id
    (swap! atoms/game-map assoc-in [9 0 :contents :patrol-country-id] 1)
    (should= :army (production/decide-production [1 0])))

  (it "does not produce army when another city in country is already producing armies"
    ;; City 1 at [1,0] already producing army, city 2 at [3,0] should not also produce army
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    ;; Give 1 transport so transport priority is met
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :transport :owner :computer :country-id 1})
    (reset! atoms/production {[1 0] {:item :army :remaining-rounds 3}})
    (should-not= :army (production/decide-production [3 0])))

)

(describe "country-city-producing? coordination"
  (before (reset-all-atoms!))

  (it "returns true when another city in country is producing the unit type"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should (production/country-city-producing? [3 0] 1 :transport)))

  (it "returns false when no other city in country is producing the unit type"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {})
    (should-not (production/country-city-producing? [3 0] 1 :transport)))

  (it "returns false when city producing is from different country"
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 2)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should-not (production/country-city-producing? [3 0] 1 :transport)))

  (it "does not produce transport when another city in country is already producing"
    (reset! atoms/game-map (build-test-map ["~X~X~aaaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~aaaaaa"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (doseq [col (range 5 11)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (reset! atoms/production {[1 0] {:item :transport :remaining-rounds 10}})
    (should= :army (production/decide-production [3 0])))

  (it "does not produce destroyer when another city in country is already producing"
    ;; Two coastal cities, same country, 10 armies in transport, 1 patrol boat, first producing destroyer
    (reset! atoms/game-map (build-test-map ["~X~X~t~p"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~t~p"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [3 0 :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [5 0 :contents :army-count] 20)
    (swap! atoms/game-map assoc-in [7 0 :contents :patrol-country-id] 1)
    (reset! atoms/production {[1 0] {:item :destroyer :remaining-rounds 10}})
    (should= :fighter (production/decide-production [3 0]))))

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
    ;; Give 1 transport (with escort) and 20 armies so transport/army/destroyer priorities are met
    ;; Map: ~ X # a(x20) t d
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; Assign country-id to armies (positions 3-22)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    ;; Assign country-id and transport-id to transport, with escort-destroyer-id
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (should= :patrol-boat (production/decide-production [1 0])))

  (it "does not produce patrol boat when country already has one"
    ;; Coastal computer city with country-id 1, one patrol boat with matching patrol-country-id
    ;; Give 1 transport (with escort) and 20 armies so transport/army/destroyer priorities are met
    ;; Map: ~ X # a(x20) t d ~ p
    ;; Idx: 0 1 2 3-22    23 24 25 26
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~p"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~p"]))
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    ;; Assign country-id to armies (positions 3-22)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    ;; Assign country-id and escort to transport
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    ;; Give patrol boat matching patrol-country-id (position 26)
    (swap! atoms/game-map assoc-in [26 0 :contents :patrol-country-id] 1)
    (should-not= :patrol-boat (production/decide-production [1 0]))))

(describe "destroyer escort production"
  (before (reset-all-atoms!))

  (it "produces destroyer when country has unadopted transport and global cap allows"
    ;; Coastal city, country with 20 armies, 1 unadopted transport, 0 destroyers, 1 patrol boat
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaat~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [25 0 :contents :patrol-country-id] 1)
    (should= :destroyer (production/decide-production [1 0])))

  (it "does not produce destroyer when global cap reached"
    ;; Same setup but add a destroyer already on the map (1 transport, 1 destroyer)
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaat~pd"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [25 0 :contents :patrol-country-id] 1)
    ;; 1 destroyer and 1 transport: cap reached (destroyers >= transports)
    (should-not= :destroyer (production/decide-production [1 0]))))

(describe "carrier production gate"
  (before (reset-all-atoms!))

  (it "produces carrier when >10 cities, <2 producing, valid position exists"
    ;; 12 cities: 6 at j=0,2,4,6,8,10 and 6 at j=50,52,54,56,58,60
    ;; Distance 0 to 50 = 50 > 32, creating a distant pair that needs carrier
    (let [cells (vec (for [j (range 80)]
                       (cond
                         (and (even? j) (<= j 10)) {:type :city :city-status :computer}
                         (<= j 10) {:type :land}
                         (and (even? j) (>= j 50) (<= j 60)) {:type :city :city-status :computer}
                         (and (>= j 50) (<= j 60)) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (satisfy-coastal-per-country 10)
      (ship/update-distant-city-pairs!)
      (should= :carrier (production/decide-production [0 10]))))

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
    ;; Coastal city with country-id 1, 1 transport (with escort), 20 armies, 1 patrol boat
    ;; All per-country priorities met. No carriers, so global priorities don't fire except fighter fallback.
    ;; With 0 country fighters, per-country fighter priority (< 2) should fire.
    ;; Map: ~ X # a(x20) t d ~ p
    ;; Idx: 0 1 2 3-22    23 24 25 26
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [26 0 :contents :patrol-country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "produces fighter when country has 1 fighter and all other per-country priorities met"
    ;; Same as above but add 1 fighter with matching country-id
    ;; Map: ~ X # a(x20) t d ~ p f
    ;; Idx: 0 1 2 3-22    23 24 25 26 27
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~pf"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [26 0 :contents :patrol-country-id] 1)
    ;; Assign country-id to the fighter at col 27
    (swap! atoms/game-map assoc-in [27 0 :contents :country-id] 1)
    (should= :fighter (production/decide-production [1 0])))

  (it "produces nil when country already has 2 fighters and all priorities met"
    ;; Same setup but with 2 fighters with matching country-id
    ;; Map: ~ X # a(x20) t d ~ p f f
    ;; Idx: 0 1 2 3-22    23 24 25 26 27 28
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~pff"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
    (doseq [col (range 3 23)]
      (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [26 0 :contents :patrol-country-id] 1)
    ;; Assign country-id to both fighters at cols 27 and 28
    (swap! atoms/game-map assoc-in [27 0 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [28 0 :contents :country-id] 1)
    ;; Per-country fighter priority should NOT fire (2 fighters >= limit of 2).
    ;; Global fallback is nil — city stays idle to prevent overproduction.
    (should-be-nil (production/decide-production [1 0]))))
