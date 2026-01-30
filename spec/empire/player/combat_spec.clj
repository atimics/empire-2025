(ns empire.player.combat-spec
  (:require [speclj.core :refer :all]
            [empire.player.combat :as combat]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]
            [empire.units.dispatcher :as dispatcher]
            [empire.containers.helpers :as uc]))

(describe "hostile-city?"
  (before (reset-all-atoms!))
  (it "returns true for free city"
    (reset! atoms/game-map (build-test-map ["+"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
      (should (combat/hostile-city? city-coords))))

  (it "returns true for computer city"
    (reset! atoms/game-map (build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (should (combat/hostile-city? city-coords))))

  (it "returns false for player city"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should-not (combat/hostile-city? city-coords))))

  (it "returns false for non-city cells"
    (reset! atoms/game-map (build-test-map ["#"]))
    (should-not (combat/hostile-city? [0 0])))

  (it "returns false for sea cells"
    (reset! atoms/game-map (build-test-map ["~"]))
    (should-not (combat/hostile-city? [0 0]))))

(describe "attempt-conquest"
  (before (reset-all-atoms!))
  (with-stubs)

  (it "removes army from original cell on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= nil (:contents (get-in @atoms/game-map army-coords))))))

  (it "converts city to player on success"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= :player (:city-status (get-in @atoms/game-map city-coords))))))

  (it "removes army from original cell on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= nil (:contents (get-in @atoms/game-map army-coords))))))

  (it "keeps city status on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= :free (:city-status (get-in @atoms/game-map city-coords))))))

  (it "sets failure message on failed conquest"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= (:conquest-failed config/messages) @atoms/line3-message))))

  (it "returns true regardless of outcome"
    (with-redefs [rand (constantly 0.5)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (should (combat/attempt-conquest army-coords city-coords))))))

(describe "hostile-unit?"
  (it "returns true for computer unit when checking from player perspective"
    (let [unit {:type :army :owner :computer}]
      (should (combat/hostile-unit? unit :player))))

  (it "returns true for player unit when checking from computer perspective"
    (let [unit {:type :army :owner :player}]
      (should (combat/hostile-unit? unit :computer))))

  (it "returns false for player unit when checking from player perspective"
    (let [unit {:type :army :owner :player}]
      (should-not (combat/hostile-unit? unit :player))))

  (it "returns false for computer unit when checking from computer perspective"
    (let [unit {:type :army :owner :computer}]
      (should-not (combat/hostile-unit? unit :computer))))

  (it "returns false for nil unit"
    (should-not (combat/hostile-unit? nil :player))))

(describe "format-combat-log"
  (it "formats simple attacker win"
    (let [log [{:hit :defender :damage 1}]
          attacker-type :destroyer
          defender-type :army]
      (should= "a-1. Army destroyed."
               (combat/format-combat-log log attacker-type defender-type :attacker))))

  (it "formats simple attacker loss"
    (let [log [{:hit :attacker :damage 1}]
          attacker-type :army
          defender-type :destroyer]
      (should= "A-1. Army destroyed."
               (combat/format-combat-log log attacker-type defender-type :defender))))

  (it "formats multi-round combat"
    (let [log [{:hit :defender :damage 3}
               {:hit :attacker :damage 1}
               {:hit :defender :damage 3}
               {:hit :defender :damage 3}]
          attacker-type :submarine
          defender-type :carrier]
      (should= "c-3,S-1,c-3,c-3. Carrier destroyed."
               (combat/format-combat-log log attacker-type defender-type :attacker))))

  (it "uses lowercase for defender hits"
    (let [log [{:hit :defender :damage 2}]
          attacker-type :battleship
          defender-type :destroyer]
      (should= "d-2. Destroyer destroyed."
               (combat/format-combat-log log attacker-type defender-type :attacker))))

  (it "uses uppercase for attacker hits"
    (let [log [{:hit :attacker :damage 1}]
          attacker-type :transport
          defender-type :destroyer]
      (should= "T-1. Transport destroyed."
               (combat/format-combat-log log attacker-type defender-type :defender)))))

