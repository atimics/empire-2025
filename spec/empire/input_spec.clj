(ns empire.input-spec
  (:require [speclj.core :refer :all]
            [empire.input :as input]
            [empire.atoms :as atoms]))

(describe "set-city-lookaround"
  (around [it]
    (reset! atoms/game-map [[{:type :sea} {:type :city :city-status :player}]
                            [{:type :city :city-status :computer} {:type :land}]])
    (it))

  (it "sets marching orders to :lookaround on player city"
    (input/set-city-lookaround [0 1])
    (should= :lookaround (get-in @atoms/game-map [0 1 :marching-orders])))

  (it "returns true when setting lookaround on player city"
    (should (input/set-city-lookaround [0 1])))

  (it "does not set marching orders on computer city"
    (input/set-city-lookaround [1 0])
    (should-be-nil (get-in @atoms/game-map [1 0 :marching-orders])))

  (it "returns nil when cell is not a player city"
    (should-be-nil (input/set-city-lookaround [1 0])))

  (it "does not set marching orders on non-city cell"
    (input/set-city-lookaround [0 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :marching-orders])))

  (it "returns nil for non-city cell"
    (should-be-nil (input/set-city-lookaround [0 0]))))
