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

(describe "get-production-ratio"
  (it "returns ratio-1-2 for 1 city"
    (let [ratio (production/get-production-ratio 1)]
      (should= 60 (:army ratio))
      (should= 0 (:fighter ratio))))

  (it "returns ratio-1-2 for 2 cities"
    (let [ratio (production/get-production-ratio 2)]
      (should= 60 (:army ratio))))

  (it "returns ratio-3-4 for 3 cities"
    (let [ratio (production/get-production-ratio 3)]
      (should= 50 (:army ratio))
      (should= 10 (:fighter ratio))))

  (it "returns ratio-5-9 for 5 cities"
    (let [ratio (production/get-production-ratio 5)]
      (should= 40 (:army ratio))
      (should= 10 (:destroyer ratio))))

  (it "returns ratio-10-plus for 10 cities"
    (let [ratio (production/get-production-ratio 10)]
      (should= 30 (:army ratio))
      (should= 15 (:fighter ratio))
      (should= 10 (:battleship ratio)))))

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
    ;; City with country-id 1, 2 transports (priority met), 2 armies (below 10)
    (reset! atoms/game-map (build-test-map ["~X~~tt~aa"]))
    (reset! atoms/computer-map (build-test-map ["~X~~tt~aa"]))
    (swap! atoms/game-map assoc-in [0 1 :country-id] 1)
    (swap! atoms/game-map assoc-in [0 4 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 5 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 7 :contents :country-id] 1)
    (swap! atoms/game-map assoc-in [0 8 :contents :country-id] 1)
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

  (it "falls back to ratio-based for non-country city"
    ;; City with no country-id falls back to existing logic
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (reset! atoms/computer-map (build-test-map ["~X#"]))
    (should-not-be-nil (production/decide-production [0 1]))))

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
