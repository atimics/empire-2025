(ns empire.rendering-util-spec
  (:require [speclj.core :refer :all]
            [empire.rendering-util :as ru]))

(describe "format-unit-status"
  (it "formats basic army status"
    (let [unit {:type :army :hits 1 :mode :awake}]
      (should= "army [1/1] awake" (ru/format-unit-status unit))))

  (it "formats fighter with fuel"
    (let [unit {:type :fighter :hits 1 :mode :sentry :fuel 15}]
      (should= "fighter [1/1] fuel:15 sentry" (ru/format-unit-status unit))))

  (it "formats transport with cargo"
    (let [unit {:type :transport :hits 1 :mode :awake :army-count 4}]
      (should= "transport [1/1] cargo:4 awake" (ru/format-unit-status unit))))

  (it "formats carrier with cargo"
    (let [unit {:type :carrier :hits 8 :mode :moving :fighter-count 3}]
      (should= "carrier [8/8] cargo:3 moving" (ru/format-unit-status unit))))

  (it "formats unit with marching orders"
    (let [unit {:type :army :hits 1 :mode :moving :marching-orders [[1 2] [3 4]]}]
      (should= "army [1/1] march moving" (ru/format-unit-status unit))))

  (it "formats unit with flight path"
    (let [unit {:type :fighter :hits 1 :mode :moving :fuel 10 :flight-path [[1 2]]}]
      (should= "fighter [1/1] fuel:10 flight moving" (ru/format-unit-status unit)))))

(describe "format-city-status"
  (it "formats player city with production"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0}
          production {:item :army :remaining-rounds 5}]
      (should= "city:player producing:army" (ru/format-city-status cell production))))

  (it "formats player city with no production"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0}]
      (should= "city:player" (ru/format-city-status cell nil))))

  (it "formats city with fighters"
    (let [cell {:type :city :city-status :player :fighter-count 3 :sleeping-fighters 1}]
      (should= "city:player fighters:3 sleeping:1" (ru/format-city-status cell nil))))

  (it "formats city with marching orders"
    (let [cell {:type :city :city-status :player :fighter-count 0 :sleeping-fighters 0 :marching-orders [[1 2]]}]
      (should= "city:player march" (ru/format-city-status cell nil))))

  (it "formats computer city"
    (let [cell {:type :city :city-status :computer :fighter-count 2 :sleeping-fighters 0}]
      (should= "city:computer fighters:2" (ru/format-city-status cell nil)))))

(describe "format-hover-status"
  (it "returns unit status for cell with contents"
    (let [cell {:contents {:type :army :hits 1 :mode :awake}}]
      (should= "army [1/1] awake" (ru/format-hover-status cell nil))))

  (it "returns city status for city cell"
    (let [cell {:type :city :city-status :free :fighter-count 0 :sleeping-fighters 0}]
      (should= "city:free" (ru/format-hover-status cell nil))))

  (it "returns nil for empty non-city cell"
    (let [cell {:type :land}]
      (should-not (ru/format-hover-status cell nil)))))

(describe "group-cells-by-color"
  (it "groups cells by their base color"
    (let [the-map [[{:type :land} {:type :sea}]
                   [{:type :land} {:type :sea}]]
          result (ru/group-cells-by-color the-map nil {} false false)]
      (should= 2 (count result))
      (should= 2 (count (get result [139 69 19])))
      (should= 2 (count (get result [0 191 255])))))

  (it "skips unexplored cells"
    (let [the-map [[{:type :land} {:type :unexplored}]]
          result (ru/group-cells-by-color the-map nil {} false false)]
      (should= 1 (count result))
      (should= 1 (count (get result [139 69 19])))))

  (it "flashes attention cell black when blink-attention is true"
    (let [the-map [[{:type :land}]]
          result (ru/group-cells-by-color the-map [[0 0]] {} true false)]
      (should= 1 (count (get result [0 0 0])))))

  (it "shows normal color for attention cell when blink-attention is false"
    (let [the-map [[{:type :land}]]
          result (ru/group-cells-by-color the-map [[0 0]] {} false false)]
      (should= 1 (count (get result [139 69 19])))))

  (it "flashes completed city white when blink-completed is true"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map nil production false true)]
      (should= 1 (count (get result [255 255 255])))))

  (it "shows normal color for completed city when blink-completed is false"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map nil production false false)]
      (should= 1 (count (get result [0 255 0])))))

  (it "attention blink takes priority over completed blink"
    (let [the-map [[{:type :city :city-status :player}]]
          production {[0 0] {:item :army :remaining-rounds 0}}
          result (ru/group-cells-by-color the-map [[0 0]] production true true)]
      (should= 1 (count (get result [0 0 0]))))))
