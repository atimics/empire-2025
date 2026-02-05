(ns acceptance.visibility-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! visibility-mask]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.ui.input :as input]))

(describe "visibility.txt"

  (it "visibility.txt:6 - Army reveals 3x3 around itself"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#####" "##A##" "#####"]))
    (reset! atoms/player-map (build-test-map ["....." "....." "....."]))
    (game-loop/update-player-map)
    (should= (visibility-mask (build-test-map [".###." ".###." ".###."]))
             (visibility-mask @atoms/player-map)))

  (it "visibility.txt:25 - Unexplored cells are not visible"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "###" "###"]))
    (reset! atoms/player-map (build-test-map ["..." "..." "..."]))
    (should= (visibility-mask (build-test-map ["..." "..." "..."]))
             (visibility-mask @atoms/player-map)))

  (it "visibility.txt:42 - City reveals surroundings"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "#O#" "###"]))
    (reset! atoms/player-map (build-test-map ["..." "..." "..."]))
    (game-loop/update-player-map)
    (should= (visibility-mask (build-test-map ["###" "###" "###"]))
             (visibility-mask @atoms/player-map)))

  (it "visibility.txt:61 - Enemy units hidden in fog"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["A####a"]))
    (reset! atoms/player-map (build-test-map ["......"]))
    (game-loop/update-player-map)
    (should= (visibility-mask (build-test-map ["##...."]))
             (visibility-mask @atoms/player-map)))

  (it "visibility.txt:74 - Satellite reveals 5x5 area"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#####" "#####" "##V##" "#####" "#####"]))
    (reset! atoms/player-map (build-test-map ["....." "....." "....." "....." "....."]))
    (game-loop/update-player-map)
    (should= (visibility-mask (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
             (visibility-mask @atoms/player-map))))
