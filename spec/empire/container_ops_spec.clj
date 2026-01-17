(ns empire.container-ops-spec
  (:require [empire.atoms :as atoms]
            [empire.container-ops :refer :all]
            [speclj.core :refer :all]))

(describe "load-adjacent-sentry-armies"
  (it "loads adjacent sentry armies onto transport"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                          (assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
                          (assoc-in [5 4] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (load-adjacent-sentry-armies [4 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= 2 (:army-count transport)))
      (should= nil (:contents (get-in @atoms/game-map [4 3])))
      (should= nil (:contents (get-in @atoms/game-map [5 4])))))

  (it "does not load awake armies onto transport"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                          (assoc-in [4 3] {:type :land :contents {:type :army :mode :awake :owner :player :hits 1}}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (load-adjacent-sentry-armies [4 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in @atoms/game-map [4 3])))))

  (it "wakes transport after loading armies if at beach"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1}})
                          (assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (load-adjacent-sentry-armies [4 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport))))))

(describe "wake-armies-on-transport"
  (it "wakes all armies and sets transport to sentry"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :awake :owner :player :hits 1 :army-count 2 :reason :transport-at-beach}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (wake-armies-on-transport [4 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= :sentry (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))))))

(describe "sleep-armies-on-transport"
  (it "puts armies to sleep and wakes transport"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (sleep-armies-on-transport [4 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-from-transport"
  (it "removes one army and decrements counts"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 3 :awake-armies 3}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (disembark-army-from-transport [4 4] [5 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))
            disembarked (:contents (get-in @atoms/game-map [5 4]))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked)))))

  (it "wakes transport when last army disembarks"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 1 :awake-armies 1}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (disembark-army-from-transport [4 4] [5 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "wakes transport when no more awake armies remain"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 1}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (disembark-army-from-transport [4 4] [5 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-with-target"
  (it "disembarks army and sets it moving toward extended target"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (disembark-army-with-target [4 4] [5 4] [8 4])
      (let [transport (:contents (get-in @atoms/game-map [4 4]))
            army (:contents (get-in @atoms/game-map [5 4]))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :moving (:mode army))
        (should= [8 4] (:target army))
        (should= 0 (:steps-remaining army))))))

(describe "disembark-army-to-explore"
  (it "disembarks army in explore mode"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :sentry :owner :player :hits 1 :army-count 2 :awake-armies 2}})
                          (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (let [result (disembark-army-to-explore [4 4] [5 4])]
        (should= [5 4] result)
        (let [transport (:contents (get-in @atoms/game-map [4 4]))
              army (:contents (get-in @atoms/game-map [5 4]))]
          (should= 1 (:army-count transport))
          (should= 1 (:awake-armies transport))
          (should= :army (:type army))
          (should= :explore (:mode army))
          (should= #{[5 4]} (:visited army)))))))

(describe "wake-fighters-on-carrier"
  (it "wakes all fighters and sets carrier to sentry"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :awake :owner :player :hits 8 :fighter-count 2}}))]
      (reset! atoms/game-map initial-map)
      (wake-fighters-on-carrier [4 4])
      (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
        (should= :sentry (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 2 (:awake-fighters carrier))))))

(describe "sleep-fighters-on-carrier"
  (it "puts fighters to sleep and wakes carrier"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 2 :awake-fighters 2}}))]
      (reset! atoms/game-map initial-map)
      (sleep-fighters-on-carrier [4 4])
      (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
        (should= :awake (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier))))))

(describe "launch-fighter-from-carrier"
  (it "removes fighter and places it at adjacent cell"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 2 :awake-fighters 2}})
                          (assoc-in [4 5] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (launch-fighter-from-carrier [4 4] [4 6])
      (let [carrier (:contents (get-in @atoms/game-map [4 4]))
            launched-fighter (:contents (get-in @atoms/game-map [4 5]))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= [4 6] (:target launched-fighter)))))

  (it "keeps carrier in sentry mode after last fighter launches"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                          (assoc-in [4 5] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (launch-fighter-from-carrier [4 4] [4 6])
      (let [carrier (:contents (get-in @atoms/game-map [4 4]))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "sets steps-remaining to speed minus one"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :sea :contents {:type :carrier :mode :sentry :owner :player :hits 8 :fighter-count 1 :awake-fighters 1}})
                          (assoc-in [4 5] {:type :sea}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (launch-fighter-from-carrier [4 4] [4 6])
      (let [fighter (:contents (get-in @atoms/game-map [4 5]))]
        (should= 7 (:steps-remaining fighter))))))

(describe "launch-fighter-from-airport"
  (it "removes awake fighter from airport and places it moving"
    (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                          (assoc-in [4 4] {:type :city :city-status :player :fighter-count 2 :awake-fighters 2})
                          (assoc-in [4 5] {:type :land}))]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (launch-fighter-from-airport [4 4] [4 6])
      (let [city (get-in @atoms/game-map [4 4])
            fighter (:contents city)]
        (should= 1 (:fighter-count city))
        (should= 1 (:awake-fighters city))
        (should= :fighter (:type fighter))
        (should= :moving (:mode fighter))
        (should= [4 6] (:target fighter))))))
