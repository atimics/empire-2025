(ns empire.acceptance.generator-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.generator :as gen]
            [clojure.string :as str]
            [clojure.edn :as edn]))

;; --- Helper to load EDN test data ---

(defn- load-edn [filename]
  (edn/read-string (slurp (str "acceptanceTests/edn/" filename))))

(defn- normalize-whitespace [s]
  (-> s
      str/trim
      (str/replace #"\r\n" "\n")
      (str/replace #"[ \t]+\n" "\n")))

;; --- determine-needs tests ---

(describe "determine-needs"

  (it "detects :config when thens have :config-key"
    (let [tests [{:givens [] :whens [] :thens [{:type :message-contains :area :attention :config-key :foo}]}]]
      (should-contain :config (gen/determine-needs tests))))

  (it "detects :game-loop when whens have :start-new-round"
    (let [tests [{:givens [] :whens [{:type :start-new-round}] :thens []}]]
      (should-contain :game-loop (gen/determine-needs tests))))

  (it "detects :item-processing when givens have :waiting-for-input"
    (let [tests [{:givens [{:type :waiting-for-input :unit "A" :set-mode true}] :whens [] :thens []}]]
      (should-contain :item-processing (gen/determine-needs tests))))

  (it "detects :quil when whens have :key-down input-fn"
    (let [tests [{:givens [] :whens [{:type :key-press :key :s :input-fn :key-down}] :thens []}]]
      (should-contain :quil (gen/determine-needs tests))))

  (it "detects :advance-helper when thens have :unit-at-next-round"
    (let [tests [{:givens [] :whens [] :thens [{:type :unit-at-next-round :unit "D" :target "="}]}]]
      (should-contain :advance-helper (gen/determine-needs tests))))

  (it "detects :get-test-cell when thens reference = target"
    (let [tests [{:givens [] :whens [] :thens [{:type :unit-at-next-round :unit "D" :target "="}]}]]
      (should-contain :get-test-cell (gen/determine-needs tests))))

  (it "detects :get-test-city when thens have container-prop with :city lookup"
    (let [tests [{:givens [] :whens [] :thens [{:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city}]}]]
      (should-contain :get-test-city (gen/determine-needs tests))))

  (it "detects :game-loop but not :advance-helper when thens have :at-next-step"
    (let [tests [{:givens [] :whens [] :thens [{:type :message-contains :area :attention :config-key :foo :at-next-step true}]}]]
      (should-contain :game-loop (gen/determine-needs tests))
      (should-not-contain :advance-helper (gen/determine-needs tests))))

  (it "detects :item-processing when whens have :waiting-for-input"
    (let [tests [{:givens [] :whens [{:type :waiting-for-input :unit "F" :set-mode true}] :thens []}]]
      (should-contain :item-processing (gen/determine-needs tests))
      (should-contain :make-initial-test-map (gen/determine-needs tests))))

  (it "detects :advance-until-waiting-helper when whens have :advance-until-waiting"
    (let [tests [{:givens [] :whens [{:type :advance-until-waiting :unit "F"}] :thens []}]
          needs (gen/determine-needs tests)]
      (should-contain :advance-until-waiting-helper needs)
      (should-contain :quil needs)
      (should-contain :game-loop needs)))

  (it "detects :advance-until-waiting-helper when thens have :unit-waiting-for-input"
    (let [tests [{:givens [] :whens [] :thens [{:type :unit-waiting-for-input :unit "C"}]}]
          needs (gen/determine-needs tests)]
      (should-contain :advance-until-waiting-helper needs)
      (should-contain :quil needs)
      (should-contain :game-loop needs))))

;; --- generate-given tests ---

(describe "generate-given"

  (it "generates map given"
    (let [result (gen/generate-given {:type :map :target :game-map :rows ["A#"]})]
      (should-contain "build-test-map" result)
      (should-contain "\"A#\"" result)))

  (it "generates unit-props given"
    (let [result (gen/generate-given {:type :unit-props :unit "F" :props {:fuel 32}})]
      (should-contain "set-test-unit" result)
      (should-contain ":fuel 32" result)))

  (it "generates waiting-for-input given with set-mode true"
    (let [result (gen/generate-given {:type :waiting-for-input :unit "A" :set-mode true})]
      (should-contain "set-test-unit" result)
      (should-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)
      (should-contain "process-player-items-batch" result)))

  (it "generates waiting-for-input given with set-mode false"
    (let [result (gen/generate-given {:type :waiting-for-input :unit "A" :set-mode false})]
      (should-not-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)))

  (it "generates waiting-for-input given for airport fighter not on map"
    (let [givens [{:type :map :target :game-map :rows ["O%"]}
                  {:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}}
                  {:type :waiting-for-input :unit "F" :set-mode true}]
          result (gen/generate-given (nth givens 2) givens)]
      (should-not-contain "set-test-unit" result)
      (should-contain "get-test-city" result)
      (should-contain "\"O\"" result)
      (should-contain "process-player-items-batch" result)))

  (it "generates container-state given for city"
    (let [result (gen/generate-given {:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}})]
      (should-contain "get-test-city" result)
      (should-contain ":fighter-count 1" result)))

  (it "generates container-state given for unit"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:fighter-count 0}})]
      (should-contain "set-test-unit" result)
      (should-contain ":fighter-count 0" result)))

  (it "generates container-state given for unit with awake-fighters but no fighter-count"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:awake-fighters 1}})]
      (should-contain "set-test-unit" result)
      (should-contain ":awake-fighters 1" result)
      (should-contain ":fighter-count 1" result)))

  (it "generates container-state given for unit with awake-armies but no army-count"
    (let [result (gen/generate-given {:type :container-state :target "T" :props {:awake-armies 2}})]
      (should-contain "set-test-unit" result)
      (should-contain ":awake-armies 2" result)
      (should-contain ":army-count 2" result)))

  (it "generates production given"
    (let [result (gen/generate-given {:type :production :city "O" :item :army :remaining-rounds 10})]
      (should-contain "atoms/production" result)
      (should-contain ":army" result)
      (should-contain ":remaining-rounds 10" result)))

  (it "generates round given"
    (let [result (gen/generate-given {:type :round :value 5})]
      (should= "    (reset! atoms/round-number 5)" result)))

  (it "generates destination given"
    (let [result (gen/generate-given {:type :destination :coords [3 4]})]
      (should= "    (reset! atoms/destination [3 4])" result)))

  (it "generates unrecognized given"
    (let [result (gen/generate-given {:type :unrecognized :text "something weird"})]
      (should-contain "pending" result)
      (should-contain "something weird" result))))

