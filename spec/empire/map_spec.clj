(ns empire.map-spec
  (:require [speclj.core :refer :all]
            [empire.map :as map]
            [empire.atoms :as atoms]))

(describe "cells-needing-attention"
  (it "returns empty list when no player cells"
    (reset! atoms/player-map [[{:type :sea }
                               {:type :land }]
                              [{:type :city :city-status :computer :contents nil}
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [] (map/cells-needing-attention)))

  (it "returns coordinates of awake units"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :land }]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0]] (map/cells-needing-attention)))

  (it "returns coordinates of cities with no production"
    (reset! atoms/player-map [[{:type :land }
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 1]] (map/cells-needing-attention)))

  (it "excludes cities with production"
    (reset! atoms/player-map [[{:type :city :city-status :player :contents nil}
                               {:type :land }]])
    (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
    (should= [] (map/cells-needing-attention)))

  (it "returns multiple coordinates"
    (reset! atoms/player-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}
                               {:type :city :city-status :player :contents nil}]
                              [{:type :land }
                               {:type :land }]])
    (reset! atoms/production {})
    (should= [[0 0] [0 1]] (map/cells-needing-attention))))