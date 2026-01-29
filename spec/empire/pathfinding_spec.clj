(ns empire.pathfinding-spec
  (:require [speclj.core :refer :all]
            [empire.pathfinding :as pathfinding]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "heuristic"
  (it "returns Manhattan distance"
    (should= 5 (pathfinding/heuristic [0 0] [3 2])))

  (it "returns 0 for same position"
    (should= 0 (pathfinding/heuristic [5 5] [5 5])))

  (it "handles negative coordinates correctly"
    (should= 4 (pathfinding/heuristic [0 0] [-2 -2]))))

(describe "passable?"
  (before (reset-all-atoms!))

  (it "returns false for unexplored cells"
    (should-not (pathfinding/passable? :army {:type :unexplored})))

  (it "returns false for nil cell"
    (should-not (pathfinding/passable? :army nil)))

  (it "returns true for land cell with army"
    (should (pathfinding/passable? :army {:type :land})))

  (it "returns false for sea cell with army"
    (should-not (pathfinding/passable? :army {:type :sea})))

  (it "returns true for sea cell with ship"
    (should (pathfinding/passable? :destroyer {:type :sea})))

  (it "returns false for land cell with ship"
    (should-not (pathfinding/passable? :destroyer {:type :land})))

  (it "returns true for any explored cell with fighter"
    (should (pathfinding/passable? :fighter {:type :land}))
    (should (pathfinding/passable? :fighter {:type :sea}))
    (should (pathfinding/passable? :fighter {:type :city}))))

(describe "get-passable-neighbors"
  (before (reset-all-atoms!))

  (it "returns land neighbors for army"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#a#"
                                             "~~~"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :army @atoms/game-map)]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [0 2] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [1 2] neighbors)))

  (it "returns sea neighbors for ship"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~d~"
                                             "###"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :destroyer @atoms/game-map)]
      (should= 5 (count neighbors))
      (should-contain [0 0] neighbors)
      (should-contain [0 1] neighbors)
      (should-contain [0 2] neighbors)
      (should-contain [1 0] neighbors)
      (should-contain [1 2] neighbors)))

  (it "returns all neighbors for fighter"
    (reset! atoms/game-map (build-test-map ["#~#"
                                             "~f~"
                                             "#~#"]))
    (let [neighbors (pathfinding/get-passable-neighbors [1 1] :fighter @atoms/game-map)]
      (should= 8 (count neighbors)))))

(describe "a-star"
  (before (reset-all-atoms!))

  (it "finds direct path on clear terrain"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (let [path (pathfinding/a-star [0 0] [0 2] :army @atoms/game-map)]
      (should= [[0 0] [0 1] [0 2]] path)))

  (it "returns just start position when already at goal"
    (reset! atoms/game-map (build-test-map ["a"]))
    (let [path (pathfinding/a-star [0 0] [0 0] :army @atoms/game-map)]
      (should= [[0 0]] path)))

  (it "navigates around obstacles"
    (reset! atoms/game-map (build-test-map ["#~#"
                                             "###"
                                             "#a#"]))
    ;; Army at [2 1] wants to reach [0 0], must go around the sea
    (let [path (pathfinding/a-star [2 1] [0 0] :army @atoms/game-map)]
      (should-not-be-nil path)
      (should= [2 1] (first path))
      (should= [0 0] (last path))
      ;; Path should not pass through sea at [0 1]
      (should-not-contain [0 1] path)))

  (it "keeps armies on land"
    (reset! atoms/game-map (build-test-map ["a~~#"]))
    ;; Army cannot cross water
    (let [path (pathfinding/a-star [0 0] [0 3] :army @atoms/game-map)]
      (should-be-nil path)))

  (it "keeps ships on sea"
    (reset! atoms/game-map (build-test-map ["d##~"]))
    ;; Ship cannot cross land
    (let [path (pathfinding/a-star [0 0] [0 3] :destroyer @atoms/game-map)]
      (should-be-nil path)))

  (it "allows fighters to fly over any terrain"
    (reset! atoms/game-map (build-test-map ["f~~#"]))
    (let [path (pathfinding/a-star [0 0] [0 3] :fighter @atoms/game-map)]
      (should-not-be-nil path)
      (should= [0 0] (first path))
      (should= [0 3] (last path))))

  (it "returns nil for unreachable goal"
    (reset! atoms/game-map (build-test-map ["a~~"
                                             "~~~"
                                             "~~#"]))
    ;; Army on island, land at [2 2] unreachable
    (let [path (pathfinding/a-star [0 0] [2 2] :army @atoms/game-map)]
      (should-be-nil path)))

  (it "finds path on larger map"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#~~~#"
                                             "#~#~#"
                                             "#~~~#"
                                             "a####"]))
    ;; Army at [4 0] needs to reach [0 4]
    (let [path (pathfinding/a-star [4 0] [0 4] :army @atoms/game-map)]
      (should-not-be-nil path)
      (should= [4 0] (first path))
      (should= [0 4] (last path)))))

