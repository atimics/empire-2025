(ns empire.map-spec
  (:require [speclj.core :refer :all]
            [empire.map :as map]
            [empire.atoms :as atoms]))

(describe "cells-needing-attention"
  (it "returns empty list when no player cells"
    (reset! atoms/player-map [[{:type :sea :owner nil}
                               {:type :land :owner nil}]
                              [{:type :city :owner :computer}
                               {:type :land :owner nil}]])
    (reset! atoms/production {})
    (should= [] (map/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (reset! atoms/player-map [[{:type :land :owner :player :contents {:type :army :mode :awake}}
                               {:type :land :owner nil}]
                              [{:type :land :owner nil}
                               {:type :land :owner nil}]])
    (reset! atoms/production {})
    (should= [[0 0]] (map/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (reset! atoms/player-map [[{:type :land :owner nil}
                               {:type :city :owner :player}]
                              [{:type :land :owner nil}
                               {:type :land :owner nil}]])
    (reset! atoms/production {})
    (should= [[0 1]] (map/cells-needing-attention)))

  (it "excludes cities with production"
    (reset! atoms/player-map [[{:type :city :owner :player}
                               {:type :land :owner nil}]])
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (map/cells-needing-attention)))

  (it "returns multiple coordinates"
    (reset! atoms/player-map [[{:type :land :owner :player :contents {:type :army :mode :awake}}
                               {:type :city :owner :player}]
                              [{:type :land :owner nil}
                               {:type :land :owner nil}]])
    (reset! atoms/production {})
    (should= [[0 0] [0 1]] (map/cells-needing-attention))))