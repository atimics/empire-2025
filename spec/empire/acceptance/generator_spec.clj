(ns empire.acceptance.generator-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.generator :as gen]
            [clojure.string :as str]))

(defn- stub-test [n desc]
  {:line n :description desc :givens [] :whens [] :thens []})

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
      (should-contain :game-loop needs)))

  (it "detects :get-test-city when givens have :production with city target"
    (let [tests [{:givens [{:type :production :city "O" :item :army}] :whens [] :thens []}]]
      (should-contain :get-test-city (gen/determine-needs tests))))

  (it "detects :game-loop when whens have :visibility-update"
    (let [tests [{:givens [] :whens [{:type :visibility-update}] :thens []}]]
      (should-contain :game-loop (gen/determine-needs tests))))

  (it "detects :quil when whens have :mouse-at-key"
    (let [tests [{:givens [] :whens [{:type :mouse-at-key :coords [0 0] :key :period}] :thens []}]]
      (should-contain :quil (gen/determine-needs tests))))

  (it "detects :computer-production when whens have :evaluate-production"
    (let [tests [{:givens [] :whens [{:type :evaluate-production :city "X"}] :thens []}]]
      (should-contain :computer-production (gen/determine-needs tests))))

  (it "detects :visibility-mask when thens have :player-map-visibility"
    (let [tests [{:givens [] :whens [] :thens [{:type :player-map-visibility :rows [".#." ".#."]}]}]]
      (should-contain :visibility-mask (gen/determine-needs tests)))))

;; --- generate-given tests ---

(describe "generate-given"

  (it "generates map given"
    (let [result (gen/generate-given {:type :map :target :game-map :rows ["A#"]})]
      (should-contain "build-test-map" result)
      (should-contain "\"A#\"" result)))

  (it "generates player-map given targeting atoms/player-map"
    (let [result (gen/generate-given {:type :map :target :player-map :rows ["..." ".."]})]
      (should-contain "atoms/player-map" result)
      (should-contain "build-test-map" result)
      (should-not-contain "atoms/game-map" result)))

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

  (it "generates city-prop given"
    (let [result (gen/generate-given {:type :city-prop :city "X" :prop :country-id :value 1})]
      (should-contain "get-test-city" result)
      (should-contain "\"X\"" result)
      (should-contain ":country-id" result)
      (should-contain "1" result)))

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
      (should-contain "process-player-items-batch" result)))

  (it "generates mouse-at-key when with period key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 1] :key :period})]
      (should-contain "q/mouse-x" result)
      (should-contain "q/mouse-y" result)
      (should-contain "input/key-down" result)
      (should-contain (str "(keyword \".\")") result)))

  (it "generates mouse-at-key when with u key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 0] :key :u})]
      (should-contain "q/mouse-x" result)
      (should-contain "input/key-down :u" result)))

  (it "generates mouse-at-key when with l key"
    (let [result (gen/generate-when {:type :mouse-at-key :coords [0 0] :key :l})]
      (should-contain "input/key-down :l" result)))

  (it "generates visibility-update when"
    (let [result (gen/generate-when {:type :visibility-update})]
      (should-contain "update-player-map" result)))

  (it "generates evaluate-production when"
    (let [result (gen/generate-when {:type :evaluate-production :city "X"})]
      (should-contain "computer-production/process-computer-city" result)
      (should-contain "get-test-city" result)
      (should-contain "\"X\"" result))))

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

  (it "generates unit-at-next-step then with single advance"
    (let [result (gen/generate-then {:type :unit-at-next-round :unit "A" :target "%" :at-next-step true} [])]
      (should-contain "game-loop/advance-game" result)
      (should-not-contain "advance-until-next-round" result)
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

  (it "generates message-contains with config-key then using message-matches?"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :army-found-city} [])]
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
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
      (should-contain "message-matches?" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-contains with :at-next-step using advance-game"
    (let [result (gen/generate-then {:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true} [])]
      (should-contain "game-loop/advance-game" result)
      (should-not-contain "advance-until-next-round" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
      (should-contain ":cant-move-into-city" result)))

  (it "generates message-for-unit then with advance loop and message-matches?"
    (let [result (gen/generate-then {:type :message-for-unit :area :attention :unit "F" :config-key :fighter-bingo} [])]
      (should-contain "loop [n 100]" result)
      (should-contain "get-test-unit" result)
      (should-contain ":awake" result)
      (should-contain "should-not-be-nil" result)
      (should-contain "message-matches?" result)
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
      (should-contain ":fighter-count" result)))

  (it "generates player-map-cell-not-nil then"
    (let [result (gen/generate-then {:type :player-map-cell-not-nil :coords [1 2]} [])]
      (should-contain "should-not-be-nil" result)
      (should-contain "atoms/player-map" result)
      (should-contain "[1 2]" result)))

  (it "generates player-map-cell-nil then"
    (let [result (gen/generate-then {:type :player-map-cell-nil :coords [1 2]} [])]
      (should-contain "should-be-nil" result)
      (should-contain "atoms/player-map" result)
      (should-contain "[1 2]" result)))

  (it "generates player-map-visibility then"
    (let [result (gen/generate-then {:type :player-map-visibility :rows [".###." ".###." ".###."]} [])]
      (should-contain "should=" result)
      (should-contain "visibility-mask" result)
      (should-contain "build-test-map" result)
      (should-contain "\".###.\"" result)
      (should-contain "atoms/player-map" result)))

  (it "generates production-with-rounds then"
    (let [result (gen/generate-then {:type :production-with-rounds :city "O" :expected :army :remaining-rounds 5} [])]
      (should-contain "should=" result)
      (should-contain ":army" result)
      (should-contain ":remaining-rounds" result)
      (should-contain "5" result)
      (should-contain "get-test-city" result)))

  (it "generates cell-props given with coordinate value"
    (let [result (gen/generate-given {:type :cell-props :coords [0 0] :props {:marching-orders [4 0]}})]
      (should-contain "update-in" result)
      (should-contain "[4 0]" result)
      (should-contain ":marching-orders" result)))

  (it "generates cell-props given with keyword value"
    (let [result (gen/generate-given {:type :cell-props :coords [0 0] :props {:marching-orders :lookaround}})]
      (should-contain "update-in" result)
      (should-contain ":lookaround" result)
      (should-contain ":marching-orders" result)))

  (it "generates production-not then"
    (let [result (gen/generate-then {:type :production-not :city "X" :excluded :army} [])]
      (should-contain "should-not=" result)
      (should-contain ":army" result)
      (should-contain "get-test-city" result)
      (should-contain "atoms/production" result))))

