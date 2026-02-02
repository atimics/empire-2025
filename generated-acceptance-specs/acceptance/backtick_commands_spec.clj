(ns acceptance.backtick-commands-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map get-test-unit
                                       reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.ui.input :as input]
            [quil.core :as q]))

(defn- setup-map-screen-dimensions!
  "Sets map-screen-dimensions so that determine-cell-coordinates maps
   pixel (x * cw, y * ch) back to cell [x y]."
  []
  (let [[cw ch] config/cell-size
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))]
    (reset! atoms/map-screen-dimensions [(* cols cw) (* rows ch)])))

(describe "backtick-commands.txt"

  ;; backtick-commands.txt:6 - Spawn player army on land.
  (it "backtick-commands.txt:6 - Spawn player army on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :A)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "A")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:17 - Spawn player fighter on land.
  (it "backtick-commands.txt:17 - Spawn player fighter on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :F)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "F")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:28 - Spawn player transport on sea.
  (it "backtick-commands.txt:28 - Spawn player transport on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :T)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:39 - Spawn player destroyer on sea.
  (it "backtick-commands.txt:39 - Spawn player destroyer on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :D)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "D")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:50 - Spawn player carrier on sea.
  (it "backtick-commands.txt:50 - Spawn player carrier on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :C)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "C")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:61 - Spawn player submarine on sea.
  (it "backtick-commands.txt:61 - Spawn player submarine on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :S)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "S")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:72 - Spawn player patrol-boat on sea.
  (it "backtick-commands.txt:72 - Spawn player patrol-boat on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :P)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "P")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:83 - Spawn player battleship on sea.
  (it "backtick-commands.txt:83 - Spawn player battleship on sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :B)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "B")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:94 - Spawn player satellite on land.
  (it "backtick-commands.txt:94 - Spawn player satellite on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :Z)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "V")]
      (should= [0 0] pos)
      (should= :player (:owner unit))))

  ;; backtick-commands.txt:105 - Spawn computer army on land.
  (it "backtick-commands.txt:105 - Spawn computer army on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :a)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "a")]
      (should= [0 0] pos)
      (should= :computer (:owner unit))))

  ;; backtick-commands.txt:116 - Spawn computer fighter on land.
  (it "backtick-commands.txt:116 - Spawn computer fighter on land"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["##"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :f)))
    (let [{:keys [pos unit]} (get-test-unit atoms/game-map "f")]
      (should= [0 0] pos)
      (should= :computer (:owner unit))))

  ;; backtick-commands.txt:127 - Own free city at mouse.
  (it "backtick-commands.txt:127 - Own free city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["+#"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :o)))
    (should= :player (:city-status (get-in @atoms/game-map [0 0]))))

  ;; backtick-commands.txt:137 - Own computer city at mouse.
  (it "backtick-commands.txt:137 - Own computer city at mouse"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["X#"]))
    (setup-map-screen-dimensions!)
    (let [[cw ch] config/cell-size]
      (with-redefs [q/mouse-x (constantly (* 0 cw))
                    q/mouse-y (constantly (* 0 ch))]
        (input/key-down (keyword "`"))
        (input/key-down :o)))
    (should= :player (:city-status (get-in @atoms/game-map [0 0])))))
