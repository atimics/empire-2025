(ns empire.debug-spec
  (:require [speclj.core :refer :all]
            [empire.debug :as debug]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [clojure.string :as str]))

(describe "format-dump includes sea lane network section"
  (before (reset-all-atoms!))

  (it "shows node and segment counts with entries"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~~~"]))
    (reset! atoms/sea-lane-network
            {:nodes {1 {:id 1 :pos [0 2] :segment-ids #{1}}
                     2 {:id 2 :pos [0 5] :segment-ids #{1}}}
             :segments {1 {:id 1 :node-a-id 1 :node-b-id 2
                            :direction [0 1]
                            :cells [[0 2] [0 3] [0 4] [0 5]]
                            :length 3}}
             :pos->node {[0 2] 1 [0 5] 2}
             :pos->seg {[0 3] 1 [0 4] 1}
             :next-node-id 3 :next-segment-id 2})
    (let [output (debug/format-dump [0 0] [1 2])]
      (should-contain "Sea Lane Network" output)
      (should-contain "nodes: 2" output)
      (should-contain "segments: 1" output)
      (should-contain "node 1:" output)
      (should-contain "node 2:" output)
      (should-contain "[0,2]" output)
      (should-contain "[0,5]" output)
      (should-contain "seg 1:" output)
      (should-contain "length:3" output)))

  (it "shows empty network when no sea lanes exist"
    (reset! atoms/game-map (build-test-map ["~~~"]))
    (let [output (debug/format-dump [0 0] [0 2])]
      (should-contain "Sea Lane Network" output)
      (should-contain "nodes: 0" output)
      (should-contain "segments: 0" output))))

(describe "format-cell handles nil contents fields"
  (before (reset-all-atoms!))

  (it "does not crash when contents has nil type or owner"
    (let [cell {:type :sea :contents {:type nil :owner nil}}
          result (debug/format-cell [0 0] cell)]
      (should-contain "contents:" result)))

  (it "formats cell with valid contents"
    (let [cell {:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
          result (debug/format-cell [0 0] cell)]
      (should-contain "type:destroyer" result)
      (should-contain "owner:computer" result))))
