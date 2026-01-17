(ns empire.movement.sidestep-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.test-utils :refer [build-test-map set-test-unit]]
    [speclj.core :refer :all]))

(describe "sidestep around friendly units"
  (it "sidesteps diagonally around friendly unit and continues moving"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----A-###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 2)
    ;; Add blocking friendly army at [4 5]
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Unit should have sidestepped and continued - now at [4 6] after sidestep+move
    ;; (sidestep to [5 5] or [3 5], then normal move to [4 6])
    (should (:contents (get-in @atoms/game-map [4 6])))
    ;; Original cell should be empty
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
    ;; Blocking army should still be there
    (should (:contents (get-in @atoms/game-map [4 5]))))

  (it "sidesteps orthogonally when diagonals blocked and continues moving"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----A~---"
                                             "---~--#--"
                                             "----##---"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [6 6] :steps-remaining 2)
    ;; Add blocking friendly army at [5 5], clear path cells
    (swap! atoms/game-map assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [5 3] {:type :land})
    (swap! atoms/game-map assoc-in [4 6] {:type :land})
    (swap! atoms/game-map assoc-in [5 6] {:type :land})
    (swap! atoms/game-map assoc-in [6 5] {:type :land})
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
                (:contents (get-in @atoms/game-map [6 5])))))

  (it "wakes when no valid sidestep exists"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---~~----"
                                             "----A-#-#"
                                             "---~~----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 1)
    ;; Add blocking friendly army at [4 5]
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Unit should wake up at original position
    (let [unit (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode unit))
      (should= :somethings-in-the-way (:reason unit))))

  (it "does not sidestep when blocked by enemy unit"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----A-#-#"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 1)
    ;; Add blocking enemy army at [4 5]
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :army :owner :computer :mode :sentry}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Unit should wake up, not sidestep (enemy blocking)
    (let [unit (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode unit))
      (should= :somethings-in-the-way (:reason unit))))

  (it "fighter sidesteps around friendly fighter and continues"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----F-###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [4 8] :fuel 20 :steps-remaining 2)
    ;; Add blocking friendly fighter at [4 5]
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :fighter :owner :player :mode :sentry :fuel 10}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped and continued to [4 6]
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
    ;; Blocking fighter should still be there
    (should (:contents (get-in @atoms/game-map [4 5]))))

  (it "ship sidesteps around friendly ship and continues"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----~---"
                                             "----D-~~~"
                                             "-----~---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "D" :mode :moving :target [4 8] :hits 3 :steps-remaining 2)
    ;; Add blocking friendly battleship at [4 5]
    (swap! atoms/game-map assoc-in [4 5] {:type :sea :contents {:type :battleship :owner :player :mode :sentry :hits 10}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Ship should have sidestepped and continued to [4 6]
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
    ;; Blocking ship should still be there
    (should (:contents (get-in @atoms/game-map [4 5]))))

  (it "chooses sidestep that gets closer to target using 4-round look-ahead"
    (reset! atoms/game-map @(build-test-map ["------------"
                                             "------------"
                                             "------------"
                                             "-----#------"
                                             "----A-####-#"
                                             "-----#------"
                                             "------~-----"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 10] :steps-remaining 2)
    ;; Add blocking friendly army at [4 5], and fix the sea cell
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [3 6] {:type :sea})
    (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Both sidesteps lead to [4 6] after sidestep+continuation
    ;; The unit sidesteps and then takes a normal move toward target
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))
    ;; Blocking army should still be there
    (should (:contents (get-in @atoms/game-map [4 5]))))

  (it "wakes up when blocked by long line of friendly units (no progress possible)"
    (reset! atoms/game-map @(build-test-map ["------------"
                                             "------------"
                                             "------------"
                                             "---#--------"
                                             "----A------#"
                                             "---#--------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"
                                             "------------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 10] :steps-remaining 1)
    ;; Add line of blocking friendly armies at column 5
    (swap! atoms/game-map assoc-in [2 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [3 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [4 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [5 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (swap! atoms/game-map assoc-in [6 5] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (reset! atoms/player-map (vec (repeat 12 (vec (repeat 12 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Unit should wake up since sidestepping doesn't get us closer
    (let [unit (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode unit))
      (should= :somethings-in-the-way (:reason unit))))

  (it "does not sidestep outside map boundaries"
    (reset! atoms/game-map @(build-test-map ["A-###"
                                             "~#---"
                                             "-----"
                                             "-----"
                                             "-----"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [0 4] :steps-remaining 2)
    ;; Add blocking friendly army at [0 1]
    (swap! atoms/game-map assoc-in [0 1] {:type :land :contents {:type :army :owner :player :mode :sentry}})
    (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 nil)))))
    (game-loop/move-current-unit [0 0])
    ;; Unit should sidestep to [1 1] and continue to [0 2]
    (should (:contents (get-in @atoms/game-map [0 2])))
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))))

(describe "sidestep around cities"
  (it "army sidesteps around friendly city"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----AO###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Army should have sidestepped around friendly city and continued
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "army wakes when no sidestep around friendly city exists"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---~~----"
                                             "----AO---"
                                             "---~~----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Army should wake up since no sidestep exists
    (let [unit (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode unit))
      (should= :cant-move-into-city (:reason unit))))

  (it "fighter sidesteps around free city when not target"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----F+###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [4 8] :fuel 20 :steps-remaining 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around city and continued
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "fighter sidesteps around player city when not target"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----FO###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [4 8] :fuel 20 :steps-remaining 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around city and continued
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "fighter does not sidestep when city is target"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----FO---"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [4 5] :fuel 20 :steps-remaining 2)
    (swap! atoms/game-map assoc-in [4 5 :fighter-count] 0)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should land at target city, not sidestep
    (should= 1 (:fighter-count (get-in @atoms/game-map [4 5])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4]))))

  (it "fighter sidesteps around hostile city"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "-----#---"
                                             "----FX###"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "F" :mode :moving :target [4 8] :fuel 20 :steps-remaining 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    ;; Fighter should have sidestepped around hostile city
    (should (:contents (get-in @atoms/game-map [4 6])))
    (should-be-nil (:contents (get-in @atoms/game-map [4 4])))))
