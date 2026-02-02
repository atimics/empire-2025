(ns acceptance.transports-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city get-test-cell reset-all-atoms!
                                       make-initial-test-map]]
            [empire.atoms :as atoms]
            [empire.player.production :as production]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.containers.ops :as container-ops]
            [empire.ui.input :as input]))

(describe "transports.txt"

  ;; transports.txt:4 - Transport produced at player city is awake with no computer fields.
  (it "transports.txt:4 - Transport produced at player city is awake with no computer fields"
    (reset-all-atoms!)
    ;; GIVEN game map ["~O" "O#"]
    (reset! atoms/game-map (build-test-map ["~O" "O#"]))
    ;; GIVEN production at O2 is transport with 1 round remaining.
    (let [city-coords (:pos (get-test-city atoms/game-map "O2"))]
      (swap! atoms/production assoc city-coords {:item :transport :remaining-rounds 1}))
    ;; WHEN the game advances.
    (production/update-production)
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should-not-be-nil unit)
      (should= :awake (:mode unit))
      (should= :player (:owner unit))
      (should-be-nil (:transport-mission unit))
      (should-be-nil (:stuck-since-round unit))
      (should-be-nil (:transport-id unit))))

  ;; transports.txt:19 - Sentry armies adjacent to sentry transport board it.
  (it "transports.txt:19 - Sentry armies adjacent to sentry transport board it"
    (reset-all-atoms!)
    ;; GIVEN game map ["#--" "AT-" "-A-"]
    ;; Columns: col0="#A-" col1="-T-" ... wait, strings are columns.
    ;; "#--" is col0: land, nil, nil
    ;; "AT-" is col1: land+army, sea+transport, nil
    ;; "-A-" is col2: nil, land+army, nil
    (reset! atoms/game-map (build-test-map ["#--" "AT-" "-A-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry)
    (set-test-unit atoms/game-map "A1" :mode :sentry)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    ;; Capture positions before loading
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))
          a1-pos (:pos (get-test-unit atoms/game-map "A1"))
          a2-pos (:pos (get-test-unit atoms/game-map "A2"))]
      ;; WHEN - load-adjacent-sentry-armies (sentry transport loading is triggered
      ;; by wake conditions, not by process-player-items-batch)
      (container-ops/load-adjacent-sentry-armies t-pos)
      ;; THEN
      (let [transport (:contents (get-in @atoms/game-map t-pos))]
        (should= 2 (:army-count transport)))
      (should-be-nil (:contents (get-in @atoms/game-map a1-pos)))
      (should-be-nil (:contents (get-in @atoms/game-map a2-pos)))))

  ;; transports.txt:35 - Transport wakes at beach with armies aboard.
  (it "transports.txt:35 - Transport wakes at beach with armies aboard"
    (reset-all-atoms!)
    ;; GIVEN game map ["~T=~" "~~#~"]
    (reset! atoms/game-map (build-test-map ["~T=~" "~~#~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 2 4 nil))
      ;; WHEN the game advances.
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      ;; THEN
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-at-beach (:reason unit)))))

  ;; transports.txt:47 - Transport does not wake at beach without armies.
  (it "transports.txt:47 - Transport does not wake at beach without armies"
    (reset-all-atoms!)
    ;; GIVEN game map ["~T=~" "~~#~"]
    (reset! atoms/game-map (build-test-map ["~T=~" "~~#~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 2 4 nil))
      ;; WHEN the game advances.
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      ;; THEN
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should-not= :transport-at-beach (:reason unit)))))

  ;; transports.txt:59 - Wake armies command puts transport to sentry and wakes armies.
  (it "transports.txt:59 - Wake armies command puts transport to sentry and wakes armies"
    (reset-all-atoms!)
    ;; GIVEN game map ["-T-" "-#-"]
    (reset! atoms/game-map (build-test-map ["-T-" "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :awake :army-count 2
                   :reason :transport-at-beach)
    ;; Set up attention state for handle-key
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/cells-needing-attention [t-pos])
      (reset! atoms/waiting-for-input true)
      ;; WHEN the player presses u.
      (input/handle-key :u))
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should= :sentry (:mode unit))
      (should= 2 (:army-count unit))
      (should= 2 (:awake-armies unit))
      (should-be-nil (:reason unit))))

  ;; transports.txt:72 - Disembarking army removes it from transport.
  ;; Land % is to the right of transport.
  (it "transports.txt:72 - Disembarking army removes it from transport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["-T-" "-%-"]))
    (let [land-pos (:pos (get-test-cell atoms/game-map "%"))]
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 3 :awake-armies 3)
      (reset! atoms/player-map (make-initial-test-map 2 3 nil))
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        ;; WHEN player items are processed (transport has awake armies → needs attention)
        (item-processing/process-player-items-batch)
        ;; WHEN the player presses d (east → toward land).
        (input/handle-key :d))
      ;; THEN
      (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
        (should= 2 (:army-count unit))
        (should= 2 (:awake-armies unit)))
      (should= :army (:type (:contents (get-in @atoms/game-map land-pos))))))

  ;; transports.txt:86 - Transport wakes when last army disembarks.
  ;; Land is to the right of transport.
  (it "transports.txt:86 - Transport wakes when last army disembarks"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["-T-" "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 1 :awake-armies 1)
    (reset! atoms/player-map (make-initial-test-map 2 3 nil))
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-items [t-pos])
      ;; WHEN player items are processed.
      (item-processing/process-player-items-batch)
      ;; WHEN the player presses d (east → toward land at [1,1]).
      (input/handle-key :d))
    ;; THEN
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should= :awake (:mode unit))
      (should= 0 (:army-count unit))))

  ;; transports.txt:99 - Transport finds land when moving from open sea.
  (it "transports.txt:99 - Transport finds land when moving from open sea"
    (reset-all-atoms!)
    ;; GIVEN game map ["~~~#" "~T=~" "~~~~"]
    (reset! atoms/game-map (build-test-map ["~~~#" "~T=~" "~~~~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 3 4 nil))
      ;; WHEN the game advances.
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      ;; THEN
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-found-land (:reason unit)))))

  ;; transports.txt:112 - Transport wakes at first beach after going to open sea.
  (it "transports.txt:112 - Transport wakes at first beach after going to open sea"
    (reset-all-atoms!)
    ;; GIVEN game map ["#~~~" "~T=#"]
    (reset! atoms/game-map (build-test-map ["#~~~" "~T=#"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :been-to-sea true :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 2 4 nil))
      ;; WHEN the game advances.
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      ;; THEN
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-at-beach (:reason unit))
        (should= false (:been-to-sea unit))))))
