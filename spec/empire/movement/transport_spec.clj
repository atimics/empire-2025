(ns empire.movement.transport-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :refer :all]
    [empire.test-utils :refer [build-test-map set-test-unit]]
    [speclj.core :refer :all]))

(describe "transport with armies"
  (it "loads adjacent sentry armies onto transport"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    ;; Add adjacent sentry armies
    (swap! atoms/game-map assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
    (swap! atoms/game-map assoc-in [5 4] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (load-adjacent-sentry-armies [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= 2 (:army-count transport)))
    (should= nil (:contents (get-in @atoms/game-map [4 3])))
    (should= nil (:contents (get-in @atoms/game-map [5 4]))))

  (it "does not load awake armies onto transport"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "----T----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    ;; Add adjacent awake army
    (swap! atoms/game-map assoc-in [4 3] {:type :land :contents {:type :army :mode :awake :owner :player :hits 1}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (load-adjacent-sentry-armies [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= 0 (:army-count transport 0)))
    (should-not= nil (:contents (get-in @atoms/game-map [4 3]))))

  (it "wakes transport after loading armies if at beach"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    ;; Add adjacent sentry army
    (swap! atoms/game-map assoc-in [4 3] {:type :land :contents {:type :army :mode :sentry :owner :player :hits 1}})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (load-adjacent-sentry-armies [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode transport))
      (should= :transport-at-beach (:reason transport))
      (should= 1 (:army-count transport))))

  (it "wake-armies-on-transport wakes all armies and sets transport to sentry"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach)
    (wake-armies-on-transport [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= :sentry (:mode transport))
      (should= nil (:reason transport))
      (should= 2 (:army-count transport))
      (should= 2 (:awake-armies transport))))

  (it "sleep-armies-on-transport puts armies to sleep and wakes transport"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (sleep-armies-on-transport [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode transport))
      (should= nil (:reason transport))
      (should= 2 (:army-count transport))
      (should= 0 (:awake-armies transport))))

  (it "disembark-army-from-transport removes one army and decrements counts"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (disembark-army-from-transport [4 4] [5 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))
          disembarked (:contents (get-in @atoms/game-map [5 4]))]
      (should= 2 (:army-count transport))
      (should= 2 (:awake-armies transport))
      (should= :army (:type disembarked))
      (should= :awake (:mode disembarked))))

  (it "disembark-army-from-transport wakes transport when last army disembarks"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (disembark-army-from-transport [4 4] [5 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode transport))
      (should= 0 (:army-count transport))))

  (it "disembark-army-from-transport wakes transport when no more awake armies remain"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (disembark-army-from-transport [4 4] [5 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))]
      (should= :awake (:mode transport))
      (should= 1 (:army-count transport))
      (should= 0 (:awake-armies transport))))

  (it "transport wakes up when reaching beach with armies"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T~---"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target [4 5] :steps-remaining 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 5]))]
      (should= :awake (:mode transport))
      (should= :transport-at-beach (:reason transport))))

  (it "transport does not wake when reaching beach without armies"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T~---"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :target [4 5] :steps-remaining 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 5]))]
      (should= :awake (:mode transport))
      (should= nil (:reason transport))))

  (it "completely-surrounded-by-sea? returns true when no adjacent land"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving)
    (should (completely-surrounded-by-sea? [4 4] atoms/game-map)))

  (it "completely-surrounded-by-sea? returns false when adjacent to land"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T#~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving)
    (should-not (completely-surrounded-by-sea? [4 4] atoms/game-map)))

  (it "transport wakes with found-land when moving from open sea to land visible"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~~#~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target [4 5] :steps-remaining 1)
    ;; Transport at [4 4] completely surrounded by sea
    ;; Target at [4 5] is sea but has land at [4 6] (adjacent to [4 5] but not to [4 4])
    (swap! atoms/game-map assoc-in [4 6] {:type :land})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 5]))]
      (should= :awake (:mode transport))
      (should= :transport-found-land (:reason transport))))

  (it "transport does not wake with found-land when already near land before move"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~#~~~~~~"
                                             "~~~~T~~~~"
                                             "~~~~~#~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target [4 5] :steps-remaining 1)
    ;; Transport at [4 4] already has land at [3 3]
    ;; Target at [4 5] also near land at [5 5]
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 5]))]
      ;; Still wakes because it's at beach with armies, but reason should be :transport-at-beach
      (should= :awake (:mode transport))
      (should= :transport-at-beach (:reason transport))))

  (it "transport wakes with found-land even without armies"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :target [4 5] :steps-remaining 1)
    ;; Transport at [4 4] completely surrounded by sea, no armies
    ;; Target at [4 5] is sea but has land at [4 6] (adjacent to [4 5] but not to [4 4])
    (swap! atoms/game-map assoc-in [4 6] {:type :land})
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (game-loop/move-current-unit [4 4])
    (let [transport (:contents (get-in @atoms/game-map [4 5]))]
      (should= :awake (:mode transport))
      (should= :transport-found-land (:reason transport))))

  (it "get-active-unit returns synthetic army when transport has awake armies"
    (let [cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :army-count 3 :awake-armies 2}}]
      (let [active (get-active-unit cell)]
        (should= :army (:type active))
        (should= :awake (:mode active))
        (should= true (:aboard-transport active)))))

  (it "get-active-unit returns transport when no awake armies"
    (let [cell {:type :sea :contents {:type :transport :mode :awake :owner :player :army-count 1 :awake-armies 0}}]
      (let [active (get-active-unit cell)]
        (should= :transport (:type active))
        (should= :awake (:mode active)))))

  (it "is-army-aboard-transport? returns true for synthetic army with :aboard-transport"
    (let [army {:type :army :mode :awake :owner :player :aboard-transport true}]
      (should= true (is-army-aboard-transport? army))))

  (it "is-army-aboard-transport? returns falsy for army without :aboard-transport"
    (let [army {:type :army :mode :awake :owner :player :hits 1}]
      (should-not (is-army-aboard-transport? army)))))

(describe "disembark-army-with-target"
  (it "disembarks army and sets it moving toward extended target"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "----#----"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (disembark-army-with-target [4 4] [5 4] [8 4])
    (let [transport (:contents (get-in @atoms/game-map [4 4]))
          army (:contents (get-in @atoms/game-map [5 4]))]
      (should= 1 (:army-count transport))
      (should= 1 (:awake-armies transport))
      (should= :army (:type army))
      (should= :moving (:mode army))
      (should= [8 4] (:target army))
      (should= 0 (:steps-remaining army)))))

(describe "disembark-army-to-explore"
  (it "disembarks army in explore mode"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [result (disembark-army-to-explore [4 4] [5 4])]
      (should= [5 4] result)
      (let [transport (:contents (get-in @atoms/game-map [4 4]))
            army (:contents (get-in @atoms/game-map [5 4]))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :explore (:mode army))
        (should= #{[5 4]} (:visited army))))))
