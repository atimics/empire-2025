(ns acceptance.visibility-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.ui.input :as input]))

(describe "visibility.txt"

  (it "visibility.txt:6 - Army reveals 3x3 around itself"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#####" "##A##" "#####"]))
    (reset! atoms/player-map (build-test-map ["....." "....." "....."]))
    (game-loop/update-player-map)
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 0]))
    (should-not-be-nil (get-in @atoms/player-map [3 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 1]))
    (should-not-be-nil (get-in @atoms/player-map [2 1]))
    (should-not-be-nil (get-in @atoms/player-map [3 1]))
    (should-not-be-nil (get-in @atoms/player-map [1 2]))
    (should-not-be-nil (get-in @atoms/player-map [2 2]))
    (should-not-be-nil (get-in @atoms/player-map [3 2])))

  (it "visibility.txt:30 - Unexplored cells are not visible"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "###" "###"]))
    (reset! atoms/player-map (build-test-map ["..." "..." "..."]))
    (should-be-nil (get-in @atoms/player-map [0 0]))
    (should-be-nil (get-in @atoms/player-map [1 1]))
    (should-be-nil (get-in @atoms/player-map [2 2])))

  (it "visibility.txt:46 - City reveals surroundings"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "#O#" "###"]))
    (reset! atoms/player-map (build-test-map ["..." "..." "..."]))
    (game-loop/update-player-map)
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 0]))
    (should-not-be-nil (get-in @atoms/player-map [0 1]))
    (should-not-be-nil (get-in @atoms/player-map [1 1]))
    (should-not-be-nil (get-in @atoms/player-map [2 1]))
    (should-not-be-nil (get-in @atoms/player-map [0 2]))
    (should-not-be-nil (get-in @atoms/player-map [1 2]))
    (should-not-be-nil (get-in @atoms/player-map [2 2])))

  (it "visibility.txt:70 - Enemy units hidden in fog"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A####a"]))
    (reset! atoms/player-map (build-test-map ["......"]))
    (game-loop/update-player-map)
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [1 0]))
    (should-be-nil (get-in @atoms/player-map [5 0])))

  (it "visibility.txt:84 - Satellite reveals 5x5 area"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#####" "#####" "##V##" "#####" "#####"]))
    (reset! atoms/player-map (build-test-map ["....." "....." "....." "....." "....."]))
    (game-loop/update-player-map)
    (should-not-be-nil (get-in @atoms/player-map [0 0]))
    (should-not-be-nil (get-in @atoms/player-map [4 4]))
    (should-not-be-nil (get-in @atoms/player-map [0 4]))
    (should-not-be-nil (get-in @atoms/player-map [4 0]))
    (should-not-be-nil (get-in @atoms/player-map [2 2]))))
