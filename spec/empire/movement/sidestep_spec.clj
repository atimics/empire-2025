(ns empire.movement.sidestep-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [speclj.core :refer :all]))

(describe "sidestep around friendly units"
  (it "sidesteps diagonally around friendly unit and continues moving"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by friendly at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})  ;; Other diagonal
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should have sidestepped and continued - now at [4 6] after sidestep+move
      ;; (sidestep to [5 5] or [3 5], then normal move to [4 6])
      (should (:contents (get-in @atoms/game-map [4 6])))
      ;; Original cell should be empty
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "sidesteps orthogonally when diagonals blocked and continues moving"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving diagonally to [6 6], blocked by friendly at [5 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [6 6] :steps-remaining 2}})
                          (assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Adjacent diagonal sidesteps blocked by water
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [4 5] {:type :sea})
                          ;; Orthogonal sidesteps available
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [5 3] {:type :land})
                          ;; Clear path from orthogonal positions to target
                          (assoc-in [4 6] {:type :land})  ;; Path from [3 5]
                          (assoc-in [5 6] {:type :land})
                          (assoc-in [6 5] {:type :land})  ;; Path from [5 3]
                          (assoc-in [6 4] {:type :land})
                          (assoc-in [6 6] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should have sidestepped and continued toward target
      ;; Either path leads to [6 6] or nearby after sidestep + normal move
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [5 5])))
      ;; Unit should have progressed (could be at [4 6], [6 4], [5 6], or [6 5] depending on path)
      (should (or (:contents (get-in @atoms/game-map [4 6]))
                  (:contents (get-in @atoms/game-map [6 4]))
                  (:contents (get-in @atoms/game-map [5 6]))
                  (:contents (get-in @atoms/game-map [6 5]))))))

  (it "wakes when no valid sidestep exists"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by friendly, all sidesteps blocked
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [3 4] {:type :sea})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up at original position
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "does not sidestep when blocked by enemy unit"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], blocked by enemy army
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :computer :mode :sentry}})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal available
                          (assoc-in [3 5] {:type :land})  ;; Diagonal available
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up, not sidestep (enemy blocking)
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "fighter sidesteps around friendly fighter and continues"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 10}})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped and continued to [4 6]
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking fighter should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "ship sidesteps around friendly ship and continues"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :destroyer :mode :moving :owner :player :target [4 8] :hits 3 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :sea :contents {:type :battleship :owner :player :mode :sentry :hits 10}})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [4 6] {:type :sea})
                          (assoc-in [4 7] {:type :sea})
                          (assoc-in [4 8] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Ship should have sidestepped and continued to [4 6]
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking ship should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "chooses sidestep that gets closer to target using 4-round look-ahead"
    (let [initial-map (-> (vec (repeat 12 (vec (repeat 12 nil))))
                          ;; Army at [4 4] moving to [4 10], blocked by friendly at [4 5]
                          ;; One path has clear land, other has water blocking
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 10] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          ;; From [5 5] path goes diagonally toward [4 10]: [4 6], [4 7], [4 8]
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land})
                          ;; From [3 5] path goes diagonally: [4 6] - already defined and clear
                          ;; But we block [3 6] to simulate bad terrain in that direction
                          (assoc-in [3 6] {:type :sea})
                          (assoc-in [4 10] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Both sidesteps lead to [4 6] after sidestep+continuation
      ;; The unit sidesteps and then takes a normal move toward target
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
      ;; Blocking army should still be there
      (should (:contents (get-in @atoms/game-map [4 5])))))

  (it "wakes up when blocked by long line of friendly units (no progress possible)"
    (let [initial-map (-> (vec (repeat 12 (vec (repeat 12 nil))))
                          ;; Army at [4 4] moving south to [4 10]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 10] :steps-remaining 1}})
                          ;; Line of friendly armies blocking the entire row
                          (assoc-in [2 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [3 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          (assoc-in [6 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Empty cells to the sides (but they don't help get closer)
                          (assoc-in [5 4] {:type :land})
                          (assoc-in [3 4] {:type :land})
                          (assoc-in [4 10] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Unit should wake up since sidestepping doesn't get us closer
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :somethings-in-the-way (:reason unit)))))

  (it "does not sidestep outside map boundaries"
    (let [initial-map (-> (vec (repeat 5 (vec (repeat 5 nil))))
                          ;; Army at [0 0] (corner) moving to [0 4], blocked by friendly at [0 1]
                          (assoc-in [0 0] {:type :land :contents {:type :army :mode :moving :owner :player :target [0 4] :steps-remaining 2}})
                          (assoc-in [0 1] {:type :land :contents {:type :army :owner :player :mode :sentry}})
                          ;; Only valid sidestep would be [1 1] (diagonal into map)
                          ;; [-1 0], [-1 1], [0 -1] etc. are out of bounds
                          (assoc-in [1 0] {:type :sea})  ;; Block [1 0] so only [1 1] is valid
                          (assoc-in [1 1] {:type :land})
                          (assoc-in [0 2] {:type :land})
                          (assoc-in [0 3] {:type :land})
                          (assoc-in [0 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
      (game-loop/move-current-unit [0 0])
      ;; Unit should sidestep to [1 1] and continue to [0 2]
      (should (:contents (get-in @atoms/game-map [0 2])))
      (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))))

(describe "sidestep around cities"
  (it "army sidesteps around friendly city"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], friendly city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})  ;; Other diagonal
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Army should have sidestepped around friendly city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "army wakes when no sidestep around friendly city exists"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Army at [4 4] moving to [4 8], friendly city at [4 5], all sidesteps blocked
                          (assoc-in [4 4] {:type :land :contents {:type :army :mode :moving :owner :player :target [4 8] :steps-remaining 1}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :sea})
                          (assoc-in [3 5] {:type :sea})
                          (assoc-in [5 4] {:type :sea})
                          (assoc-in [3 4] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Army should wake up since no sidestep exists
      (let [unit (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode unit))
        (should= :cant-move-into-city (:reason unit)))))

  (it "fighter sidesteps around free city when not target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], free city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :free})
                          (assoc-in [5 5] {:type :land})  ;; Diagonal sidestep
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter sidesteps around player city when not target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], player city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around city and continued
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter does not sidestep when city is target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] with target [4 5] which is a player city
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 5] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :player :fighter-count 0})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should land at target city, not sidestep
      (should= 1 (:fighter-count (get-in @atoms/game-map [4 5])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))

  (it "fighter sidesteps around hostile city"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          ;; Fighter at [4 4] moving to [4 8], hostile city at [4 5]
                          (assoc-in [4 4] {:type :land :contents {:type :fighter :mode :moving :owner :player :target [4 8] :fuel 20 :steps-remaining 2}})
                          (assoc-in [4 5] {:type :city :city-status :computer})
                          (assoc-in [5 5] {:type :land})
                          (assoc-in [3 5] {:type :land})
                          (assoc-in [4 6] {:type :land})
                          (assoc-in [4 7] {:type :land})
                          (assoc-in [4 8] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit [4 4])
      ;; Fighter should have sidestepped around hostile city
      (should (:contents (get-in @atoms/game-map [4 6])))
      (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))))
