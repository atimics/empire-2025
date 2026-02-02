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

  ;; transports.txt:6 - Transport produced at player city is awake with no computer fields.
  (it "transports.txt:6 - Transport produced at player city is awake with no computer fields"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O" "O#"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O2"))]
      (swap! atoms/production assoc city-coords {:item :transport :remaining-rounds 1}))
    (production/update-production)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should-not-be-nil unit)
      (should= :awake (:mode unit))
      (should= :player (:owner unit))
      (should-be-nil (:transport-mission unit))
      (should-be-nil (:stuck-since-round unit))
      (should-be-nil (:transport-id unit))))

  ;; transports.txt:23 - Sentry armies adjacent to sentry transport board it.
  (it "transports.txt:23 - Sentry armies adjacent to sentry transport board it"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#A-" "-TA" "---"]))
    (set-test-unit atoms/game-map "T" :mode :sentry)
    (set-test-unit atoms/game-map "A1" :mode :sentry)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))
          a1-pos (:pos (get-test-unit atoms/game-map "A1"))
          a2-pos (:pos (get-test-unit atoms/game-map "A2"))]
      (container-ops/load-adjacent-sentry-armies t-pos)
      (let [transport (:contents (get-in @atoms/game-map t-pos))]
        (should= 2 (:army-count transport)))
      (should-be-nil (:contents (get-in @atoms/game-map a1-pos)))
      (should-be-nil (:contents (get-in @atoms/game-map a2-pos)))))

  ;; transports.txt:41 - Transport wakes at beach with armies aboard.
  (it "transports.txt:41 - Transport wakes at beach with armies aboard"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~" "T~" "=#" "~~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 4 2 nil))
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-at-beach (:reason unit)))))

  ;; transports.txt:57 - Transport does not wake at beach without armies.
  (it "transports.txt:57 - Transport does not wake at beach without armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~" "T~" "=#" "~~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 4 2 nil))
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should-not= :transport-at-beach (:reason unit)))))

  ;; transports.txt:73 - Wake armies command puts transport to sentry and wakes armies.
  (it "transports.txt:73 - Wake armies command puts transport to sentry and wakes armies"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["--" "T#" "--"]))
    (set-test-unit atoms/game-map "T" :mode :awake :army-count 2
                   :reason :transport-at-beach)
    ;; GIVEN T is waiting for input (mode already set above, don't override)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :u)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should= :sentry (:mode unit))
      (should= 2 (:army-count unit))
      (should= 2 (:awake-armies unit))
      (should-be-nil (:reason unit))))

  ;; transports.txt:91 - Disembarking army removes it from transport.
  ;; Land % is to the right of transport.
  (it "transports.txt:91 - Disembarking army removes it from transport"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["--" "T%" "--"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 3 :awake-armies 3)
    ;; GIVEN T is waiting for input (mode already set above, don't override)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (let [land-pos (:pos (get-test-cell atoms/game-map "%"))
          {:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should= 2 (:army-count unit))
      (should= 2 (:awake-armies unit))
      (should= :army (:type (:contents (get-in @atoms/game-map land-pos))))))

  ;; transports.txt:108 - Transport wakes when last army disembarks.
  ;; Land is to the right of transport.
  (it "transports.txt:108 - Transport wakes when last army disembarks"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["--" "T#" "--"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 1 :awake-armies 1)
    ;; GIVEN T is waiting for input (mode already set above, don't override)
    (let [cols (count @atoms/game-map)
          rows (count (first @atoms/game-map))
          pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-map (make-initial-test-map rows cols nil))
      (reset! atoms/player-items [pos])
      (item-processing/process-player-items-batch))
    (input/handle-key :d)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
      (should= :awake (:mode unit))
      (should= 0 (:army-count unit))))

  ;; transports.txt:123 - Transport finds land when moving from open sea.
  (it "transports.txt:123 - Transport finds land when moving from open sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~~~" "~T~" "~=~" "#~~"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 4 3 nil))
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-found-land (:reason unit)))))

  ;; transports.txt:139 - Transport wakes at first beach after going to open sea.
  (it "transports.txt:139 - Transport wakes at first beach after going to open sea"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#~" "~T" "~=" "~#"]))
    (let [target-pos (:pos (get-test-cell atoms/game-map "="))]
      (set-test-unit atoms/game-map "T" :mode :moving :army-count 1
                     :been-to-sea true :target target-pos :steps-remaining 1)
      (reset! atoms/player-map (make-initial-test-map 4 2 nil))
      (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
        (reset! atoms/player-items [t-pos])
        (game-loop/advance-game))
      (let [{:keys [pos unit]} (get-test-unit atoms/game-map "T")]
        (should= target-pos pos)
        (should= :awake (:mode unit))
        (should= :transport-at-beach (:reason unit))
        (should= false (:been-to-sea unit)))))

  ;; transports.txt:156 - Transport with armies can attack city.
  ;; NOTE: These tests document the desired future behavior.

  (it "transports.txt:156 - Army from transport conquers city (conquest succeeds)"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~T~" "#+#"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 1 :awake-armies 1)
    (reset! atoms/player-map (make-initial-test-map 2 3 nil))
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-items [t-pos])
      (item-processing/process-player-items-batch)
      (with-redefs [rand (constantly 0.1)]
        (input/handle-key :x))
      (let [city-cell (get-in @atoms/game-map [1 1])
            transport (:contents (get-in @atoms/game-map t-pos))]
        (should= :player (:city-status city-cell))
        (should= 0 (:army-count transport)))))

  (it "transports.txt:156 - Army from transport attacks city (conquest fails)"
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~T~" "#+#"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 1 :awake-armies 1)
    (reset! atoms/player-map (make-initial-test-map 2 3 nil))
    (let [t-pos (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/player-items [t-pos])
      (item-processing/process-player-items-batch)
      (with-redefs [rand (constantly 0.9)]
        (input/handle-key :x))
      (let [city-cell (get-in @atoms/game-map [1 1])
            transport (:contents (get-in @atoms/game-map t-pos))]
        (should= :free (:city-status city-cell))
        (should= 0 (:army-count transport))))))
