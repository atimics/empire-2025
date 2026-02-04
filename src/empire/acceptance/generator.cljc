(ns empire.acceptance.generator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; --- Target resolution ---

(def ^:private city-chars #{"O" "X" "+"})
(def ^:private cell-label-chars #{"=" "%"})

(defn- target-pos-expr [target]
  (cond
    (contains? city-chars target)
    (str "(:pos (get-test-city atoms/game-map \"" target "\"))")

    (contains? cell-label-chars target)
    (str "(:pos (get-test-cell atoms/game-map \"" target "\"))")

    :else
    (str "(:pos (get-test-unit atoms/game-map \"" target "\"))")))

;; --- Message area → atom mapping ---

(defn- area->atom [area]
  (case area
    :attention "atoms/attention-message"
    :turn "atoms/turn-message"
    :error "atoms/error-message"))

;; --- Needs determination ---

(defn- node-types
  "Collect all :type values from a sequence of IR nodes."
  [nodes]
  (set (map :type nodes)))

(defn determine-needs
  "Scan all IR nodes across all tests. Returns a set of keywords
   indicating which requires/helpers are needed."
  [tests]
  (let [needs (atom #{})]
    (doseq [test tests]
      (let [givens (:givens test)
            whens (:whens test)
            thens (:thens test)
            all-nodes (concat givens whens thens)
            types (node-types all-nodes)]
        ;; :config — any message assertion with :config-key
        (when (some :config-key (concat thens whens))
          (swap! needs conj :config))
        ;; :game-loop
        (when (some #{:start-new-round :advance-game :advance-game-batch} types)
          (swap! needs conj :game-loop))
        (when (some #{:unit-at-next-round :unit-after-moves :unit-after-steps} types)
          (swap! needs conj :game-loop))
        ;; battle whens with :ship combat-type need advance-game
        (when (some #(= :battle (:type %)) whens)
          (swap! needs conj :game-loop))
        ;; :item-processing
        (when (some #(and (= :waiting-for-input (:type %))) givens)
          (swap! needs conj :item-processing))
        (when (some #{:process-player-items} types)
          (swap! needs conj :item-processing))
        ;; Collect targets that actually generate target-pos-expr calls
        ;; thens: :target from unit-at-next-round, unit-after-moves, unit-after-steps, unit-eventually-at, unit-present
        ;; givens: :player-items and :unit-target use target-pos-expr
        (let [then-targets (keep :target thens)
              given-pi-targets (mapcat :items (filter #(= :player-items (:type %)) givens))
              given-ut-targets (keep :target (filter #(= :unit-target (:type %)) givens))
              all-targets (concat then-targets given-pi-targets given-ut-targets)]
          ;; :get-test-cell
          (when (some #(or (= "=" %) (= "%" %)) all-targets)
            (swap! needs conj :get-test-cell))
          ;; :get-test-city (from targets)
          (when (some #(contains? city-chars %) all-targets)
            (swap! needs conj :get-test-city)))
        ;; :get-test-city (from target-unit in thens)
        (when (some #(contains? city-chars %) (keep :target-unit thens))
          (swap! needs conj :get-test-city))
        (when (some #(and (= :container-prop (:type %)) (= :city (:lookup %))) thens)
          (swap! needs conj :get-test-city))
        (when (some #(and (= :container-state (:type %))
                          (contains? city-chars (:target %))) givens)
          (swap! needs conj :get-test-city))
        ;; :get-test-city from waiting-for-input with city unit
        (when (some #(and (= :waiting-for-input (:type %))
                          (contains? city-chars (:unit %))) givens)
          (swap! needs conj :get-test-city))
        ;; :make-initial-test-map
        (when (some #(= :waiting-for-input (:type %)) givens)
          (swap! needs conj :make-initial-test-map))
        ;; :quil
        (when (some #(and (= :key-press (:type %)) (= :key-down (:input-fn %))) whens)
          (swap! needs conj :quil))
        (when (some #(= :backtick (:type %)) whens)
          (swap! needs conj :quil))
        ;; :advance-helper — unit-at-next-round, unit-eventually-at, unit-after-steps, or :at-next-round flag
        (when (or (some #{:unit-at-next-round :unit-eventually-at :unit-after-steps} types)
                  (some :at-next-round thens))
          (swap! needs conj :advance-helper))
        (when (some :at-next-round thens)
          (swap! needs conj :game-loop))))
    @needs))

;; --- NS form generation ---

(defn generate-ns-form
  "Generate the ns declaration string."
  [source-name needs]
  (let [base-name (str/replace source-name #"\.txt$" "")
        ns-name (str "acceptance." base-name "-spec")
        ;; Build test-utils refers
        refers (atom ["build-test-map" "set-test-unit" "get-test-unit"])
        requires (atom [])]
    (when (contains? needs :get-test-cell)
      (swap! refers conj "get-test-cell"))
    (when (contains? needs :get-test-city)
      (swap! refers conj "get-test-city"))
    (swap! refers conj "reset-all-atoms!")
    (when (contains? needs :make-initial-test-map)
      (swap! refers conj "make-initial-test-map"))

    ;; Always need atoms
    (swap! requires conj "[empire.atoms :as atoms]")
    (when (contains? needs :config)
      (swap! requires conj "[empire.config :as config]"))
    (when (contains? needs :game-loop)
      (swap! requires conj "[empire.game-loop :as game-loop]"))
    (when (contains? needs :item-processing)
      (swap! requires conj "[empire.game-loop.item-processing :as item-processing]"))
    ;; Always need input (key-down/handle-key)
    (swap! requires conj "[empire.ui.input :as input]")
    (when (contains? needs :quil)
      (swap! requires conj "[quil.core :as q]"))

    (str "(ns " ns-name "\n"
         "  (:require [speclj.core :refer :all]\n"
         "            [empire.test-utils :refer [" (str/join " " @refers) "]]\n"
         (str/join "\n" (map #(str "            " %) @requires))
         "))")))

;; --- Helper functions ---

(defn generate-helper-fns
  "Generate helper function definitions if needed."
  [needs]
  (if (contains? needs :advance-helper)
    (str "\n\n(defn- advance-until-next-round []\n"
         "  (let [start-round @atoms/round-number]\n"
         "    (while (= start-round @atoms/round-number)\n"
         "      (game-loop/advance-game)))\n"
         "  (game-loop/advance-game))")
    ""))

;; --- GIVEN generation ---

(defn- generate-map-given [{:keys [rows]}]
  (let [row-strs (str/join " " (map #(str "\"" % "\"") rows))]
    (str "    (reset! atoms/game-map (build-test-map [" row-strs "]))")))

(defn- generate-unit-props-given [{:keys [unit props]}]
  (let [kvs (mapv (fn [[k v]]
                    (str ":" (name k) " " (pr-str v)))
                  props)]
    (str "    (set-test-unit atoms/game-map \"" unit "\" " (str/join " " kvs) ")")))

(defn- generate-waiting-for-input-given [{:keys [unit set-mode]}]
  (let [lines (atom [])
        is-city (contains? city-chars unit)
        pos-lookup (if is-city
                     (str "(:pos (get-test-city atoms/game-map \"" unit "\"))")
                     (str "(:pos (get-test-unit atoms/game-map \"" unit "\"))"))]
    (when set-mode
      (if is-city
        nil ;; cities don't need mode set — they have awake-fighters
        (swap! lines conj (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :awake)"))))
    (swap! lines conj
           (str "    (let [cols (count @atoms/game-map)\n"
                "          rows (count (first @atoms/game-map))\n"
                "          pos " pos-lookup "]\n"
                "      (reset! atoms/player-map (make-initial-test-map rows cols nil))\n"
                "      (reset! atoms/player-items [pos])\n"
                "      (item-processing/process-player-items-batch))"))
    (str/join "\n" @lines)))

(defn- generate-container-state-given [{:keys [target props]}]
  (if (contains? city-chars target)
    ;; City container
    (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
      (str "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))]\n"
           "      (swap! atoms/game-map update-in " (str/lower-case target) "-pos merge {" prop-map "}))"))
    ;; Unit container
    (let [kvs (mapv (fn [[k v]] (str ":" (name k) " " (pr-str v))) props)]
      (str "    (set-test-unit atoms/game-map \"" target "\" " (str/join " " kvs) ")"))))

(defn- generate-production-given [{:keys [city item remaining-rounds]}]
  (let [pos-expr (target-pos-expr city)]
    (if remaining-rounds
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) " :remaining-rounds " remaining-rounds "}))")
      (str "    (let [" (str/lower-case city) "-pos " pos-expr "]\n"
           "      (swap! atoms/production assoc " (str/lower-case city) "-pos {:item :" (name item) "}))"))))

(defn- generate-unit-target-given [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (set-test-unit atoms/game-map \"" unit "\" :mode :moving :target " target-expr ")")))

(defn- generate-round-given [{:keys [value]}]
  (str "    (reset! atoms/round-number " value ")"))

(defn- generate-destination-given [{:keys [coords]}]
  (str "    (reset! atoms/destination " (pr-str coords) ")"))

(defn- generate-cell-props-given [{:keys [coords props]}]
  (let [prop-map (str/join " " (mapcat (fn [[k v]] [(str ":" (name k)) (pr-str v)]) props))]
    (str "    (swap! atoms/game-map update-in " (pr-str coords) " merge {" prop-map "})")))

(defn- generate-player-items-given [{:keys [items]}]
  (let [exprs (mapv target-pos-expr items)]
    (str "    (reset! atoms/player-items [" (str/join " " exprs) "])")))

(defn- generate-waiting-for-input-state-given [_]
  "    (reset! atoms/waiting-for-input true)")

(defn- generate-no-production-given [_]
  "    (reset! atoms/production {})")

(defn generate-given
  "Generate code string for a single GIVEN IR node."
  [given]
  (case (:type given)
    :map (generate-map-given given)
    :unit-props (generate-unit-props-given given)
    :waiting-for-input (generate-waiting-for-input-given given)
    :container-state (generate-container-state-given given)
    :production (generate-production-given given)
    :unit-target (generate-unit-target-given given)
    :round (generate-round-given given)
    :destination (generate-destination-given given)
    :cell-props (generate-cell-props-given given)
    :player-items (generate-player-items-given given)
    :waiting-for-input-state (generate-waiting-for-input-state-given given)
    :no-production (generate-no-production-given given)
    :unrecognized (str "    (pending \"Unrecognized: " (:text given) "\")")
    (str "    ;; Unknown given type: " (:type given))))

;; --- WHEN generation ---

(defn- generate-key-press-when [{:keys [key input-fn]}]
  (if (= input-fn :key-down)
    (str "    (with-redefs [q/mouse-x (constantly 0)\n"
         "                  q/mouse-y (constantly 0)]\n"
         "      (reset! atoms/last-key nil)\n"
         "      (input/key-down :" (name key) "))")
    (str "    (input/handle-key :" (name key) ")")))

(defn- generate-battle-when [{:keys [key outcome combat-type]}]
  (let [rand-val (if (= outcome :win) "0.0" "1.0")]
    (if (= combat-type :ship)
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) ")\n"
           "      (game-loop/advance-game))")
      (str "    (with-redefs [rand (constantly " rand-val ")]\n"
           "      (input/handle-key :" (name key) "))"))))

(defn- generate-backtick-when [{:keys [key mouse-cell]}]
  (let [[x y] mouse-cell]
    (str "    (reset! atoms/map-screen-dimensions [22 16])\n"
         "    (with-redefs [q/mouse-x (constantly " x ")\n"
         "                  q/mouse-y (constantly " y ")]\n"
         "      (input/key-down (keyword \"`\"))\n"
         "      (input/key-down :" (name key) "))")))

(defn- generate-start-new-round-when [_]
  (str "    (game-loop/start-new-round)\n"
       "    (game-loop/advance-game)"))

(defn- generate-advance-game-when [_]
  "    (game-loop/advance-game)")

(defn- generate-process-player-items-when [_]
  "    (item-processing/process-player-items-batch)")

(defn generate-when
  "Generate code string for a single WHEN IR node."
  [when-ir]
  (case (:type when-ir)
    :key-press (generate-key-press-when when-ir)
    :battle (generate-battle-when when-ir)
    :backtick (generate-backtick-when when-ir)
    :start-new-round (generate-start-new-round-when when-ir)
    :advance-game (generate-advance-game-when when-ir)
    :advance-game-batch (generate-advance-game-when when-ir)
    :process-player-items (generate-process-player-items-when when-ir)
    :unrecognized (str "    (pending \"Unrecognized: " (:text when-ir) "\")")
    (str "    ;; Unknown when type: " (:type when-ir))))

;; --- THEN generation ---

(defn- generate-unit-prop-then [{:keys [unit property expected]}]
  (str "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))"))

(defn- generate-unit-absent-then [{:keys [unit]}]
  (str "    (should-be-nil (get-test-unit atoms/game-map \"" unit "\"))"))

(defn- generate-unit-present-then [{:keys [unit coords target]}]
  (if target
    ;; Unit present at named target
    (let [target-expr (target-pos-expr target)]
      (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
           "      (should-not-be-nil pos)\n"
           "      (should= " target-expr " pos))"))
    ;; Unit present at explicit coords
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str coords) " pos))")))

(defn- generate-unit-at-then [{:keys [unit coords]}]
  (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
       "      (should= " (pr-str coords) " pos))"))

(defn- generate-unit-at-next-round-then [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (advance-until-next-round)\n"
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-moves-then [{:keys [unit moves target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (dotimes [_ " moves "] (game-loop/advance-game))\n"
         "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")\n"
         "          target-pos " target-expr "]\n"
         "      (should= target-pos pos))")))

(defn- generate-unit-after-steps-then [{:keys [unit steps target coords]}]
  (let [pos-expr (if target
                   (target-pos-expr target)
                   (pr-str coords))]
    (str "    (advance-until-next-round)\n"
         "    (let [target-pos " pos-expr "\n"
         "          f-result (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should-not-be-nil f-result)\n"
         "      (should= target-pos (:pos f-result)))")))

(defn- generate-unit-eventually-at-then [{:keys [unit target]}]
  (let [target-expr (target-pos-expr target)]
    (str "    (let [target-pos " target-expr "]\n"
         "      (loop [n 0]\n"
         "        (when (< n 20)\n"
         "          (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "            (when (not= target-pos pos)\n"
         "              (game-loop/advance-game)\n"
         "              (recur (inc n))))))\n"
         "      (should= target-pos (:pos (get-test-unit atoms/game-map \"" unit "\")))")))

(defn- find-unit-initial-pos
  "Given a map rows and a unit spec, find the [col row] position."
  [rows unit-spec]
  (let [ch (first unit-spec)]
    (first (for [r (range (count rows))
                 c (range (count (nth rows r)))
                 :when (= ch (nth (nth rows r) c))]
             [c r]))))

(defn- generate-unit-occupies-cell-then
  "Unit occupies the cell where target-unit was originally placed."
  [{:keys [unit target-unit]} givens]
  ;; Find the original position of target-unit from the map rows
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        target-pos (find-unit-initial-pos rows target-unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str target-pos) " pos))")))

(defn- generate-unit-unmoved-then
  "Assert unit hasn't moved from its original position."
  [{:keys [unit]} givens]
  (let [map-given (first (filter #(= :map (:type %)) givens))
        rows (:rows map-given)
        orig-pos (find-unit-initial-pos rows unit)]
    (str "    (let [{:keys [pos]} (get-test-unit atoms/game-map \"" unit "\")]\n"
         "      (should= " (pr-str orig-pos) " pos))")))

(defn- generate-unit-waiting-for-input-then [{:keys [unit]}]
  (str "    (should= :awake (:mode (:unit (get-test-unit atoms/game-map \"" unit "\"))))\n"
       "    (should @atoms/waiting-for-input)"))

(defn- generate-message-contains-then [{:keys [area config-key text at-next-round]}]
  (let [atom-str (area->atom area)
        advance (if at-next-round "    (advance-until-next-round)\n" "")]
    (if config-key
      (str advance "    (should-contain (:" (name config-key) " config/messages) @" atom-str ")")
      (str advance "    (should-contain \"" text "\" @" atom-str ")"))))

(defn- generate-message-is-then [{:keys [area config-key format]}]
  (let [atom-str (area->atom area)]
    (if config-key
      (str "    (should= (:" (name config-key) " config/messages) @" atom-str ")")
      ;; format case
      (let [{:keys [key args]} format
            args-str (str/join " " (map pr-str args))]
        (str "    (should= (format (:" (name key) " config/messages) " args-str ") @" atom-str ")")))))

(defn- generate-no-message-then [{:keys [area]}]
  (let [atom-str (area->atom area)]
    (str "    (should= \"\" @" atom-str ")")))

(defn- generate-cell-prop-then [{:keys [coords property expected]}]
  (str "    (should= " (pr-str expected) " (:" (name property) " (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-cell-type-then [{:keys [coords expected]}]
  (str "    (should= " (pr-str expected) " (:type (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-waiting-for-input-then [{:keys [expected]}]
  (if expected
    "    (should @atoms/waiting-for-input)"
    "    (should-not @atoms/waiting-for-input)"))

(defn- generate-container-prop-then [{:keys [target property expected lookup]}]
  (if (= lookup :city)
    (str "    (let [" (str/lower-case target) "-pos (:pos (get-test-city atoms/game-map \"" target "\"))\n"
         "          cell (get-in @atoms/game-map " (str/lower-case target) "-pos)]\n"
         "      (should= " (pr-str expected) " (:" (name property) " cell)))")
    ;; :unit lookup
    (str "    (should= " (pr-str expected) " (:" (name property) " (:unit (get-test-unit atoms/game-map \"" target "\"))))")))

(defn- generate-round-then [{:keys [expected]}]
  (str "    (should= " expected " @atoms/round-number)"))

(defn- generate-destination-then [{:keys [expected]}]
  (str "    (should= " (pr-str expected) " @atoms/destination)"))

(defn- generate-production-then [{:keys [city expected]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (should= " (pr-str expected) " (:item (get @atoms/production " pos-expr ")))")))

(defn- generate-no-production-then [{:keys [city]}]
  (let [pos-expr (target-pos-expr city)]
    (str "    (should-be-nil (get @atoms/production " pos-expr "))")))

(defn- generate-game-paused-then [_]
  "    (should @atoms/paused)")

(defn- generate-no-unit-at-then [{:keys [coords]}]
  (str "    (should-be-nil (:contents (get-in @atoms/game-map " (pr-str coords) ")))"))

(defn- generate-unit-prop-absent-then [{:keys [unit property]}]
  (str "    (should-be-nil (:" (name property) " (:unit (get-test-unit atoms/game-map \"" unit "\"))))"))

(defn generate-then
  "Generate code string for a single THEN IR node."
  [then-ir givens]
  (case (:type then-ir)
    :unit-prop (generate-unit-prop-then then-ir)
    :unit-absent (generate-unit-absent-then then-ir)
    :unit-present (generate-unit-present-then then-ir)
    :unit-at (generate-unit-at-then then-ir)
    :unit-at-next-round (generate-unit-at-next-round-then then-ir)
    :unit-after-moves (generate-unit-after-moves-then then-ir)
    :unit-after-steps (generate-unit-after-steps-then then-ir)
    :unit-eventually-at (generate-unit-eventually-at-then then-ir)
    :unit-occupies-cell (generate-unit-occupies-cell-then then-ir givens)
    :unit-unmoved (generate-unit-unmoved-then then-ir givens)
    :unit-waiting-for-input (generate-unit-waiting-for-input-then then-ir)
    :message-contains (generate-message-contains-then then-ir)
    :message-is (generate-message-is-then then-ir)
    :no-message (generate-no-message-then then-ir)
    :cell-prop (generate-cell-prop-then then-ir)
    :cell-type (generate-cell-type-then then-ir)
    :waiting-for-input (generate-waiting-for-input-then then-ir)
    :container-prop (generate-container-prop-then then-ir)
    :round (generate-round-then then-ir)
    :destination (generate-destination-then then-ir)
    :production (generate-production-then then-ir)
    :no-production (generate-no-production-then then-ir)
    :game-paused (generate-game-paused-then then-ir)
    :no-unit-at (generate-no-unit-at-then then-ir)
    :unit-prop-absent (generate-unit-prop-absent-then then-ir)
    :unrecognized (str "    (pending \"Unrecognized: " (:text then-ir) "\")")
    (str "    ;; Unknown then type: " (:type then-ir))))

;; --- Test generation ---

(defn generate-test
  "Generate a single (it ...) block from test IR."
  [test-ir source-name]
  (let [{:keys [line description givens whens thens]} test-ir
        clean-desc (if (str/ends-with? description ".")
                     (subs description 0 (dec (count description)))
                     description)
        it-name (str source-name ":" line " - " clean-desc)
        given-code (str/join "\n" (map generate-given givens))
        when-code (str/join "\n" (map generate-when whens))
        then-code (str/join "\n" (map #(generate-then % givens) thens))
        body-parts (remove str/blank?
                           [(str "    (reset-all-atoms!)")
                            given-code
                            when-code
                            then-code])]
    (str "  (it \"" it-name "\"\n"
         (str/join "\n" body-parts) ")")))

;; --- Top-level generation ---

(defn generate-spec
  "Generate a complete Speclj spec file string from parsed EDN data."
  [edn-data]
  (let [{:keys [source tests]} edn-data
        needs (determine-needs tests)
        ns-form (generate-ns-form source needs)
        helpers (generate-helper-fns needs)
        describe-name source
        test-blocks (map #(generate-test % source) tests)
        test-str (str/join "\n\n" test-blocks)]
    (str ns-form
         helpers
         "\n\n(describe \"" describe-name "\"\n\n"
         test-str ")\n")))

;; --- CLI entry point ---

(defn -main [& args]
  (let [edn-dir (or (first args) "acceptanceTests/edn")
        out-dir (or (second args) "generated-acceptance-specs/acceptance")
        edn-files (->> (io/file edn-dir)
                       .listFiles
                       (filter #(str/ends-with? (.getName %) ".edn"))
                       (sort-by #(.getName %)))]
    (doseq [f edn-files]
      (let [edn-path (.getPath f)
            data (edn/read-string (slurp edn-path))
            base-name (-> (.getName f)
                          (str/replace #"\.edn$" "")
                          (str/replace #"-" "_"))
            out-path (str out-dir "/" base-name "_spec.clj")
            spec-str (generate-spec data)]
        (println (str "Generating " out-path " from " edn-path))
        (io/make-parents (io/file out-path))
        (spit out-path spec-str)
        (println (str "  " (count (:tests data)) " tests generated"))))))