;; --- generate-when tests ---

(describe "generate-when"

  (it "generates key-down when"
    (let [result (gen/generate-when {:type :key-press :key :s :input-fn :key-down})]
      (should-contain "q/mouse-x" result)
      (should-contain "input/key-down :s" result)))

  (it "generates handle-key when"
    (let [result (gen/generate-when {:type :key-press :key :d :input-fn :handle-key})]
      (should-contain "input/handle-key :d" result)
      (should-not-contain "q/mouse-x" result)))

  (it "generates army battle win when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :win :combat-type :army})]
      (should-contain "rand (constantly 0.0)" result)
      (should-contain "input/handle-key :d" result)
      (should-not-contain "advance-game" result)))

  (it "generates ship battle win when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :win :combat-type :ship})]
      (should-contain "rand (constantly 0.0)" result)
      (should-contain "advance-game" result)))

  (it "generates ship battle lose when"
    (let [result (gen/generate-when {:type :battle :key :d :outcome :lose :combat-type :ship})]
      (should-contain "rand (constantly 1.0)" result)
      (should-contain "advance-game" result)))

  (it "generates backtick when"
    (let [result (gen/generate-when {:type :backtick :key :A :mouse-cell [0 0]})]
      (should-contain "map-screen-dimensions" result)
      (should-contain "keyword \"`\"" result)
      (should-contain "input/key-down :A" result)))

  (it "generates start-new-round when"
    (let [result (gen/generate-when {:type :start-new-round})]
      (should-contain "start-new-round" result)
      (should-contain "advance-game" result)))

  (it "generates advance-game when"
    (let [result (gen/generate-when {:type :advance-game})]
      (should-contain "advance-game" result)))

  (it "generates advance-until-waiting when"
    (let [result (gen/generate-when {:type :advance-until-waiting :unit "F"})]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"F\"" result)))

  (it "generates waiting-for-input when"
    (let [result (gen/generate-when {:type :waiting-for-input :unit "F" :set-mode true})]
      (should-contain "set-test-unit" result)
      (should-contain ":mode :awake" result)
      (should-contain "make-initial-test-map" result)
      (should-contain "process-player-items-batch" result))))

;; --- generate-then tests ---

