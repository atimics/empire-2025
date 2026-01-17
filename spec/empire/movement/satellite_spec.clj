(ns empire.movement.satellite-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :refer [move-satellite set-unit-movement]]
    [empire.movement.visibility :refer [update-cell-visibility]]
    [speclj.core :refer :all]))

(describe "satellite movement"
  (it "does not move without a target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 5])
      ;; Satellite should stay in place - no target set
      (should (:contents (get-in @atoms/game-map [5 5])))
      (should-be-nil (:target (:contents (get-in @atoms/game-map [5 5]))))))

  (it "still decrements turns even without a target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Run move-satellites (which calls move-satellite-steps)
      (game-loop/move-satellites)
      ;; Satellite should still be at [5 5] but with decremented turns
      (let [sat (:contents (get-in @atoms/game-map [5 5]))]
        (should sat)
        (should= 4 (:turns-remaining sat)))))

  (it "moves toward its target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 5])
      ;; Satellite should have moved toward target [9 9], so to [6 6]
      (should (:contents (get-in @atoms/game-map [6 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 5])))
      (should= [9 9] (:target (:contents (get-in @atoms/game-map [6 6]))))))

  (it "moves horizontally when target is directly east"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 3] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 3])
      ;; Satellite should move east to [5 4]
      (should (:contents (get-in @atoms/game-map [5 4])))
      (should-be-nil (:contents (get-in @atoms/game-map [5 3])))))

  (it "moves vertically when target is directly south"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [3 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [3 5])
      ;; Satellite should move south to [4 5]
      (should (:contents (get-in @atoms/game-map [4 5])))
      (should-be-nil (:contents (get-in @atoms/game-map [3 5])))))

  (it "gets new target on opposite boundary when reaching right edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 9] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 9])
      ;; Satellite at target on right edge should get new target on left edge (column 0)
      (let [sat (:contents (get-in @atoms/game-map [5 9]))]
        (should sat)
        (should= 0 (second (:target sat))))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 5])
      ;; Satellite at target on bottom edge should get new target on top edge (row 0)
      (let [sat (:contents (get-in @atoms/game-map [9 5]))]
        (should sat)
        (should= 0 (first (:target sat))))))

  (it "gets new target on one of opposite boundaries when at corner"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 9] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 9])
      ;; Satellite at corner should get new target on either top edge (row 0) or left edge (column 0)
      (let [sat (:contents (get-in @atoms/game-map [9 9]))
            [tx ty] (:target sat)]
        (should sat)
        (should (or (= tx 0) (= ty 0))))))

  (it "extends non-boundary target to wall when setting movement"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          ;; Satellite at [2 2] - will set target to [5 5] (not on boundary)
                          (assoc-in [2 2] {:type :land :contents {:type :satellite :owner :player :mode :awake :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Set movement to non-boundary target [5 5] - should extend to [9 9]
      (set-unit-movement [2 2] [5 5])
      (let [sat (:contents (get-in @atoms/game-map [2 2]))
            [tx ty] (:target sat)]
        (should sat)
        (should= :moving (:mode sat))
        ;; Target should be extended to boundary at [9 9] (southeast corner)
        (should= [9 9] [tx ty]))))

  (it "decrements turns-remaining once per round not per step"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [2 2] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (game-loop/move-satellites)
      ;; After one round of movement (10 steps), turns-remaining should only decrement by 1
      (let [sat-coords (first (for [i (range 10) j (range 10)
                                    :let [cell (get-in @atoms/game-map [i j])]
                                    :when (= :satellite (:type (:contents cell)))]
                                [i j]))
            sat (:contents (get-in @atoms/game-map sat-coords))]
        (should= 49 (:turns-remaining sat)))))

  (it "is removed when turns-remaining reaches zero"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (game-loop/move-satellites)
      ;; Satellite should be removed after round ends with turns-remaining at 0
      ;; Check that satellite is gone from both original and any moved position
      (let [sat-count (count (for [i (range 10) j (range 10)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies after correct number of rounds"
    (let [initial-map (-> (vec (repeat 20 (vec (repeat 20 {:type :land}))))
                          (assoc-in [10 10] {:type :land :contents {:type :satellite :owner :player :target [19 19] :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 20 (vec (repeat 20 nil)))))
      ;; Run 4 rounds - satellite should still exist
      (dotimes [_ 4]
        (game-loop/move-satellites))
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 1 sat-count))
      ;; Run 1 more round - satellite should be removed
      (game-loop/move-satellites)
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies through full game loop with start-new-round"
    (let [initial-map (-> (vec (repeat 20 (vec (repeat 20 {:type :land}))))
                          (assoc-in [10 10] {:type :land :contents {:type :satellite :owner :player :target [19 19] :turns-remaining 3}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 20 (vec (repeat 20 nil)))))
      (reset! atoms/player-items [])
      (reset! atoms/production {})
      (reset! atoms/round-number 0)
      ;; Run 3 full rounds via start-new-round
      (dotimes [_ 3]
        (game-loop/start-new-round)
        ;; Process all player items (the satellite should be skipped because it has a target)
        (while (seq @atoms/player-items)
          (game-loop/advance-game)))
      ;; Satellite should be dead after 3 rounds
      (let [sat-count (count (for [i (range 20) j (range 20)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "dies even when bouncing off corners multiple times"
    ;; Satellite starting near corner, will bounce multiple times in 5 turns
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [8 8] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 5}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Run 5 rounds - satellite should die
      (dotimes [_ 5]
        (game-loop/move-satellites))
      (let [sat-count (count (for [i (range 10) j (range 10)
                                   :let [cell (get-in @atoms/game-map [i j])]
                                   :when (= :satellite (:type (:contents cell)))]
                               [i j]))]
        (should= 0 sat-count))))

  (it "is removed from visibility map when it dies"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      ;; Update visibility so satellite appears on player-map
      (update-cell-visibility [5 5] :player)
      ;; Verify satellite is visible
      (should= :satellite (:type (:contents (get-in @atoms/player-map [5 5]))))
      ;; Run one round - satellite should die and be removed from both maps
      (game-loop/move-satellites)
      ;; Find where satellite ended up (it moved before dying)
      (let [sat-in-game (first (for [i (range 10) j (range 10)
                                     :let [cell (get-in @atoms/game-map [i j])]
                                     :when (= :satellite (:type (:contents cell)))]
                                 [i j]))
            sat-in-player (first (for [i (range 10) j (range 10)
                                       :let [cell (get-in @atoms/player-map [i j])]
                                       :when (= :satellite (:type (:contents cell)))]
                                   [i j]))]
        (should-be-nil sat-in-game)
        (should-be-nil sat-in-player))))

  (it "reveals two rectangular rings around its position"
    (let [initial-map (vec (repeat 15 (vec (repeat 15 {:type :land}))))
          initial-map (assoc-in initial-map [7 7] {:type :land :contents {:type :satellite :owner :player :target [14 14] :turns-remaining 50}})]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 15 (vec (repeat 15 nil)))))
      (update-cell-visibility [7 7] :player)
      ;; Ring 1 (distance 1) - all 8 cells should be visible
      (should (get-in @atoms/player-map [6 6]))
      (should (get-in @atoms/player-map [6 7]))
      (should (get-in @atoms/player-map [6 8]))
      (should (get-in @atoms/player-map [7 6]))
      (should (get-in @atoms/player-map [7 8]))
      (should (get-in @atoms/player-map [8 6]))
      (should (get-in @atoms/player-map [8 7]))
      (should (get-in @atoms/player-map [8 8]))
      ;; Ring 2 (distance 2) - all 16 cells should be visible
      (should (get-in @atoms/player-map [5 5]))
      (should (get-in @atoms/player-map [5 6]))
      (should (get-in @atoms/player-map [5 7]))
      (should (get-in @atoms/player-map [5 8]))
      (should (get-in @atoms/player-map [5 9]))
      (should (get-in @atoms/player-map [6 5]))
      (should (get-in @atoms/player-map [6 9]))
      (should (get-in @atoms/player-map [7 5]))
      (should (get-in @atoms/player-map [7 9]))
      (should (get-in @atoms/player-map [8 5]))
      (should (get-in @atoms/player-map [8 9]))
      (should (get-in @atoms/player-map [9 5]))
      (should (get-in @atoms/player-map [9 6]))
      (should (get-in @atoms/player-map [9 7]))
      (should (get-in @atoms/player-map [9 8]))
      (should (get-in @atoms/player-map [9 9]))
      ;; Center cell (the satellite's position) should also be visible
      (should (get-in @atoms/player-map [7 7])))))
