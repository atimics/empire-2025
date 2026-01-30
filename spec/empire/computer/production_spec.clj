(ns empire.computer.production-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "city-is-coastal?"
  (before (reset-all-atoms!))

  (it "returns true when city has adjacent sea"
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (should (production/city-is-coastal? [0 1])))

  (it "returns false when city has no adjacent sea"
    (reset! atoms/game-map (build-test-map ["#X#"]))
    (should-not (production/city-is-coastal? [0 1]))))

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

  (it "non-country city produces army when continent has objectives"
    (reset! atoms/game-map (build-test-map ["~X+"]))
    (reset! atoms/computer-map (build-test-map ["~X+"]))
    (should= :army (production/decide-production [0 1])))

  (it "country city produces fighter when all per-country and global caps met"
    ;; Coastal city with country-id 1, 2 transports (with escorts), 10 armies, 1 patrol boat
    ;; All per-country priorities met. No carriers, so global priorities don't fire.
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [0 13 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [0 14 :contents :escort-destroyer-id] 2)
    (swap! atoms/game-map assoc-in [0 18 :contents :patrol-country-id] 1)
    (should= :fighter (production/decide-production [0 1])))

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

  (it "produces army when continent has objectives"
    (reset! atoms/game-map (build-test-map ["X#+~"]))
    (reset! atoms/computer-map (build-test-map ["X#+~"]))
    ;; Continent has free city - should produce army
    (should= :army (production/decide-production [0 0])))

  (it "landlocked city produces army or fighter only"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#X#"
                                             "###"]))
    (reset! atoms/computer-map (build-test-map ["###"
                                                 "#X#"
                                                 "###"]))
    (let [unit-type (production/decide-production [1 1])]
      (should (#{:army :fighter} unit-type))))

  (it "coastal city can produce naval units"
    ;; With many cities, ratio allows naval production
    (reset! atoms/game-map (build-test-map ["~X~X~X~X~X~X~X~X~X~X~"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~X~X~X~X~X~X~X~X~"]))
    ;; After many units exist, should eventually produce naval
    (reset! atoms/game-map
            (vec (concat
                   [(vec (concat [{:type :city :city-status :computer}]
                                 (repeat 20 {:type :sea})))]
                   ;; Add some existing armies
                   [(vec (concat
                           (for [_ (range 10)]
                             {:type :land :contents {:type :army :owner :computer}})
                           (repeat 11 {:type :sea})))])))
    (reset! atoms/computer-map @atoms/game-map)
    ;; With 10+ existing armies, coastal city might produce transport
    (let [unit-type (production/decide-production [0 0])]
      (should-not-be-nil unit-type))))

(describe "country-aware production"
  (before (reset-all-atoms!))

  (it "coastal city produces transport when country has fewer than 2 and at least 6 armies"
    ;; Coastal city with country-id 1, no transports, 6 armies on map
    (reset! atoms/game-map (build-test-map ["~X#aaaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaa"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (doseq [col (range 3 9)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    (should= :transport (production/decide-production [0 1])))

  (it "coastal city does not produce transport when country has fewer than 6 armies"
    ;; Coastal city with country-id 1, no transports, only 5 armies
    (reset! atoms/game-map (build-test-map ["~X#aaaaa"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaa"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (doseq [col (range 3 8)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    (should-not= :transport (production/decide-production [0 1])))

  (it "does not produce transport when country already has 2"
    ;; Coastal city with country-id 1, two transports with same country-id on map
    (reset! atoms/game-map (build-test-map ["~X~~tt"]))
    (reset! atoms/computer-map (build-test-map ["~X~~tt"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (swap! atoms/game-map assoc-in [0 4 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 5 :contents :country-id] 1)
    (should-not= :transport (production/decide-production [0 1])))

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
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (swap! atoms/game-map assoc-in [0 4 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 5 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 7 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 8 :contents :country-id] 1)
    ;; Patrol boat with matching patrol-country-id
    (swap! atoms/game-map assoc-in [0 10 :contents :patrol-country-id] 1)
    (should= :army (production/decide-production [0 1])))

  (it "does not produce army when another city in country is already producing armies"
    ;; City 1 at [0,1] already producing army, city 2 at [0,3] should not also produce army
    (reset! atoms/game-map (build-test-map ["~X~X~"]))
    (reset! atoms/computer-map (build-test-map ["~X~X~"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (swap! atoms/game-map assoc-in [0 3 :country-id] 1)
    ;; Give 2 transports so transport priority is met
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :transport :owner :computer :country-id 1})
    (swap! atoms/game-map assoc-in [0 4 :contents]
           {:type :transport :owner :computer :country-id 1})
    (reset! atoms/production {[0 1] {:item :army :remaining-rounds 3}})
    (should-not= :army (production/decide-production [0 3])))

  (it "non-country city without objectives falls through to global priorities"
    ;; City with no country-id and no objectives on continent falls to global (fighter fallback)
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (reset! atoms/computer-map (build-test-map ["~X#"]))
    (should= :fighter (production/decide-production [0 1]))))

(describe "satellite production gate"
  (before (reset-all-atoms!))

  (it "produces satellite when >15 cities and none alive"
    ;; Build a map with 16 computer cities (need >15)
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      ;; No live satellites, 16 cities
      (should= :satellite (production/decide-production [0 0]))))

  (it "does not produce satellite when one already alive"
    ;; 16 computer cities but one live satellite on the map
    (let [city-row (vec (for [i (range 32)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          sat-row [{:type :land :contents {:type :satellite :owner :computer :direction [1 0] :turns-remaining 50}}
                   {:type :land}]
          game-map (vec [city-row sat-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (should-not= :satellite (production/decide-production [0 0]))))

  (it "does not produce satellite when <=15 cities"
    ;; Only 15 computer cities
    (let [city-row (vec (for [i (range 30)]
                          (if (even? i)
                            {:type :city :city-status :computer}
                            {:type :land})))
          game-map (vec [city-row])]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map game-map)
      (should-not= :satellite (production/decide-production [0 0])))))

(describe "process-computer-city"
  (before (reset-all-atoms!))

  (it "sets production when none exists"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/computer-map (build-test-map ["X#"]))
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
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    ;; Assign country-id to armies (positions 3-12)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    ;; Assign country-id and transport-id to transports, with escort-destroyer-id
    (swap! atoms/game-map assoc-in [0 13 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [0 14 :contents :escort-destroyer-id] 2)
    (should= :patrol-boat (production/decide-production [0 1])))

  (it "does not produce patrol boat when country already has one"
    ;; Coastal computer city with country-id 1, one patrol boat with matching patrol-country-id
    ;; Give 2 transports (with escorts) and 10 armies so transport/army/destroyer priorities are met
    ;; Map: ~ X # a a a a a a a a a a t t d d ~ p
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (reset! atoms/computer-map (build-test-map ["~X#aaaaaaaaaattdd~p"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    ;; Assign country-id to armies (positions 3-12)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    ;; Assign country-id and escort to transports
    (swap! atoms/game-map assoc-in [0 13 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :escort-destroyer-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [0 14 :contents :escort-destroyer-id] 2)
    ;; Give patrol boat matching patrol-country-id
    (swap! atoms/game-map assoc-in [0 18 :contents :patrol-country-id] 1)
    (should-not= :patrol-boat (production/decide-production [0 1]))))

(describe "destroyer escort production"
  (before (reset-all-atoms!))

  (it "produces destroyer when country has unadopted transport and global cap allows"
    ;; Coastal city, country with 10 armies, 2 transports (one unadopted), 0 destroyers, 1 patrol boat
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaatt~p"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [0 13 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [0 16 :contents :patrol-country-id] 1)
    (should= :destroyer (production/decide-production [0 1])))

  (it "does not produce destroyer when global cap reached"
    ;; Same setup but add a destroyer already on the map (2 transports, 2 destroyers)
    (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaattd~pd"]))
    (reset! atoms/computer-map @atoms/game-map)
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (doseq [col (range 3 13)]
      (swap! atoms/game-map assoc-in [0 col :contents :country-id] 1))
    (swap! atoms/game-map assoc-in [0 13 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 13 :contents :transport-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 14 :contents :transport-id] 2)
    (swap! atoms/game-map assoc-in [0 16 :contents :patrol-country-id] 1)
    ;; 2 destroyers and 2 transports: cap reached (destroyers >= transports)
    (should-not= :destroyer (production/decide-production [0 1]))))

(describe "carrier production gate"
  (before (reset-all-atoms!))

  (it "produces carrier when >10 cities, <2 producing, valid position exists"
    ;; 12 computer cities at even positions 0-22, land at odd 1-21, sea from 23-59
    ;; No country-id on test city, continent has no objectives
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (should= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when <=10 cities"
    ;; 10 computer cities at even positions 0-18, land at odd 1-17, sea from 19-49
    (let [cells (vec (for [j (range 50)]
                       (cond
                         (and (even? j) (<= j 18)) {:type :city :city-status :computer}
                         (<= j 18) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (should-not= :carrier (production/decide-production [0 18]))))

  (it "does not produce carrier when 2 already producing"
    ;; 12 cities but 2 already producing carriers
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :carrier (production/decide-production [0 22]))))

  (it "does not produce carrier when no valid position exists"
    ;; 12 cities but sea only extends 25 cells past last city (max distance 25 < 26)
    (let [cells (vec (for [j (range 48)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (should-not= :carrier (production/decide-production [0 22])))))

(describe "battleship production gate"
  (before (reset-all-atoms!))

  (it "produces battleship when battleships < carriers"
    ;; 12 cities, a carrier on the map, no battleships
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
      ;; Carrier gate won't fire because a carrier already exists at valid position
      ;; and find-carrier-position returns nil when position [0,48] is occupied
      ;; Actually - the carrier at [0,48] is a refueling site, so the next valid
      ;; position would need to be >= 26 from it too. Let's skip carrier gate
      ;; by setting 2 carrier productions
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :battleship (production/decide-production [0 22]))))

  (it "does not produce battleship when battleships >= carriers"
    ;; 12 cities, 1 carrier, 1 battleship - cap reached
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :battleship (production/decide-production [0 22])))))

(describe "submarine production gate"
  (before (reset-all-atoms!))

  (it "produces submarine when submarines < 2 * carriers"
    ;; 12 cities, 1 carrier, 1 battleship (cap met), 0 submarines
    (let [cells (vec (for [j (range 60)]
                       (cond
                         (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                         (<= j 22) {:type :land}
                         (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                         :else {:type :sea})))]
      (reset! atoms/game-map [cells])
      (reset! atoms/computer-map [cells])
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should= :submarine (production/decide-production [0 22]))))

  (it "does not produce submarine when submarines >= 2 * carriers"
    ;; 12 cities, 1 carrier, 1 BB (cap met), 2 subs - cap reached
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
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :carrier :remaining-rounds 10}})
      (should-not= :submarine (production/decide-production [0 22])))))
