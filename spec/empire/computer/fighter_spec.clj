(ns empire.computer.fighter-spec
  "Tests for VMS Empire style computer fighter movement."
  (:require [speclj.core :refer :all]
            [empire.computer.fighter :as fighter]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-fighter"
  (before (reset-all-atoms!))

  (describe "attack behavior"
    (it "attacks adjacent player unit"
      (reset! atoms/game-map [[{:type :land :contents {:type :fighter :owner :computer
                                                        :fuel 20 :hits 1}}
                                {:type :land :contents {:type :army :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))
            _result (fighter/process-fighter [0 0] unit)]
        ;; Combat should have occurred - one unit should be gone
        (let [cell0 (get-in @atoms/game-map [0 0])
              cell1 (get-in @atoms/game-map [0 1])]
          (should (or (nil? (:contents cell0))
                      (nil? (:contents cell1))
                      (= :computer (:owner (:contents cell1)))))))))

  (describe "fuel management"
    (it "returns to city when low on fuel"
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :land}
                                {:type :land :contents {:type :fighter :owner :computer
                                                         :fuel 3 :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 2]))
            result (fighter/process-fighter [0 2] unit)]
        ;; Fighter should move toward city
        (should= [0 1] result)))

    (it "lands at adjacent city"
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :land :contents {:type :fighter :owner :computer
                                                         :fuel 2 :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 1]))
            result (fighter/process-fighter [0 1] unit)]
        ;; Fighter should land at city
        (should-be-nil result)
        ;; City should have fighter
        (should= 1 (:fighter-count (get-in @atoms/game-map [0 0]))))))

  (describe "patrol behavior"
    (it "patrols toward player units when fuel allows"
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :land :contents {:type :fighter :owner :computer
                                                         :fuel 20 :hits 1}}
                                {:type :land}
                                {:type :land :contents {:type :army :owner :player :hits 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [unit (:contents (get-in @atoms/game-map [0 1]))
            result (fighter/process-fighter [0 1] unit)]
        ;; Fighter should move toward player army
        (should= [0 2] result)))

    (it "explores toward unexplored territory"
      (reset! atoms/computer-map [[{:type :city :city-status :computer}
                                    {:type :land :contents {:type :fighter :owner :computer
                                                             :fuel 20 :hits 1}}
                                    nil]])
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :land :contents {:type :fighter :owner :computer
                                                         :fuel 20 :hits 1}}
                                {:type :land}]])
      (let [unit (:contents (get-in @atoms/game-map [0 1]))
            result (fighter/process-fighter [0 1] unit)]
        ;; Fighter should move toward unexplored
        (should= [0 2] result))))

  (describe "ignores non-computer fighters"
    (it "returns nil for player fighter"
      (reset! atoms/game-map [[{:type :land :contents {:type :fighter :owner :player :fuel 20}}]])
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))

    (it "returns nil for non-fighter"
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}]])
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should-be-nil (fighter/process-fighter [0 0] unit))))))