(describe "fight-round"
  (it "attacker hits when rand < 0.5"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :destroyer :hits 3 :owner :player}
            defender {:type :transport :hits 1 :owner :computer}
            [new-attacker new-defender log-entry] (combat/fight-round attacker defender)]
        (should= 3 (:hits new-attacker))
        (should= 0 (:hits new-defender))
        (should= {:hit :defender :damage 1} log-entry))))

  (it "defender hits when rand >= 0.5"
    (with-redefs [rand (constantly 0.6)]
      (let [attacker {:type :destroyer :hits 3 :owner :player}
            defender {:type :transport :hits 1 :owner :computer}
            [new-attacker new-defender log-entry] (combat/fight-round attacker defender)]
        (should= 2 (:hits new-attacker))
        (should= 1 (:hits new-defender))
        (should= {:hit :attacker :damage 1} log-entry))))

  (it "submarine deals 3 damage"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :submarine :hits 2 :owner :player}
            defender {:type :carrier :hits 8 :owner :computer}
            [_ new-defender log-entry] (combat/fight-round attacker defender)]
        (should= 5 (:hits new-defender))
        (should= {:hit :defender :damage 3} log-entry))))

  (it "battleship deals 2 damage"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :battleship :hits 10 :owner :player}
            defender {:type :carrier :hits 8 :owner :computer}
            [_ new-defender log-entry] (combat/fight-round attacker defender)]
        (should= 6 (:hits new-defender))
        (should= {:hit :defender :damage 2} log-entry))))

  (it "army deals 1 damage"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :army :hits 1 :owner :player}
            defender {:type :army :hits 1 :owner :computer}
            [_ new-defender log-entry] (combat/fight-round attacker defender)]
        (should= 0 (:hits new-defender))
        (should= {:hit :defender :damage 1} log-entry)))))

(describe "resolve-combat"
  (it "attacker wins when always hitting"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :destroyer :hits 3 :owner :player}
            defender {:type :transport :hits 1 :owner :computer}
            result (combat/resolve-combat attacker defender)]
        (should= :attacker (:winner result))
        (should= 3 (:hits (:survivor result))))))

  (it "returns combat log with hit entries"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :submarine :hits 2 :owner :player}
            defender {:type :carrier :hits 8 :owner :computer}
            result (combat/resolve-combat attacker defender)]
        (should= :attacker (:winner result))
        (should= [{:hit :defender :damage 3}
                  {:hit :defender :damage 3}
                  {:hit :defender :damage 3}] (:log result)))))

  (it "logs defender hits with defender's strength"
    ;; Submarine has 2 hits, carrier deals 1 damage per hit
    ;; Roll 0.6: carrier hits submarine (1 damage), submarine has 1 hit left
    ;; Roll 0.4: submarine hits carrier (3 damage), carrier has 5 hits left
    ;; Roll 0.6: carrier hits submarine (1 damage), submarine has 0 hits -> dies
    (let [rolls (atom [0.6 0.4 0.6])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (let [attacker {:type :submarine :hits 2 :owner :player}
              defender {:type :carrier :hits 8 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :defender (:winner result))
          (should= [{:hit :attacker :damage 1}
                    {:hit :defender :damage 3}
                    {:hit :attacker :damage 1}] (:log result))))))

  (it "defender wins when always hitting"
    (with-redefs [rand (constantly 0.6)]
      (let [attacker {:type :transport :hits 1 :owner :player}
            defender {:type :destroyer :hits 3 :owner :computer}
            result (combat/resolve-combat attacker defender)]
        (should= :defender (:winner result))
        (should= 3 (:hits (:survivor result))))))

  (it "submarine can defeat battleship with lucky rolls"
    (let [rolls (atom [0.4 0.4 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (let [attacker {:type :submarine :hits 2 :owner :player}
              defender {:type :battleship :hits 10 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :attacker (:winner result))
          (should= 2 (:hits (:survivor result)))))))

  (it "army vs army is 50/50"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :army :hits 1 :owner :player}
            defender {:type :army :hits 1 :owner :computer}
            result (combat/resolve-combat attacker defender)]
        (should= :attacker (:winner result)))))

  (it "preserves unit type and owner on survivor"
    (with-redefs [rand (constantly 0.4)]
      (let [attacker {:type :destroyer :hits 3 :owner :player}
            defender {:type :transport :hits 1 :owner :computer}
            result (combat/resolve-combat attacker defender)]
        (should= :destroyer (:type (:survivor result)))
        (should= :player (:owner (:survivor result)))))))