(describe "generate-then"

  (it "generates unit-prop then"
    (let [result (gen/generate-then {:type :unit-prop :unit "A" :property :mode :expected :sentry} [])]
      (should-contain "should=" result)
      (should-contain ":sentry" result)
      (should-contain ":mode" result)))

  (it "generates unit-absent then"
    (let [result (gen/generate-then {:type :unit-absent :unit "s"} [])]
      (should-contain "should-be-nil" result)
      (should-contain "\"s\"" result)))

  (it "generates unit-at then with named target"
    (let [result (gen/generate-then {:type :unit-at :unit "F" :target "="} [])]
      (should-contain "get-test-cell" result)
      (should-contain "should=" result)))

  (it "generates unit-present then with coords"
    (let [result (gen/generate-then {:type :unit-present :unit "A" :coords [0 0]} [])]
      (should-contain "should=" result)
      (should-contain "[0 0]" result)))

  (it "generates unit-at-next-round then with timeout check"
    (let [result (gen/generate-then {:type :unit-at-next-round :unit "D" :target "="} [])]
      (should-contain "should= :ok (advance-until-next-round)" result)
      (should-contain "get-test-cell" result)))

  (it "generates unit-after-moves then"
    (let [result (gen/generate-then {:type :unit-after-moves :unit "F" :moves 2 :target "="} [])]
      (should-contain "dotimes" result)
      (should-contain "_ 2" result)
      (should-contain "get-test-cell" result)))

  (it "generates unit-after-steps then with step advances"
    (let [result (gen/generate-then {:type :unit-after-steps :unit "F" :steps 1 :target "%"} [])]
      (should-contain "advance-game" result)
      (should-contain "dotimes" result)
      (should-contain "_ 1" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "get-test-cell" result)))

  (it "generates unit-after-steps then with multiple steps"
    (let [result (gen/generate-then {:type :unit-after-steps :unit "F" :steps 3 :target "%"} [])]
      (should-contain "_ 3" result)))

  (it "generates unit-occupies-cell then"
    (let [givens [{:type :map :target :game-map :rows ["Ds"]}]
          result (gen/generate-then {:type :unit-occupies-cell :unit "D" :target-unit "s"} givens)]
      (should-contain "should=" result)
      (should-contain "[1 0]" result)))

  (it "generates unit-unmoved then"
    (let [givens [{:type :map :target :game-map :rows ["Ds"]}]
          result (gen/generate-then {:type :unit-unmoved :unit "s"} givens)]
      (should-contain "should=" result)
      (should-contain "[1 0]" result)))

  (it "generates unit-waiting-for-input then"
    (let [result (gen/generate-then {:type :unit-waiting-for-input :unit "F"} [])]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"F\"" result)))

  (it "generates message-contains with config-key then including existence check"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :army-found-city} [])]
      (should-contain "should-not-be-nil" result)
      (should-contain "should-contain" result)
      (should-contain ":army-found-city" result)
      (should-contain "config/messages" result)
      (should-contain "atoms/attention-message" result)))

  (it "generates message-contains with text then"
    (let [result (gen/generate-then {:type :message-contains :area :attention :text "fuel:20"} [])]
      (should-contain "should-contain" result)
      (should-contain "\"fuel:20\"" result)
      (should-contain "atoms/attention-message" result)))

  (it "generates message-contains with turn area"
    (let [result (gen/generate-then {:type :message-contains :area :turn :text "Destroyer destroyed"} [])]
      (should-contain "atoms/turn-message" result)))

  (it "generates message-contains with :at-next-round and timeout check"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-round true} [])]
      (should-contain "should= :ok (advance-until-next-round)" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "should-contain" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-contains with :at-next-step using advance-game"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true} [])]
      (should-contain "game-loop/advance-game" result)
      (should-not-contain "advance-until-next-round" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "should-contain" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-for-unit then with advance loop"
    (let [result (gen/generate-then {:type :message-for-unit :area :attention :unit "F" :config-key :fighter-bingo} [])]
      (should-contain "loop [n 100]" result)
      (should-contain "get-test-unit" result)
      (should-contain ":awake" result)
      (should-contain "should-not-be-nil" result)
      (should-contain ":fighter-bingo" result)
      (should-contain "atoms/attention-message" result)))

  (it "generates message-is with config-key then"
    (let [result (gen/generate-then {:type :message-is :area :turn :config-key :hit-edge} [])]
      (should-contain "should=" result)
      (should-contain ":hit-edge" result)
      (should-contain "atoms/turn-message" result)))

  (it "generates cell-prop then"
    (let [result (gen/generate-then {:type :cell-prop :coords [1 0] :property :city-status :expected :player} [])]
      (should-contain "should=" result)
      (should-contain ":player" result)
      (should-contain "[1 0]" result)))

  (it "generates waiting-for-input true then"
    (let [result (gen/generate-then {:type :waiting-for-input :expected true} [])]
      (should-contain "should @atoms/waiting-for-input" result)))

  (it "generates waiting-for-input false then"
    (let [result (gen/generate-then {:type :waiting-for-input :expected false} [])]
      (should-contain "should-not @atoms/waiting-for-input" result)))

  (it "generates unit-waiting-for-input then with advance"
    (let [result (gen/generate-then {:type :unit-waiting-for-input :unit "C"} [])]
      (should-contain "advance-until-unit-waiting" result)
      (should-contain "\"C\"" result)
      (should-contain "should=" result)))

  (it "generates container-state given for unit with container props"
    (let [result (gen/generate-given {:type :container-state :target "C" :props {:fighter-count 2 :awake-fighters 0}})]
      (should-contain "set-test-unit" result)
      (should-contain ":fighter-count 2" result)
      (should-contain ":awake-fighters 0" result)))

  (it "generates container-prop city lookup then"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city} [])]
      (should-contain "get-test-city" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop unit lookup then"
    (let [result (gen/generate-then {:type :container-prop :target "C" :property :fighter-count :expected 1 :lookup :unit} [])]
      (should-contain "get-test-unit" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop unit lookup for unit-level prop"
    (let [result (gen/generate-then {:type :container-prop :target "C" :property :awake-fighters :expected 2 :lookup :unit} [])]
      (should-contain "get-test-unit" result)
      (should-contain ":awake-fighters" result)
      (should-contain ":unit" result)))

  (it "generates container-prop city lookup with at-next-step"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city :at-next-step true} [])]
      (should-contain "game-loop/advance-game" result)
      (should-contain ":fighter-count" result)))

  (it "generates container-prop city lookup with at-next-round"
    (let [result (gen/generate-then {:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city :at-next-round true} [])]
      (should-contain "advance-until-next-round" result)
      (should-contain ":fighter-count" result))))

