(ns empire.computer.ship-spec
  "Tests for VMS Empire style computer ship movement."
  (:require [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-ship"
  (before (reset-all-atoms!))

  (describe "attack behavior"
    (it "attacks adjacent player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [_result (ship/process-ship [0 0] :destroyer)]
        ;; Combat should have occurred
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (describe "escort behavior"
    (it "destroyer moves toward transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :army-count 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (ship/process-ship [0 0] :destroyer)]
        ;; Destroyer should move toward transport
        (should= [0 1] result))))

  (describe "exploration behavior"
    (it "explores toward unexplored sea"
      (reset! atoms/computer-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                    {:type :sea}
                                    nil]])
      (reset! atoms/game-map [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                                {:type :sea}
                                {:type :sea}]])
      (let [result (ship/process-ship [0 0] :patrol-boat)]
        ;; Ship should move toward unexplored
        (should= [0 1] result)))

    (it "moves to available sea cell when fully explored"
      (reset! atoms/game-map [[{:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (ship/process-ship [0 0] :submarine)]
        ;; Ship should move to available sea
        (should= [0 1] result))))

  (describe "hunting behavior"
    (it "moves toward visible player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                                {:type :sea}
                                {:type :sea}
                                {:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (ship/process-ship [0 0] :battleship)]
        ;; Battleship should move toward player ship
        (should= [0 1] result))))

  (describe "ignores non-computer ships"
    (it "returns nil for player ship"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :player :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :destroyer)))

    (it "returns nil for wrong ship type"
      (reset! atoms/game-map [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}]])
      (should-be-nil (ship/process-ship [0 0] :patrol-boat)))))