(describe "attempt-attack"
  (before (reset-all-atoms!))

  (it "returns false when target has no unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (should-not (combat/attempt-attack [0 0] [0 1])))

  (it "returns false when target unit is friendly"
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :hits 1)
    (set-test-unit atoms/game-map "A2" :hits 1)
    (should-not (combat/attempt-attack [0 0] [0 1])))

  (it "returns true when attacking enemy unit"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (with-redefs [rand (constantly 0.4)]
      (should (combat/attempt-attack [0 0] [0 1]))))

  (it "attacker wins and occupies cell when victorious"
    (reset! atoms/game-map (build-test-map ["Da"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "a" :hits 1)
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "attacker loses and defender remains"
    (reset! atoms/game-map (build-test-map ["aD"]))
    (set-test-unit atoms/game-map "a" :hits 1)
    (set-test-unit atoms/game-map "D" :hits 3)
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "removes attacker from original cell even when losing"
    (reset! atoms/game-map (build-test-map ["Tb"]))
    (set-test-unit atoms/game-map "T" :hits 1)
    (set-test-unit atoms/game-map "b" :hits 10)
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))))

  (it "survivor has reduced hits after combat"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "d" :hits 3)
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= :destroyer (:type survivor))
          (should= :player (:owner survivor))
          (should= 2 (:hits survivor))))))

  (it "displays combat log when attacker wins"
    (reset! atoms/game-map (build-test-map ["Da"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/line2-message "")
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      (should= "a-1. Army destroyed." @atoms/line2-message)))

  (it "displays combat log when attacker loses"
    (reset! atoms/game-map (build-test-map ["Ad"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/line2-message "")
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= "A-1. Army destroyed." @atoms/line2-message)))

  (it "displays combat log with multiple exchanges"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "d-1,D-1,d-1,d-1. Destroyer destroyed." @atoms/line2-message))))

  (it "displays combat log for submarine vs carrier"
    (reset! atoms/game-map (build-test-map ["Sc"]))
    (set-test-unit atoms/game-map "S" :hits 2)
    (set-test-unit atoms/game-map "c" :hits 8)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.6 (c hits S:1), 0.6 (c hits S:0)
    (let [rolls (atom [0.6 0.6])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "S-1,S-1. Submarine destroyed." @atoms/line2-message))))

  (it "displays combat log for submarine defeating carrier"
    (reset! atoms/game-map (build-test-map ["Sc"]))
    (set-test-unit atoms/game-map "S" :hits 2)
    (set-test-unit atoms/game-map "c" :hits 8)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.4 (S hits c:5), 0.6 (c hits S:1), 0.4 (S hits c:2), 0.4 (S hits c:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "c-3,S-1,c-3,c-3. Carrier destroyed." @atoms/line2-message)))))