(describe "next-step"
  (before (reset-all-atoms!))

  (it "returns first step of computed path"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (let [step (pathfinding/next-step [0 0] [0 2] :army)]
      (should= [0 1] step)))

  (it "returns nil for unreachable goal"
    (reset! atoms/game-map (build-test-map ["a~~#"]))
    (let [step (pathfinding/next-step [0 0] [0 3] :army)]
      (should-be-nil step)))

  (it "returns nil when already at goal"
    (reset! atoms/game-map (build-test-map ["a"]))
    (let [step (pathfinding/next-step [0 0] [0 0] :army)]
      (should-be-nil step))))

(describe "path caching"
  (before
    (reset-all-atoms!)
    (pathfinding/clear-path-cache))

  (it "caches computed paths"
    (reset! atoms/game-map (build-test-map ["a####"]))
    ;; First call computes path
    (let [step1 (pathfinding/next-step [0 0] [0 4] :army)
          ;; Second call should use cached path
          step2 (pathfinding/next-step [0 0] [0 4] :army)]
      (should= [0 1] step1)
      (should= [0 1] step2)))

  (it "clear-path-cache resets the cache"
    (reset! atoms/game-map (build-test-map ["a##"]))
    (pathfinding/next-step [0 0] [0 2] :army)
    (pathfinding/clear-path-cache)
    ;; Cache should be empty now, so this should work fresh
    (reset! atoms/game-map (build-test-map ["a~#"]))
    ;; Path should now be nil since terrain changed
    (let [step (pathfinding/next-step [0 0] [0 2] :army)]
      (should-be-nil step))))

