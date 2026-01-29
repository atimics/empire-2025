(ns empire.computer.transport-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (describe "loading behavior"
    (it "moves toward nearest army"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land :contents {:type :army :owner :computer}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (transport/process-transport [0 0])]
        ;; Transport should move toward army (to [0 1])
        (should= [0 1] result)))

    (it "patrols near coast when no armies"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (transport/process-transport [0 0])]
        ;; Transport should move somewhere
        (should= [0 1] result))))

  (describe "unloading behavior"
    (it "unloads armies onto adjacent land"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Should have unloaded an army onto land
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
      ;; Transport should have fewer armies
      (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1])))))

    (it "changes to loading mode after full unload"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be in loading mode now
      (should= :loading (:transport-mission (:contents (get-in @atoms/game-map [0 1])))))

    (it "moves toward enemy city when full"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 6}}
                                {:type :sea}
                                {:type :sea}
                                {:type :land}
                                {:type :city :city-status :player}]])
      (reset! atoms/computer-map @atoms/game-map)
      (let [result (transport/process-transport [0 0])]
        ;; Transport should move toward enemy city
        (should= [0 1] result)
        ;; Mission should change to unloading
        (should= :unloading (:transport-mission (:contents (get-in @atoms/game-map [0 1])))))))

  (describe "mission transitions"
    (it "sets idle transport to loading"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Should be in loading mode
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport)))))

  (describe "ignores non-computer transports"
    (it "returns nil for player transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :player
                                                       :army-count 0}}]])
      (should-be-nil (transport/process-transport [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map [[{:type :sea}]])
      (should-be-nil (transport/process-transport [0 0])))))
