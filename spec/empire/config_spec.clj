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