(describe "find-nearest-unexplored"
  (before (reset-all-atoms!))

  (it "finds sea cell adjacent to unexplored territory"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"
                                                "~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [0 0] :transport)]
      (should-not-be-nil target)
      ;; Target should be adjacent to the unexplored cell [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]]))))

  (it "returns nil when no unexplored territory exists"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                "~~~"]))
    (should-be-nil (pathfinding/find-nearest-unexplored [0 0] :transport)))

  (it "does not exhibit northwest bias"
    ;; Transport at center [2,2], unexplored only in SE corner
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"
                                            "~~~~~"]))
    (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~~"
                                                "~~~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [2 2] :transport)]
      (should-not-be-nil target)
      ;; Target should be south-east of start, not northwest
      (should (>= (first target) 2))
      (should (>= (second target) 2))))

  (it "skips start position even if adjacent to unexplored"
    (reset! atoms/game-map (build-test-map ["~~"
                                            "~~"]))
    (reset! atoms/computer-map (build-test-map ["~-"
                                                "~~"]))
    ;; Start [0,0] is adjacent to unexplored [0,1] on computer-map
    ;; But [0,1] is sea on game-map, so BFS should find [0,1] as target
    (let [target (pathfinding/find-nearest-unexplored [0 0] :transport)]
      ;; Should find a cell adjacent to unexplored, but not start
      (should-not-be-nil target)
      (should-not= [0 0] target)))

  (it "returns nil when only start is adjacent to unexplored"
    ;; Only one sea cell, surrounded by land. Start is the only sea cell.
    (reset! atoms/game-map (build-test-map ["#~#"]))
    (reset! atoms/computer-map (build-test-map ["#~-"]))
    ;; Start [0,1] is adjacent to unexplored [0,2] but [0,2] is not passable sea
    ;; No other sea cells to explore toward
    (should-be-nil (pathfinding/find-nearest-unexplored [0 1] :transport)))

  (it "works with fighter unit type over all terrain"
    ;; Fighter can traverse land and sea
    (reset! atoms/game-map (build-test-map ["##~"
                                            "#~~"
                                            "~~~"]))
    (reset! atoms/computer-map (build-test-map ["##~"
                                                "#~~"
                                                "~~-"]))
    (let [target (pathfinding/find-nearest-unexplored [0 0] :fighter)]
      (should-not-be-nil target)
      ;; Should find a cell adjacent to [2,2]
      (should (some #{target} [[1 1] [1 2] [2 1]])))))

(describe "find-nearest-unload-position"
  (before (reset-all-atoms!))

  (it "finds nearest sea cell adjacent to target-continent land"
    ;; Two continents separated by sea
    ;; Target continent at rows 4-5
    (reset! atoms/game-map (build-test-map ["###"
                                            "###"
                                            "~~~"
                                            "~~~"
                                            "###"
                                            "###"]))
    (let [target-continent #{[4 0] [4 1] [4 2] [5 0] [5 1] [5 2]}
          result (pathfinding/find-nearest-unload-position [2 1] target-continent)]
      ;; Should find a sea cell in row 3 adjacent to land in row 4
      (should-not-be-nil result)
      (should= 3 (first result))))

  (it "returns nil when target-continent land is unreachable"
    ;; Target continent positions don't exist near any reachable sea
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"]))
    (let [target-continent #{[10 10] [10 11]}
          result (pathfinding/find-nearest-unload-position [1 1] target-continent)]
      (should-be-nil result)))

  (it "ignores non-target-continent land"
    ;; Two landmasses: row 0 and row 4. Only row 4 is target.
    ;; BFS should skip row 1 sea (adjacent to non-target row 0 land)
    ;; and find row 3 sea (adjacent to target row 4 land).
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "###"]))
    (let [target-continent #{[4 0] [4 1] [4 2]}
          result (pathfinding/find-nearest-unload-position [1 1] target-continent)]
      (should-not-be-nil result)
      (should= 3 (first result))))

  (it "skips occupied sea cells as unload destinations"
    ;; Target continent at row 4. Sea cell [3,1] is occupied by enemy ship.
    (reset! atoms/game-map (build-test-map ["###"
                                            "~~~"
                                            "~~~"
                                            "~d~"
                                            "###"]))
    (let [target-continent #{[4 0] [4 1] [4 2]}
          result (pathfinding/find-nearest-unload-position [2 1] target-continent)]
      ;; Should skip [3,1] (occupied) and find [3,0] or [3,2]
      (should-not-be-nil result)
      (should= 3 (first result))
      (should-not= [3 1] result)))

  (it "finds globally nearest position on target continent"
    ;; Target continent connected land at rows 4 and 8 (same continent via land).
    ;; Transport at row 2 - should find row 3 (nearest to row 4 target land).
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"
                                            "~~~"
                                            "~~~"
                                            "O##"
                                            "###"
                                            "###"
                                            "###"
                                            "###"]))
    (let [target-continent #{[4 0] [4 1] [4 2] [5 0] [5 1] [5 2]
                             [6 0] [6 1] [6 2] [7 0] [7 1] [7 2]
                             [8 0] [8 1] [8 2]}
          result (pathfinding/find-nearest-unload-position [2 1] target-continent)]
      (should-not-be-nil result)
      (should= 3 (first result)))))
