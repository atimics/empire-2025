(ns acceptance.computer-production-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit get-test-city reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.game-loop :as game-loop]
            [empire.ui.input :as input]
            [empire.computer.production :as computer-production]))

(describe "computer-production.txt"

  (it "computer-production.txt:6 - No-country city produces army when continent has free city"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X+" "##"]))
    (reset! atoms/computer-map (build-test-map ["X+" "##"]))
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :army (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:20 - No-country city produces nothing when no land objective"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#" "##"]))
    (reset! atoms/computer-map (build-test-map ["X#" "##"]))
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "X")))]
      (should (or (nil? prod) (= :none prod)))))

  (it "computer-production.txt:34 - Country produces army when zero armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#" "##"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :army (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:46 - Country produces transport at coastal city after 6 armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X~" "t#"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (set-test-unit atoms/game-map "t" :country-id 1)
    (set-test-unit atoms/game-map "t" :army-count 6)
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :transport (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:60 - Country inland city skips transport, produces army"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#~" "##t"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (set-test-unit atoms/game-map "t" :country-id 1)
    (set-test-unit atoms/game-map "t" :army-count 6)
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :army (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:74 - Country produces patrol-boat when army and transport caps met"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X~~~" "##t#" "##t#"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (set-test-unit atoms/game-map "t1" :country-id 1)
    (set-test-unit atoms/game-map "t1" :army-count 5)
    (set-test-unit atoms/game-map "t2" :country-id 1)
    (set-test-unit atoms/game-map "t2" :army-count 5)
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :patrol-boat (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:91 - Country produces destroyer when unadopted transport exists"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X~~~" "##t#" "##t#" "p###"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (set-test-unit atoms/game-map "t1" :country-id 1)
    (set-test-unit atoms/game-map "t1" :army-count 5)
    (set-test-unit atoms/game-map "t2" :country-id 1)
    (set-test-unit atoms/game-map "t2" :army-count 5)
    (set-test-unit atoms/game-map "p" :patrol-country-id 1)
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :destroyer (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:110 - Country produces fighter at inland city when army cap met"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#~" "##t"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (set-test-unit atoms/game-map "t" :country-id 1)
    (set-test-unit atoms/game-map "t" :army-count 10)
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :fighter (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:124 - Existing production is not overwritten"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#" "##"]))
    (swap! atoms/game-map update-in [0 0] merge {:country-id 1})
    (let [x-pos (:pos (get-test-city atoms/game-map "X"))]
      (swap! atoms/production assoc x-pos {:item :army}))
    (computer-production/process-computer-city (:pos (get-test-city atoms/game-map "X")))
    (should= :army (:item (get @atoms/production (:pos (get-test-city atoms/game-map "X"))))))

  (it "computer-production.txt:137 - Computer production does not repeat after spawning"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~~" "~X~" "~~~"]))
    (let [x-pos (:pos (get-test-city atoms/game-map "X"))]
      (swap! atoms/production assoc x-pos {:item :army :remaining-rounds 1}))
    (game-loop/start-new-round)
    (game-loop/advance-game)
    (let [{:keys [pos]} (get-test-unit atoms/game-map "a")]
      (should= [1 1] pos))
    (let [prod (get @atoms/production (:pos (get-test-city atoms/game-map "X")))]
      (should (or (nil? prod) (= :none prod))))))