;; --- Integration: generate-spec on actual EDN data ---

(describe "generate-spec integration"

  (it "generates army spec with correct ns form"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :s :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]}
                            {:line 18 :description "Army set to explore mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :l :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :explore}]}
                            {:line 29 :description "Army wakes near hostile city with reason."
                             :givens [{:type :map :target :game-map :rows ["A#+"]}
                                      {:type :unit-target :unit "A" :target "+"}]
                             :whens [{:type :start-new-round}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :awake}
                                     {:type :message-contains :area :attention :config-key :army-found-city}]}
                            {:line 41 :description "Army conquers free city."
                             :givens [{:type :map :target :game-map :rows ["A+"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :battle :key :d :outcome :win :combat-type :army}]
                             :thens [{:type :cell-prop :coords [1 0] :property :city-status :expected :player}]}
                            {:line 52 :description "Army skips round with space."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :space :input-fn :key-down}]
                             :thens [{:type :waiting-for-input :expected false}]}
                            {:line 63 :description "Army blocked by friendly city."
                             :givens [{:type :map :target :game-map :rows ["AO"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :d :input-fn :handle-key}]
                             :thens [{:type :message-contains :area :attention :config-key :cant-move-into-city :at-next-step true}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.army-spec" result)
      (should-contain "speclj.core :refer :all" result)
      (should-contain "empire.atoms :as atoms" result)
      (should-contain "empire.config :as config" result)
      (should-contain "empire.ui.input :as input" result)
      (should-contain "quil.core :as q" result)))

  (it "generates army spec with correct describe"
    (let [edn-data {:source "army.txt"
                    :tests [(stub-test 1 "test")]}
          result (gen/generate-spec edn-data)]
      (should-contain "(describe \"army.txt\"" result)))

  (it "generates army spec with all 6 tests"
    (let [edn-data {:source "army.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 7))}
          result (gen/generate-spec edn-data)]
      (should= 6 (count (re-seq #"\(it " result)))))

  (it "generates army spec with correct test descriptions"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode." :givens [] :whens [] :thens []}
                            {:line 18 :description "Army set to explore mode." :givens [] :whens [] :thens []}
                            {:line 29 :description "Army wakes near hostile city with reason." :givens [] :whens [] :thens []}
                            {:line 41 :description "Army conquers free city." :givens [] :whens [] :thens []}
                            {:line 52 :description "Army skips round with space." :givens [] :whens [] :thens []}
                            {:line 63 :description "Army blocked by friendly city." :givens [] :whens [] :thens []}]}
          result (gen/generate-spec edn-data)]
      (should-contain "army.txt:7 - Army put to sentry mode" result)
      (should-contain "army.txt:18 - Army set to explore mode" result)
      (should-contain "army.txt:29 - Army wakes near hostile city with reason" result)
      (should-contain "army.txt:41 - Army conquers free city" result)
      (should-contain "army.txt:52 - Army skips round with space" result)
      (should-contain "army.txt:63 - Army blocked by friendly city" result)))

  (it "generates army spec sentry test with correct assertions"
    (let [edn-data {:source "army.txt"
                    :tests [{:line 7 :description "Army put to sentry mode."
                             :givens [{:type :map :target :game-map :rows ["A#"]}
                                      {:type :waiting-for-input :unit "A" :set-mode true}]
                             :whens [{:type :key-press :key :s :input-fn :key-down}]
                             :thens [{:type :unit-prop :unit "A" :property :mode :expected :sentry}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "build-test-map [\"A#\"]" result)
      (should-contain "(should= :sentry (:mode (:unit (get-test-unit atoms/game-map \"A\"))))" result)))

  (it "generates backtick-commands spec with correct ns form"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests [{:line 6 :description "Spawn army."
                             :givens [{:type :map :target :game-map :rows ["##"]}]
                             :whens [{:type :backtick :key :A :mouse-cell [0 0]}]
                             :thens [{:type :unit-present :unit "A" :coords [0 0]}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "(ns acceptance.backtick-commands-spec" result)
      (should-contain "quil.core :as q" result)))

  (it "generates backtick-commands spec with all 13 tests"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests (into [{:line 6 :description "Spawn army."
                                   :givens [] :whens [{:type :backtick :key :A :mouse-cell [0 0]}] :thens []}]
                                 (mapv #(stub-test % (str "test " %)) (range 2 14)))}
          result (gen/generate-spec edn-data)]
      (should= 13 (count (re-seq #"\(it " result)))))

  (it "generates backtick-commands spec with map-screen-dimensions"
    (let [edn-data {:source "backtick-commands.txt"
                    :tests [{:line 6 :description "Spawn army."
                             :givens [{:type :map :target :game-map :rows ["##"]}]
                             :whens [{:type :backtick :key :A :mouse-cell [0 0]}]
                             :thens []}]}
          result (gen/generate-spec edn-data)]
      (should-contain "map-screen-dimensions" result)))

  (it "generates destroyer spec with all 4 tests"
    (let [edn-data {:source "destroyer.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 5))}
          result (gen/generate-spec edn-data)]
      (should= 4 (count (re-seq #"\(it " result)))))

  (it "generates destroyer spec with advance-game for ship battles"
    (let [edn-data {:source "destroyer.txt"
                    :tests [{:line 17 :description "Destroyer attacks enemy ship."
                             :givens [{:type :map :target :game-map :rows ["Ds"]}
                                      {:type :waiting-for-input :unit "D" :set-mode true}]
                             :whens [{:type :battle :key :d :outcome :win :combat-type :ship}]
                             :thens [{:type :unit-occupies-cell :unit "D" :target-unit "s"}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "advance-game" result)))

  (it "generates fighter spec with all 9 tests"
    (let [edn-data {:source "fighter.txt"
                    :tests (mapv #(stub-test % (str "test " %)) (range 1 10))}
          result (gen/generate-spec edn-data)]
      (should= 9 (count (re-seq #"\(it " result)))))

  (it "generates fighter spec with advance-until-next-round helper"
    (let [edn-data {:source "fighter.txt"
                    :tests [{:line 104 :description "Fighter speed is 8 per round."
                             :givens [{:type :map :target :game-map :rows ["F~~~~~~~~=~"]}
                                      {:type :waiting-for-input :unit "F" :set-mode true}]
                             :whens [{:type :key-press :key :D :input-fn :key-down}]
                             :thens [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]}]}
          result (gen/generate-spec edn-data)]
      (should-contain "defn- advance-until-next-round" result)))

  (it "generates advance-until-next-round with loop and timeout"
    (let [edn-data {:source "fighter.txt"
                    :tests [{:line 104 :description "Fighter speed is 8 per round."
                             :givens [{:type :map :target :game-map :rows ["F~~~~~~~~=~"]}
                                      {:type :waiting-for-input :unit "F" :set-mode true}]
                             :whens [{:type :key-press :key :D :input-fn :key-down}]
                             :thens [{:type :unit-at-next-round :unit "F" :target "=" :at-next-round true}]}]}
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
