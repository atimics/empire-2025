(ns empire.units.patrol-boat-spec
  (:require [speclj.core :refer :all]
            [empire.units.patrol-boat :as patrol-boat]))

(describe "patrol boat unit module"
  (describe "configuration"
    (it "has speed of 4"
      (should= 4 patrol-boat/speed))

    (it "has cost of 15"
      (should= 15 patrol-boat/cost))

    (it "has 1 hit point"
      (should= 1 patrol-boat/hits))

    (it "displays as P"
      (should= "P" patrol-boat/display-char))

    (it "has visibility radius of 1"
      (should= 1 patrol-boat/visibility-radius)))

  (describe "initial-state"
    (it "returns empty map"
      (should= {} (patrol-boat/initial-state))))

  (describe "can-move-to?"
    (it "returns true for sea"
      (should (patrol-boat/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (patrol-boat/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (patrol-boat/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (patrol-boat/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (patrol-boat/needs-attention? {:type :patrol-boat :mode :awake})))

    (it "returns false when sentry"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :sentry})))

    (it "returns false when moving"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :moving})))

    (it "returns false when exploring"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :explore})))))
