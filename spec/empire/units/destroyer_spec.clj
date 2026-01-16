(ns empire.units.destroyer-spec
  (:require [speclj.core :refer :all]
            [empire.units.destroyer :as destroyer]))

(describe "destroyer unit module"
  (describe "configuration"
    (it "has speed of 2"
      (should= 2 destroyer/speed))

    (it "has cost of 20"
      (should= 20 destroyer/cost))

    (it "has 3 hit points"
      (should= 3 destroyer/hits))

    (it "displays as D"
      (should= "D" destroyer/display-char))

    (it "has visibility radius of 1"
      (should= 1 destroyer/visibility-radius)))

  (describe "initial-state"
    (it "returns empty map"
      (should= {} (destroyer/initial-state))))

  (describe "can-move-to?"
    (it "returns true for sea"
      (should (destroyer/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (destroyer/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (destroyer/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (destroyer/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (destroyer/needs-attention? {:type :destroyer :mode :awake})))

    (it "returns false when sentry"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :sentry})))

    (it "returns false when moving"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :moving})))

    (it "returns false when exploring"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :explore})))))