;; --- Integration: generate-spec on actual EDN data ---

(describe "generate-spec integration"

  (it "generates army spec with correct ns form"
    (let [edn-data (load-edn "army.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.army-spec" result)
      (should-contain "speclj.core :refer :all" result)
      (should-contain "empire.atoms :as atoms" result)
      (should-contain "empire.config :as config" result)
      (should-contain "empire.ui.input :as input" result)
      (should-contain "quil.core :as q" result)))

  (it "generates army spec with correct describe"
    (let [edn-data (load-edn "army.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "(describe \"army.txt\"" result)))

  (it "generates army spec with all 6 tests"
    (let [edn-data (load-edn "army.edn")
          result (gen/generate-spec edn-data)]
      (should= 6 (count (re-seq #"\(it " result)))))

  (it "generates army spec with correct test descriptions"
    (let [edn-data (load-edn "army.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "army.txt:7 - Army put to sentry mode" result)
      (should-contain "army.txt:18 - Army set to explore mode" result)
      (should-contain "army.txt:29 - Army wakes near hostile city with reason" result)
      (should-contain "army.txt:41 - Army conquers free city" result)
      (should-contain "army.txt:52 - Army skips round with space" result)
      (should-contain "army.txt:63 - Army blocked by friendly city" result)))

  (it "generates army spec sentry test with correct assertions"
    (let [edn-data (load-edn "army.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "build-test-map [\"A#\"]" result)
      (should-contain "(should= :sentry (:mode (:unit (get-test-unit atoms/game-map \"A\"))))" result)))

  (it "generates backtick-commands spec with correct ns form"
    (let [edn-data (load-edn "backtick-commands.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.backtick-commands-spec" result)
      (should-contain "quil.core :as q" result)))

  (it "generates backtick-commands spec with all 13 tests"
    (let [edn-data (load-edn "backtick-commands.edn")
          result (gen/generate-spec edn-data)]
      (should= 13 (count (re-seq #"\(it " result)))))

  (it "generates backtick-commands spec with map-screen-dimensions"
    (let [edn-data (load-edn "backtick-commands.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "map-screen-dimensions" result)))

  (it "generates destroyer spec with all 4 tests"
    (let [edn-data (load-edn "destroyer.edn")
          result (gen/generate-spec edn-data)]
      (should= 4 (count (re-seq #"\(it " result)))))

  (it "generates destroyer spec with advance-game for ship battles"
    (let [edn-data (load-edn "destroyer.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "advance-game" result)))

  (it "generates fighter spec with all 9 tests"
    (let [edn-data (load-edn "fighter.edn")
          result (gen/generate-spec edn-data)]
      (should= 9 (count (re-seq #"\(it " result)))))

  (it "generates fighter spec with advance-until-next-round helper"
    (let [edn-data (load-edn "fighter.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "defn- advance-until-next-round" result)))

  (it "generates advance-until-next-round with loop and timeout"
    (let [edn-data (load-edn "fighter.edn")
          result (gen/generate-spec edn-data)]
      (should-contain "loop [n 100]" result)
      (should-contain ":timeout" result)
      (should-contain ":ok" result)))

  (it "generates advance-until-unit-waiting helper when needed"
    (let [result (gen/generate-helper-fns #{:advance-until-waiting-helper})]
      (should-contain "defn- advance-until-unit-waiting" result)
      (should-contain "cells-needing-attention" result)
      (should-contain ":timeout" result)
      (should-contain ":x" result)
      (should-contain ":space" result)))

  (it "advance-until-unit-waiting does not require awake mode"
    (let [result (gen/generate-helper-fns #{:advance-until-waiting-helper})]
      (should-not-contain ":awake" result))))
