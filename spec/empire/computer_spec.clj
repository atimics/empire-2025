(ns empire.computer-spec
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.computer :as computer]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms!]]))

(describe "build-computer-items"
  (before (reset-all-atoms!))

  (it "returns computer city coordinates"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "returns computer unit coordinates"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 0] items)))

  (it "does not return player cities"
    (reset! atoms/game-map (build-test-map ["OX"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "does not return player units"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "does not return empty land"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-not-contain [0 0] items)))

  (it "returns all computer ship types"
    (reset! atoms/game-map (build-test-map ["tdpsbc"]))
    (let [items (game-loop/build-computer-items)]
      (should= 6 (count items)))))

(describe "decide-army-move"
  (before (reset-all-atoms!))

  (it "returns nil when no valid moves exist"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/decide-army-move [0 1])))

  (it "returns nil when surrounded by sea (no passable moves)"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Army surrounded by sea should have no valid move
    (should-be-nil (computer/decide-army-move [1 1])))

  (it "army survives multiple moves without enemies"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##a##"
                                             "#####"
                                             "#####"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Move army 10 times
    (dotimes [_ 10]
      (let [army-pos (first (for [i (range 5)
                                  j (range 5)
                                  :let [cell (get-in @atoms/game-map [i j])]
                                  :when (and (:contents cell)
                                             (= :army (:type (:contents cell))))]
                              [i j]))]
        (when army-pos
          (computer/process-computer-unit army-pos))))
    ;; Army should still exist somewhere on the map
    (let [army-count (count (for [i (range 5)
                                  j (range 5)
                                  :let [cell (get-in @atoms/game-map [i j])]
                                  :when (and (:contents cell)
                                             (= :army (:type (:contents cell))))]
                              [i j]))]
      (should= 1 army-count)))

  (it "army dies when attempting to conquer free city"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Army should attack the free city
    (computer/process-computer-unit [0 0])
    ;; Army is always removed during conquest attempt
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    ;; City should be either conquered or still free
    (should (#{:computer :free} (:city-status (get-in @atoms/game-map [0 1])))))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["aA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "returns adjacent free city position to attack"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "returns adjacent player city position to attack"
    (reset! atoms/game-map (build-test-map ["aO"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-army-move [0 0])))

  (it "moves toward visible free city"
    (reset! atoms/game-map (build-test-map ["a##+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 0])]
      (should= [0 1] move)))

  (it "moves toward visible player city when no free city"
    (reset! atoms/game-map (build-test-map ["a##O"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 0])]
      (should= [0 1] move)))

  (it "returns a valid land cell when exploring"
    (reset! atoms/game-map (build-test-map ["#a#"
                                             "###"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 1])]
      (should-not-be-nil move)
      (should= :land (:type (get-in @atoms/game-map move)))))

  (it "does not move onto sea"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~#~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-army-move [0 1])]
      (should= [1 1] move)))

  (it "does not move onto friendly units"
    (reset! atoms/game-map (build-test-map ["#a#"
                                             "a#a"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Army at [0 1] has neighbors: [0 0], [0 2], [1 0] (army), [1 1], [1 2] (army)
    ;; Should move to [0 0], [0 2], or [1 1] but not [1 0] or [1 2]
    (let [move (computer/decide-army-move [0 1])]
      (should-not-be-nil move)
      (should (#{[0 0] [0 2] [1 1]} move)))))

(describe "process-computer-unit"
  (before (reset-all-atoms!))

  (it "moves army to adjacent empty land"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Army should have moved from [0 0] to [0 1]
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "army attacks adjacent player unit"
    (reset! atoms/game-map (build-test-map ["aA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Combat occurred - one unit should be dead
    (let [cell0 (get-in @atoms/game-map [0 0])
          cell1 (get-in @atoms/game-map [0 1])
          units (filter some? [(:contents cell0) (:contents cell1)])]
      (should= 1 (count units))))

  (it "army attacks adjacent free city"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 0])
    ;; Army should be removed (conquest attempt removes army win or lose)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    ;; City should be either conquered (:computer) or still free
    (should (#{:computer :free} (:city-status (get-in @atoms/game-map [0 1])))))

  (it "does nothing when no valid moves"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (computer/process-computer-unit [0 1])
    ;; Army should still be at [0 1]
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "returns nil after move (unit done for this turn)"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      ;; But the unit should have moved
      (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))))

  (it "returns nil when no move possible"
    (reset! atoms/game-map (build-test-map ["~a~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 1])]
      (should-be-nil result))))

(describe "game loop integration"
  (before (reset-all-atoms!))

  (it "start-new-round builds computer-items list"
    (reset! atoms/game-map (build-test-map ["OaX"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (game-loop/start-new-round)
    ;; Should have computer army and computer city
    (should-contain [0 1] @atoms/computer-items)
    (should-contain [0 2] @atoms/computer-items))

  (it "start-new-round builds both player and computer items"
    (reset! atoms/game-map (build-test-map ["OAaX"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (game-loop/start-new-round)
    (should (seq @atoms/player-items))
    (should (seq @atoms/computer-items)))

  (it "advance-game does not start new round when computer-items not empty"
    (reset! atoms/game-map (build-test-map ["#a#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 1]])
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    ;; Should not have started new round
    (should= 5 @atoms/round-number))

  (it "advance-game processes computer unit when player-items empty"
    (reset! atoms/game-map (build-test-map ["#a#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 1]])
    (game-loop/advance-game)
    ;; Computer army should have moved (from [0 1] to [0 0] or [0 2])
    (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
    ;; Computer-items should be empty (unit done moving)
    (should (empty? @atoms/computer-items)))

  (it "advance-game starts new round when both lists empty"
    (reset! atoms/game-map (build-test-map ["#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/round-number 5)
    (game-loop/advance-game)
    (should= 6 @atoms/round-number)))

(describe "decide-ship-move"
  (before (reset-all-atoms!))

  (it "returns nil when no valid moves exist"
    (reset! atoms/game-map (build-test-map ["#d#"
                                             "###"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer/decide-ship-move [0 1] :destroyer)))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["dD"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-ship-move [0 0] :destroyer)))

  (it "moves toward visible player unit"
    (reset! atoms/game-map (build-test-map ["d~~D"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 0] :destroyer)]
      (should= [0 1] move)))

  (it "returns valid sea cell when exploring"
    (reset! atoms/game-map (build-test-map ["~d~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 1] :destroyer)]
      (should-not-be-nil move)
      (should= :sea (:type (get-in @atoms/game-map move)))))

  (it "does not move onto land"
    (reset! atoms/game-map (build-test-map ["#d#"
                                             "#~#"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [move (computer/decide-ship-move [0 1] :destroyer)]
      (should= [1 1] move))))

(describe "decide-fighter-move"
  (before (reset-all-atoms!))

  (it "returns adjacent player unit position to attack"
    (reset! atoms/game-map (build-test-map ["fA"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= [0 1] (computer/decide-fighter-move [0 0] 10)))

  (it "moves toward unexplored when fuel is sufficient"
    (reset! atoms/game-map (build-test-map ["Xf#"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; With computer city at [0 0] and fighter at [0 1] with plenty of fuel
    (let [move (computer/decide-fighter-move [0 1] 10)]
      ;; Should move toward unexplored or explore
      (should-not-be-nil move)))

  (it "returns to nearest friendly city when fuel is low"
    (reset! atoms/game-map (build-test-map ["X#f"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Fighter at [0 2], city at [0 0], fuel = 2 (just enough to get back)
    (let [move (computer/decide-fighter-move [0 2] 2)]
      ;; Should move toward city
      (should= [0 1] move)))

  (it "returns nil when no valid moves"
    (reset! atoms/game-map (build-test-map ["~f~"
                                             "~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    ;; Fighter can fly over sea but prefers land/cities
    ;; With no land neighbors, should still return a move (fly over sea)
    (let [move (computer/decide-fighter-move [0 1] 10)]
      ;; Fighters can move over sea
      (should-not-be-nil move))))

(describe "decide-production"
  (before (reset-all-atoms!))

  (it "returns :army for Phase 1"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should= :army (computer/decide-production [0 0]))))

(describe "process-computer-city"
  (before (reset-all-atoms!))

  (it "sets production when city has none"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (computer/process-computer-city [0 0])
    (should (@atoms/production [0 0])))

  (it "does not change production when city already has production"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :fighter :remaining-rounds 10}})
    (computer/process-computer-city [0 0])
    (should= :fighter (:item (@atoms/production [0 0]))))

  (it "sets production to army"
    (reset! atoms/game-map (build-test-map ["X"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (computer/process-computer-city [0 0])
    (should= :army (:item (@atoms/production [0 0])))))

(describe "computer production full cycle"
  (before (reset-all-atoms!))

  (it "sets production on first round processing"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 0]])
    ;; Process computer city
    (game-loop/advance-game)
    ;; Production should be set
    (should= :army (:item (@atoms/production [0 0])))
    (should= 5 (:remaining-rounds (@atoms/production [0 0]))))

  (it "decrements production each round"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 3}})
    ;; Start new round calls update-production
    (game-loop/start-new-round)
    (should= 2 (:remaining-rounds (@atoms/production [0 0]))))

  (it "produces army when remaining-rounds reaches zero"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 1}})
    ;; Start new round - production completes
    (game-loop/start-new-round)
    ;; Army should be on the city
    (let [city-cell (get-in @atoms/game-map [0 0])]
      (should= :army (:type (:contents city-cell)))
      (should= :computer (:owner (:contents city-cell)))))

  (it "army moves to friendly city when all other neighbors blocked"
    ;; Set up scenario: army at [1,1] with other armies blocking most neighbors
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~###~"
                                             "~#X#~"
                                             "~###~"
                                             "~~~~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/production {})
    ;; Put armies at specific positions - blocking [1,2] and [2,1]
    (swap! atoms/game-map assoc-in [1 1 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    (swap! atoms/game-map assoc-in [1 2 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    (swap! atoms/game-map assoc-in [2 1 :contents] {:type :army :owner :computer :hits 1 :mode :awake})
    ;; Process the army at [1,1] - it should move to [2,2] (the friendly city)
    (computer/process-computer-unit [1 1])
    ;; Army should have moved to [2,2] (the city) not died trying to conquer it
    (should= :army (:type (:contents (get-in @atoms/game-map [2 2])))))

  (it "army moves off city allowing next production cycle"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    ;; Put army on city with production ready to reset
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :army :owner :computer :hits 1})
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 0]])
    ;; Process the army (should move to [0 1])
    (game-loop/advance-game)
    ;; Army should have moved off city
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "counts produced vs remaining armies on isolated island"
    ;; Small island with computer city surrounded by sea
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~###~"
                                             "~#X#~"
                                             "~###~"
                                             "~~~~~"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/round-number 0)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    ;; Count function for armies on map
    (let [count-armies (fn []
                         (count (for [i (range 5)
                                      j (range 5)
                                      :let [cell (get-in @atoms/game-map [i j])]
                                      :when (and (:contents cell)
                                                 (= :army (:type (:contents cell)))
                                                 (= :computer (:owner (:contents cell))))]
                                  [i j])))
          armies-produced (atom 0)]
      ;; Run 30 rounds (enough to produce 5 armies with 5-round build time)
      (dotimes [_ 30]
        (let [before-count (count-armies)]
          (game-loop/start-new-round)
          ;; Process computer items
          (while (seq @atoms/computer-items)
            (let [coords (first @atoms/computer-items)
                  cell (get-in @atoms/game-map coords)]
              (when (and (= (:type cell) :city) (= (:city-status cell) :computer))
                (computer/process-computer-city coords))
              (when (= (:owner (:contents cell)) :computer)
                (computer/process-computer-unit coords))
              (swap! atoms/computer-items rest)))
          (let [after-count (count-armies)]
            (when (> after-count before-count)
              (swap! armies-produced + (- after-count before-count))))))
      ;; Should have produced 5 armies (30 rounds / 5 rounds per army = 6, minus first round to set production)
      ;; The key assertion: produced armies should equal remaining armies (no unexplained losses)
      (let [final-count (count-armies)]
        (should (>= @armies-produced 4))
        (should= @armies-produced final-count)))))
