(ns empire.init-spec
  (:require [speclj.core :refer :all]
            [empire.init :refer :all]))

(describe "smooth-map"
  (it "handles a 1x1 map by returning the same value"
    (let [input [[42]]
          result (smooth-map input)]
      (should= input result)))

  (it "smooths a 2x2 map correctly"
    (let [input [[2 3] [4 5]]
          result (smooth-map input)
          expected [[2 3] [3 4]]]
      (should= expected result))))