(describe "conquer-city-contents"
  (before (reset-all-atoms!))

  (it "flips a fighter at the city to new owner"
    (reset! atoms/game-map (build-test-map ["X"]))
    ;; Place computer fighter on the city
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :fighter :owner :computer :mode :moving :hits 1 :fuel 20 :target [5 5]})
    (combat/conquer-city-contents [0 0] :player)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :player (:owner unit))
      (should= :fighter (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))))

  (it "flips a destroyer at the city to new owner"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :destroyer :owner :computer :mode :moving :hits 3 :target [5 5]})
    (combat/conquer-city-contents [0 0] :player)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :player (:owner unit))
      (should= :destroyer (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))))

  (it "kills army standing on the city"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :army :owner :computer :mode :awake :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (get-in @atoms/game-map [0 0 :contents])))

  (it "kills armies inside a transport and flips the transport"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :transport :owner :computer :mode :sentry :hits 1
            :army-count 4 :awake-armies 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :player (:owner unit))
      (should= :transport (:type unit))
      (should= :awake (:mode unit))
      (should= 0 (:army-count unit))
      (should= 0 (:awake-armies unit))))

  (it "kills fighters inside a carrier and flips the carrier"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :carrier :owner :computer :mode :sentry :hits 8
            :fighter-count 5 :awake-fighters 3})
    (combat/conquer-city-contents [0 0] :player)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :player (:owner unit))
      (should= :carrier (:type unit))
      (should= :awake (:mode unit))
      (should= 0 (:fighter-count unit))
      (should= 0 (:awake-fighters unit))))

  (it "leaves satellites unchanged"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :satellite :owner :computer :mode :moving :hits 1 :turns-remaining 30 :target [5 5]})
    (combat/conquer-city-contents [0 0] :player)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :computer (:owner unit))
      (should= :satellite (:type unit))
      (should= :moving (:mode unit))))

  (it "clears city production on conquest"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/production assoc [0 0] {:item :army :remaining-rounds 3})
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (get @atoms/production [0 0])))

  (it "clears marching-orders and flight-path on conquest"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :marching-orders] [10 10])
    (swap! atoms/game-map assoc-in [0 0 :flight-path] [20 20])
    (combat/conquer-city-contents [0 0] :player)
    (let [cell (get-in @atoms/game-map [0 0])]
      (should-be-nil (:marching-orders cell))
      (should-be-nil (:flight-path cell))))

  (it "preserves airport fighter count for new owner"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :fighter-count] 3)
    (swap! atoms/game-map assoc-in [0 0 :awake-fighters] 1)
    (combat/conquer-city-contents [0 0] :player)
    (let [cell (get-in @atoms/game-map [0 0])]
      (should= 3 (:fighter-count cell))
      (should= 1 (:awake-fighters cell))))

  (it "preserves shipyard ships for new owner"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :shipyard]
           [{:type :destroyer :hits 2} {:type :submarine :hits 1}])
    (combat/conquer-city-contents [0 0] :player)
    (let [cell (get-in @atoms/game-map [0 0])]
      (should= 2 (count (:shipyard cell)))
      (should= :destroyer (:type (first (:shipyard cell))))))

  (it "player conquest calls conquer-city-contents"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map (build-test-map ["AX"]))
      ;; Place a computer destroyer on the city
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :destroyer :owner :computer :mode :sentry :hits 3})
      (swap! atoms/production assoc [0 1] {:item :fighter :remaining-rounds 5})
      (combat/attempt-conquest [0 0] [0 1])
      ;; City should be player-owned
      (should= :player (get-in @atoms/game-map [0 1 :city-status]))
      ;; Destroyer should be flipped to player
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        (should= :player (:owner unit))
        (should= :destroyer (:type unit)))
      ;; Production should be cleared
      (should-be-nil (get @atoms/production [0 1]))))

  (it "computer conquest applies same logic"
    (with-redefs [rand (constantly 0.1)]
      (reset! atoms/game-map (build-test-map ["aO"]))
      (reset! atoms/computer-map @atoms/game-map)
      ;; Place a player destroyer on the city
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :destroyer :owner :player :mode :sentry :hits 3})
      (swap! atoms/production assoc [0 1] {:item :army :remaining-rounds 2})
      (let [core (requiring-resolve 'empire.computer.core/attempt-conquest-computer)]
        (core [0 0] [0 1])
        ;; City should be computer-owned
        (should= :computer (get-in @atoms/game-map [0 1 :city-status]))
        ;; Destroyer should be flipped to computer
        (let [unit (get-in @atoms/game-map [0 1 :contents])]
          (should= :computer (:owner unit))
          (should= :destroyer (:type unit)))
        ;; Production should be cleared
        (should-be-nil (get @atoms/production [0 1]))))))

