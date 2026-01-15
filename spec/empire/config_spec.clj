(ns empire.config-spec
  (:require [speclj.core :refer :all]
            [empire.config :as config]))

(describe "color-of"
  (it "returns green for player city"
    (should= [0 255 0] (config/color-of {:type :city :city-status :player})))

  (it "returns red for computer city"
    (should= [255 0 0] (config/color-of {:type :city :city-status :computer})))

  (it "returns white for free city"
    (should= [255 255 255] (config/color-of {:type :city :city-status :free})))

  (it "returns brown for land"
    (should= [139 69 19] (config/color-of {:type :land})))

  (it "returns blue for sea"
    (should= [0 191 255] (config/color-of {:type :sea}))))

(describe "mode->color"
  (it "returns white for awake"
    (should= [255 255 255] (config/mode->color :awake)))

  (it "returns pink for sentry"
    (should= [255 128 128] (config/mode->color :sentry)))

  (it "returns light green for explore"
    (should= [144 238 144] (config/mode->color :explore)))

  (it "returns black for other modes"
    (should= [0 0 0] (config/mode->color :moving))
    (should= [0 0 0] (config/mode->color :unknown))))

(describe "format-unit-status"
  (it "formats basic army status"
    (let [unit {:type :army :hits 1 :mode :awake}]
      (should= "army [1/1] awake" (config/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 15}]
      (should= "fighter [1/1] fuel:15 sentry" (config/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4}]
      (should= "transport [1/1] cargo:4 awake" (config/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 3}]
      (should= "carrier [8/8] cargo:3 moving" (config/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [[1 2] [3 4]]}]
      (should= "army [1/1] march moving" (config/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 10 :flight-path [[1 2]]}]
      (should= "fighter [1/1] fuel:10 flight moving" (config/format-unit-status unit)))))

(describe "format-city-status"
  (it "formats player city with production"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0}
          production {:item :army :remaining-rounds 5}]
      (should= "city:player producing:army" (config/format-city-status cell production))))

  (it "formats player city with no production"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0}]
      (should= "city:player" (config/format-city-status cell nil))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3 :sleeping-fighters 1}]
      (should= "city:player fighters:3 sleeping:1" (config/format-city-status cell nil))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0 :marching-orders [[1 2]]}]
      (should= "city:player march" (config/format-city-status cell nil))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer :fighter-count 2 :sleeping-fighters 0}]
      (should= "city:computer fighters:2" (config/format-city-status cell nil)))))

(describe "format-hover-status"
  (it "returns unit status for cell with contents"
    (let [cell {:contents {:type :army :hits 1 :mode :awake}}]
      (should= "army [1/1] awake" (config/format-hover-status cell nil))))

  (it "returns city status for city cell"
    (let [cell {:type :city :city-status :free :fighter-count 0 :sleeping-fighters 0}]
      (should= "city:free" (config/format-hover-status cell nil))))

  (it "returns nil for empty non-city cell"
    (let [cell {:type :land}]
      (should-not (config/format-hover-status cell nil)))))
