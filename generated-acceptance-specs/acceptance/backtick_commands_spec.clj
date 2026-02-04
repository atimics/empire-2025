(ns acceptance.backtick-commands-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.ui.input :as input]
            [quil.core :as q]))

(describe "backtick-commands.txt"

  (it "backtick-commands.txt:6 - Spawn player army on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :A))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "A")))))

  (it "backtick-commands.txt:17 - Spawn player fighter on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :F))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "F")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "F")))))

  (it "backtick-commands.txt:28 - Spawn player transport on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :T))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "T")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "T")))))

  (it "backtick-commands.txt:39 - Spawn player destroyer on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :D))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "D")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "D")))))

  (it "backtick-commands.txt:50 - Spawn player carrier on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :C))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "C")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "C")))))

  (it "backtick-commands.txt:61 - Spawn player submarine on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :S))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "S")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "S")))))

  (it "backtick-commands.txt:72 - Spawn player patrol-boat on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :P))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "P")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "P")))))

  (it "backtick-commands.txt:83 - Spawn player battleship on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :B))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "B")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "B")))))

  (it "backtick-commands.txt:94 - Spawn player satellite on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :Z))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "V")]
      (should= [0 0] pos))
    (should= :player (:owner (:unit (get-test-unit atoms/game-map "V")))))

  (it "backtick-commands.txt:105 - Spawn computer army on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :a))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "a")]
      (should= [0 0] pos))
    (should= :computer (:owner (:unit (get-test-unit atoms/game-map "a")))))

  (it "backtick-commands.txt:116 - Spawn computer fighter on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :f))
    (let [{:keys [pos]} (get-test-unit atoms/game-map "f")]
      (should= [0 0] pos))
    (should= :computer (:owner (:unit (get-test-unit atoms/game-map "f")))))

  (it "backtick-commands.txt:127 - Own free city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["+#"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :o))
    (should= :player (:city-status (get-in @atoms/game-map [0 0]))))

  (it "backtick-commands.txt:137 - Own computer city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/map-screen-dimensions [22 16])
    (with-redefs [q/mouse-x (constantly 0)
                  q/mouse-y (constantly 0)]
      (input/key-down (keyword "`"))
      (input/key-down :o))
    (should= :player (:city-status (get-in @atoms/game-map [0 0])))))
