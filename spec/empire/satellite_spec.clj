(ns empire.satellite-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.satellite :refer :all]))

(describe "calculate-satellite-target"
  (it "extends target to boundary in direction of travel"
    (reset! atoms/game-map (vec (repeat 10 (vec (repeat 10 {:type :land})))))
    ;; From [2 2] toward [5 5] should extend to [9 9]
    (should= [9 9] (calculate-satellite-target [2 2] [5 5])))

  (it "extends target to right edge when moving east"
    (reset! atoms/game-map (vec (repeat 10 (vec (repeat 10 {:type :land})))))
    (should= [5 9] (calculate-satellite-target [5 3] [5 5])))

  (it "extends target to bottom edge when moving south"
    (reset! atoms/game-map (vec (repeat 10 (vec (repeat 10 {:type :land})))))
    (should= [9 5] (calculate-satellite-target [3 5] [5 5])))

  (it "extends target to top-left corner when moving northwest"
    (reset! atoms/game-map (vec (repeat 10 (vec (repeat 10 {:type :land})))))
    (should= [0 0] (calculate-satellite-target [5 5] [3 3]))))

(describe "move-satellite"
  (it "does not move without a target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (let [result (move-satellite [5 5])]
        (should= [5 5] result)
        (should (:contents (get-in @atoms/game-map [5 5])))
        (should-be-nil (:target (:contents (get-in @atoms/game-map [5 5])))))))

  (it "moves toward its target"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 5] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (let [result (move-satellite [5 5])]
        (should= [6 6] result)
        (should (:contents (get-in @atoms/game-map [6 6])))
        (should-be-nil (:contents (get-in @atoms/game-map [5 5])))
        (should= [9 9] (:target (:contents (get-in @atoms/game-map [6 6])))))))

  (it "moves horizontally when target is directly east"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 3] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (let [result (move-satellite [5 3])]
        (should= [5 4] result)
        (should (:contents (get-in @atoms/game-map [5 4])))
        (should-be-nil (:contents (get-in @atoms/game-map [5 3]))))))

  (it "moves vertically when target is directly south"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [3 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (let [result (move-satellite [3 5])]
        (should= [4 5] result)
        (should (:contents (get-in @atoms/game-map [4 5])))
        (should-be-nil (:contents (get-in @atoms/game-map [3 5]))))))

  (it "gets new target on opposite boundary when reaching right edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [5 9] {:type :land :contents {:type :satellite :owner :player :target [5 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [5 9])
      (let [sat (:contents (get-in @atoms/game-map [5 9]))]
        (should sat)
        (should= 0 (second (:target sat))))))

  (it "gets new target on opposite boundary when reaching bottom edge"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 5] {:type :land :contents {:type :satellite :owner :player :target [9 5] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 5])
      (let [sat (:contents (get-in @atoms/game-map [9 5]))]
        (should sat)
        (should= 0 (first (:target sat))))))

  (it "gets new target on one of opposite boundaries when at corner"
    (let [initial-map (-> (vec (repeat 10 (vec (repeat 10 {:type :land}))))
                          (assoc-in [9 9] {:type :land :contents {:type :satellite :owner :player :target [9 9] :turns-remaining 50}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 10 (vec (repeat 10 nil)))))
      (move-satellite [9 9])
      (let [sat (:contents (get-in @atoms/game-map [9 9]))
            [tx ty] (:target sat)]
        (should sat)
        (should (or (= tx 0) (= ty 0)))))))
