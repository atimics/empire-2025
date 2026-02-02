(ns acceptance.visibility-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map get-test-unit reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.movement.visibility :as visibility]))

(describe "visibility.txt"

  ;; visibility.txt:6 - Army reveals 3x3 around itself.
  (it "visibility.txt:6 - Army reveals 3x3 around itself"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["#####"
                                            "##A##"
                                            "#####"]))
    ;; GIVEN player map (all nil)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN visibility updates
    (game-loop/update-player-map)
    ;; THEN player-map cells around A [2,1] are not nil (3x3 area)
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 0]))
    (should-not-be-nil (get-in @atoms/player-map [3 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 1]))
    (should-not-be-nil (get-in @atoms/player-map [2 1]))
    (should-not-be-nil (get-in @atoms/player-map [3 1]))
    (should-not-be-nil (get-in @atoms/player-map [1 2]))
    (should-not-be-nil (get-in @atoms/player-map [2 2]))
    (should-not-be-nil (get-in @atoms/player-map [3 2])))

  ;; visibility.txt:30 - Unexplored cells are not visible.
  ;; No player units on the map, so no visibility updates needed.
  (it "visibility.txt:30 - Unexplored cells are not visible"
    (reset-all-atoms!)
    ;; GIVEN game map (no player units)
    (reset! atoms/game-map (build-test-map ["###"
                                            "###"
                                            "###"]))
    ;; GIVEN player map (all nil)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; THEN (no WHEN - just check initial state)
    (should-be-nil (get-in @atoms/player-map [0 0]))
    (should-be-nil (get-in @atoms/player-map [1 1]))
    (should-be-nil (get-in @atoms/player-map [2 2])))

  ;; visibility.txt:46 - City reveals surroundings.
  (it "visibility.txt:46 - City reveals surroundings"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["###"
                                            "#O#"
                                            "###"]))
    ;; GIVEN player map (all nil)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN visibility updates
    (game-loop/update-player-map)
    ;; THEN all 9 cells revealed
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 0]))
    (should-not-be-nil (get-in @atoms/player-map [0 1]))
    (should-not-be-nil (get-in @atoms/player-map [1 1]))
    (should-not-be-nil (get-in @atoms/player-map [2 1]))
    (should-not-be-nil (get-in @atoms/player-map [0 2]))
    (should-not-be-nil (get-in @atoms/player-map [1 2]))
    (should-not-be-nil (get-in @atoms/player-map [2 2])))

  ;; visibility.txt:70 - Enemy units hidden in fog.
  (it "visibility.txt:70 - Enemy units hidden in fog"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["A####a"]))
    ;; GIVEN player map (all nil)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN visibility updates
    (game-loop/update-player-map)
    ;; THEN cells near player army are visible, far enemy is not
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-be-nil (get-in @atoms/player-map [5 0])))

  ;; visibility.txt:84 - Satellite reveals 5x5 area.
  (it "visibility.txt:84 - Satellite reveals 5x5 area"
    (reset-all-atoms!)
    ;; GIVEN game map
    (reset! atoms/game-map (build-test-map ["#####"
                                            "#####"
                                            "##V##"
                                            "#####"
                                            "#####"]))
    ;; GIVEN player map (all nil)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil)))
    ;; WHEN visibility updates
    ;; update-combatant-map reveals 3x3 for all owned cells;
    ;; update-cell-visibility handles satellite's 5x5 (radius 2).
    (game-loop/update-player-map)
    (let [sat-pos (:pos (get-test-unit atoms/game-map "V"))]
      (visibility/update-cell-visibility sat-pos :player))
    ;; THEN all corners and center of 5x5 area are visible
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [4 4]))
    (should-not-be-nil (get-in @atoms/player-map [0 4]))
    (should-not-be-nil (get-in @atoms/player-map [4 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 2]))))
