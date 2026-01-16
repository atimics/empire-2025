(ns empire.units.submarine-spec
  (:require [speclj.core :refer :all]
            [empire.units.submarine :as submarine]))

(describe "submarine unit module"
  (describe "configuration"
    (it "has speed of 2"
      (should= 2 submarine/speed))

    (it "has cost of 20"
      (should= 20 submarine/cost))

    (it "has 2 hit points"
      (should= 2 submarine/hits))

    (it "displays as S"
      (should= "S" submarine/display-char))

    (it "has visibility radius of 1"
      (should= 1 submarine/visibility-radius)))

  (describe "initial-state"
    (it "returns empty map"
      (should= {} (submarine/initial-state))))

  (describe "can-move-to?"
    (it "returns true for sea"
      (should (submarine/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (submarine/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (submarine/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (submarine/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (submarine/needs-attention? {:type :submarine :mode :awake})))

    (it "returns false when sentry"
      (should-not (submarine/needs-attention? {:type :submarine :mode :sentry})))

    (it "returns false when moving"
      (should-not (submarine/needs-attention? {:type :submarine :mode :moving})))

    (it "returns false when exploring"
      (should-not (submarine/needs-attention? {:type :submarine :mode :explore})))))
