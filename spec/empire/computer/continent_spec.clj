(ns empire.computer.continent-spec
  "Tests for continent detection using fog-of-war flood-fill."
  (:require [speclj.core :refer :all]
            [empire.computer.continent :as continent]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "flood-fill-continent"
  (before (reset-all-atoms!))

  (it "finds all connected land cells"
    (reset! atoms/computer-map (build-test-map ["###"
                                                 "###"
                                                 "###"]))
    (let [cont (continent/flood-fill-continent [1 1])]
      (should= 9 (count cont))))

  (it "stops at sea boundaries"
    (reset! atoms/computer-map (build-test-map ["###~##"
                                                 "###~##"]))
    (let [cont (continent/flood-fill-continent [0 0])]
      ;; Should only find left 3x2 = 6 cells
      (should= 6 (count cont))
      (should-contain [0 0] cont)
      (should-contain [2 1] cont)
      (should-not-contain [4 0] cont)))

  (it "marks but does not expand through unexplored territory"
    ;; Map where middle column is unexplored (nil)
    (reset! atoms/computer-map [[{:type :land} nil {:type :land}]
                                 [{:type :land} nil {:type :land}]])
    (let [cont (continent/flood-fill-continent [0 0])]
      ;; Should find left column (2) + adjacent unexplored (2) = 4
      ;; Should NOT find right column
      (should= 4 (count cont))
      (should-contain [0 0] cont)
      (should-contain [1 0] cont)
      (should-contain [0 1] cont)  ; unexplored but adjacent
      (should-contain [1 1] cont)  ; unexplored but adjacent
      (should-not-contain [0 2] cont)
      (should-not-contain [1 2] cont)))

  (it "treats cities as land for connectivity"
    (reset! atoms/computer-map (build-test-map ["#X#"]))
    (let [cont (continent/flood-fill-continent [0 0])]
      (should= 3 (count cont))))

  (it "finds isolated landmass when separated by unexplored"
    ;; Two explored regions separated by unexplored
    (reset! atoms/computer-map [[{:type :land} nil nil {:type :land}]])
    (let [cont-left (continent/flood-fill-continent [0 0])
          cont-right (continent/flood-fill-continent [0 3])]
      ;; Left region sees 1 land + 1 adjacent unexplored
      (should= 2 (count cont-left))
      ;; Right region sees 1 land + 1 adjacent unexplored
      (should= 2 (count cont-right))
      ;; They are disjoint (different continents from fog-of-war perspective)
      (should-not-contain [0 3] cont-left)
      (should-not-contain [0 0] cont-right))))

(describe "scan-continent"
  (before (reset-all-atoms!))

  (it "counts unexplored cells"
    (reset! atoms/computer-map [[{:type :land} nil]
                                 [{:type :land} nil]])
    (reset! atoms/game-map [[{:type :land} {:type :land}]
                             [{:type :land} {:type :land}]])
    (let [cont (continent/flood-fill-continent [0 0])
          counts (continent/scan-continent cont)]
      (should= 2 (:unexplored counts))))

  (it "counts cities by owner"
    (reset! atoms/computer-map (build-test-map ["X+O#"]))
    (reset! atoms/game-map (build-test-map ["X+O#"]))
    (let [cont (continent/flood-fill-continent [0 0])
          counts (continent/scan-continent cont)]
      (should= 1 (:computer-cities counts))
      (should= 1 (:free-cities counts))
      (should= 1 (:player-cities counts))))

  (it "counts units by owner"
    (reset! atoms/computer-map (build-test-map ["aA#"]))
    (reset! atoms/game-map (build-test-map ["aA#"]))
    (let [cont (continent/flood-fill-continent [0 0])
          counts (continent/scan-continent cont)]
      (should= 1 (:computer-units counts))
      (should= 1 (:player-units counts)))))

(describe "has-land-objective?"
  (it "returns true when unexplored territory exists"
    (should (continent/has-land-objective? {:unexplored 5 :free-cities 0 :player-cities 0})))

  (it "returns true when free cities exist"
    (should (continent/has-land-objective? {:unexplored 0 :free-cities 1 :player-cities 0})))

  (it "returns true when player cities exist"
    (should (continent/has-land-objective? {:unexplored 0 :free-cities 0 :player-cities 2})))

  (it "returns false when nothing to explore or attack"
    (should-not (continent/has-land-objective? {:unexplored 0 :free-cities 0 :player-cities 0}))))

(describe "find-unexplored-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest unexplored cell"
    (reset! atoms/computer-map [[{:type :land} {:type :land} nil]
                                 [{:type :land} {:type :land} {:type :land}]])
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-unexplored-on-continent [0 0] cont)]
      (should= [0 2] nearest)))

  (it "returns nil when no unexplored on continent"
    (reset! atoms/computer-map (build-test-map ["###"]))
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-unexplored-on-continent [0 0] cont)]
      (should-be-nil nearest))))

(describe "find-free-city-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest free city"
    (reset! atoms/computer-map (build-test-map ["##+"]))
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-free-city-on-continent [0 0] cont)]
      (should= [2 0] nearest)))

  (it "returns nil when no free city on continent"
    (reset! atoms/computer-map (build-test-map ["##X"]))
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-free-city-on-continent [0 0] cont)]
      (should-be-nil nearest))))

(describe "find-player-city-on-continent"
  (before (reset-all-atoms!))

  (it "finds nearest player city"
    (reset! atoms/computer-map (build-test-map ["##O"]))
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-player-city-on-continent [0 0] cont)]
      (should= [2 0] nearest)))

  (it "returns nil when no player city on continent"
    (reset! atoms/computer-map (build-test-map ["##X"]))
    (let [cont (continent/flood-fill-continent [0 0])
          nearest (continent/find-player-city-on-continent [0 0] cont)]
      (should-be-nil nearest))))