(describe "cargo drowning after combat"
  (before (reset-all-atoms!))

  (it "drowns excess fighters when carrier wins with reduced capacity"
    ;; Carrier at 8 hits with 6 fighters attacks army. Carrier wins but takes hits.
    ;; Carrier ends at 4/8 hits -> capacity 4, so 2 fighters drown.
    (reset! atoms/game-map (build-test-map ["Ca"]))
    (set-test-unit atoms/game-map "C" :hits 8 :fighter-count 6 :awake-fighters 0)
    (set-test-unit atoms/game-map "a" :hits 1)
    ;; Rolls: 0.6(a hits C:7), 0.6(a hits C:6), 0.6(a hits C:5), 0.6(a hits C:4), 0.4(C hits a:0)
    (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= :carrier (:type survivor))
          (should= 4 (:hits survivor))
          (should= 4 (:fighter-count survivor))))))

  (it "does not drown when cargo within capacity"
    ;; Carrier at 8 hits with 3 fighters attacks army. Carrier wins with 4 hits.
    ;; Capacity 4 >= 3 fighters, no drowning.
    (reset! atoms/game-map (build-test-map ["Ca"]))
    (set-test-unit atoms/game-map "C" :hits 8 :fighter-count 3 :awake-fighters 0)
    (set-test-unit atoms/game-map "a" :hits 1)
    (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= 3 (:fighter-count survivor))))))

  (it "caps awake-fighters at new fighter-count after drowning"
    (reset! atoms/game-map (build-test-map ["Ca"]))
    (set-test-unit atoms/game-map "C" :hits 8 :fighter-count 6 :awake-fighters 5)
    (set-test-unit atoms/game-map "a" :hits 1)
    ;; Carrier ends at 4 hits -> capacity 4
    (let [rolls (atom [0.6 0.6 0.6 0.6 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= 4 (:fighter-count survivor))
          (should (<= (:awake-fighters survivor) 4))))))

  (it "drowns cargo when defending carrier takes damage"
    ;; Computer army attacks player carrier. Carrier wins but takes damage.
    (reset! atoms/game-map (build-test-map ["aC"]))
    (set-test-unit atoms/game-map "C" :hits 8 :fighter-count 6 :awake-fighters 0)
    (set-test-unit atoms/game-map "a" :hits 1)
    ;; Rolls: 0.6(C hits a:0) -> army dies immediately, carrier unhurt
    ;; Need carrier to take damage. Army attacks carrier.
    ;; Rolls: 0.6(C hits a, damage 1) -> a dies. But carrier takes no damage.
    ;; Let me use a scenario where the attacker damages the defender before dying.
    ;; Roll 0.4: attacker hits defender (army deals 1 damage to carrier -> 7 hits)
    ;; Roll 0.6: defender (carrier) hits attacker (1 damage -> army dies)
    ;; Carrier at 7 hits, capacity 7 >= 6, no drowning.
    ;; Need more damage. Use a stronger attacker.
    (reset! atoms/game-map (build-test-map ["sC"]))
    (set-test-unit atoms/game-map "C" :hits 8 :fighter-count 7 :awake-fighters 0)
    (set-test-unit atoms/game-map "s" :hits 2)
    ;; Rolls: 0.4(sub hits C:5), 0.4(sub hits C:2), 0.6(C hits sub:1), 0.6(C hits sub:0)
    (let [rolls (atom [0.4 0.4 0.6 0.6])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        ;; Carrier won (defender), now at 2/8 hits -> capacity 2
        ;; 7 fighters should be reduced to 2
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= :carrier (:type survivor))
          (should= 2 (:hits survivor))
          (should= 2 (:fighter-count survivor)))))))
