(ns empire.acceptance.parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [empire.config :as config]))

;; --- Helpers ---

(defn- strip-trailing-period [s]
  (if (str/ends-with? s ".")
    (subs s 0 (dec (count s)))
    s))

(defn- strip-keyword-prefix [line]
  (-> line
      (str/replace #"^(?:GIVEN|WHEN|THEN)\s+" "")
      str/trim))

(defn- blank-or-comment? [line]
  (or (str/blank? line)
      (str/starts-with? (str/trim line) ";")))

(defn- separator-line? [line]
  (re-matches #"\s*;=+\s*" line))

(defn- map-row? [line]
  (let [trimmed (str/trim line)]
    (and (not (str/blank? trimmed))
         (not (str/starts-with? trimmed ";"))
         (not (re-matches #"(?i)^(GIVEN|WHEN|THEN)\b.*" trimmed))
         (not (re-matches #"^[A-Za-z].*\s+(is|has|are|with)\b.*" trimmed))
         (re-matches #"^[~#.=% +XOAFTDCSBPVatdfcsbpv\-]+$" trimmed))))

(def direction-keys #{"q" "w" "e" "a" "d" "z" "x" "c"})

(defn- lowercase-direction? [k]
  (contains? direction-keys (name k)))

(defn- uppercase-direction? [k]
  (let [n (name k)]
    (and (= 1 (count n))
         (contains? direction-keys (str/lower-case n))
         (Character/isUpperCase ^char (first n)))))

(defn- parse-coords [s]
  (when-let [[_ x y] (re-find #"\[(\d+)\s+(\d+)\]" s)]
    [(Integer/parseInt x) (Integer/parseInt y)]))

(defn- parse-number [s]
  (try (Integer/parseInt s) (catch Exception _ nil)))

(def unit-name->char
  {"army" "A" "fighter" "F" "transport" "T" "destroyer" "D"
   "carrier" "C" "submarine" "S" "battleship" "B" "patrol-boat" "P"
   "satellite" "V"})

(defn- normalize-unit-ref [s]
  (get unit-name->char (str/lower-case s) s))

(def player-unit-chars #{"A" "F" "T" "D" "C" "S" "B" "P" "V"})
(def computer-unit-chars #{"a" "f" "t" "d" "c" "s" "b" "p" "v"})
(def city-chars #{"O" "X" "+"})
(def ship-unit-chars #{"T" "D" "C" "S" "B" "P" "t" "d" "c" "s" "b" "p"})

(defn- unit-char? [s]
  (or (contains? player-unit-chars s)
      (contains? computer-unit-chars s)
      (re-matches #"[A-Za-z]\d+" s)))

(defn- city-or-unit-char? [s]
  (or (unit-char? s) (contains? city-chars s)))

;; --- GIVEN parsing ---

(defn- parse-unit-props-line [line]
  (let [clean (strip-trailing-period (str/trim line))
        clean (strip-keyword-prefix clean)]
    (when-let [[_ unit rest-str] (re-matches #"(\w+)\s+(.*)" clean)]
      (when (city-or-unit-char? unit)
        (let [props (atom {})
              text rest-str]
          ;; Parse "is awake", "is explore", "is sentry", "is in mode X"
          (when-let [[_ mode] (re-find #"(?:is|has mode|mode)\s+(\w+)" text)]
            (swap! props assoc :mode (keyword mode)))
          ;; Parse "has fuel N", "fuel N", "with fuel N"
          (when-let [[_ n] (re-find #"(?:has\s+)?fuel\s+(\d+)" text)]
            (swap! props assoc :fuel (Integer/parseInt n)))
          ;; Parse "with fuel N"
          (when-let [[_ n] (re-find #"with\s+fuel\s+(\d+)" text)]
            (swap! props assoc :fuel (Integer/parseInt n)))
          ;; Parse "army-count N"
          (when-let [[_ n] (re-find #"army-count\s+(\d+)" text)]
            (swap! props assoc :army-count (Integer/parseInt n)))
          ;; Parse "hits N"
          (when-let [[_ n] (re-find #"hits\s+(\d+)" text)]
            (swap! props assoc :hits (Integer/parseInt n)))
          (when (seq @props)
            {:type :unit-props :unit unit :props @props}))))))

(defn- parse-container-state-line [line]
  (let [clean (strip-trailing-period (str/trim line))
        clean (strip-keyword-prefix clean)]
    (cond
      ;; "O has one fighter in its airport"
      (re-find #"(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+one\s+fighter" clean)]
        {:type :container-state :target target :props {:fighter-count 1 :awake-fighters 1}})

      ;; "C has no fighters"
      (re-find #"(\w+)\s+has\s+no\s+fighters" clean)
      (let [[_ target] (re-find #"(\w+)\s+has\s+no\s+fighters" clean)]
        {:type :container-state :target target :props {:fighter-count 0}})

      :else nil)))

(defn- parse-given-line [line context]
  (let [clean (str/trim line)
        stripped (strip-trailing-period clean)
        no-given (strip-keyword-prefix stripped)]
    (cond
      ;; Map directives
      (re-find #"(?i)^(?:GIVEN\s+)?(?:game\s+)?map\s*$" stripped)
      {:directive :map-start :target :game-map}

      (re-find #"(?i)^(?:GIVEN\s+)?game\s+map" stripped)
      {:directive :map-start :target :game-map}

      (re-find #"(?i)^(?:GIVEN\s+)?player\s+map" stripped)
      {:directive :map-start :target :player-map}

      (re-find #"(?i)^(?:GIVEN\s+)?computer\s+map" stripped)
      {:directive :map-start :target :computer-map}

      ;; Waiting for input
      (re-find #"(\w+)\s+is\s+waiting\s+for\s+input" no-given)
      (let [[_ unit] (re-find #"(\w+)\s+is\s+waiting\s+for\s+input" no-given)
            mode-already-set (contains? (:units-with-mode context) unit)]
        {:directive :waiting-for-input
         :ir {:type :waiting-for-input :unit unit :set-mode (not mode-already-set)}})

      ;; Production with remaining rounds
      (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)\s+with\s+(\d+)\s+rounds?\s+remaining" no-given)
      (let [[_ city item n] (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)\s+with\s+(\d+)\s+rounds?\s+remaining" no-given)]
        {:directive :production
         :ir {:type :production :city city :item (keyword item) :remaining-rounds (Integer/parseInt n)}})

      ;; Production
      (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)" no-given)
      (let [[_ city item] (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)" no-given)]
        {:directive :production
         :ir {:type :production :city city :item (keyword item)}})

      ;; No production
      (re-find #"no\s+production" no-given)
      {:directive :no-production
       :ir {:type :no-production}}

      ;; Round
      (re-find #"round\s+(\d+)" no-given)
      (let [[_ n] (re-find #"round\s+(\d+)" no-given)]
        {:directive :round
         :ir {:type :round :value (Integer/parseInt n)}})

      ;; Destination
      (re-find #"destination\s+\[(\d+)\s+(\d+)\]" no-given)
      (let [[_ x y] (re-find #"destination\s+\[(\d+)\s+(\d+)\]" no-given)]
        {:directive :destination
         :ir {:type :destination :coords [(Integer/parseInt x) (Integer/parseInt y)]}})

      ;; Cell props
      (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(.*)" no-given)
      (let [[_ x y rest-str] (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(.*)" no-given)
            pairs (str/split rest-str #"\s+and\s+")
            props (into {}
                        (for [pair pairs
                              :let [[_ k v] (re-find #"(\S+)\s+(\S+)" (str/trim pair))]
                              :when k]
                          [(keyword k) (parse-number v)]))]
        {:directive :cell-props
         :ir {:type :cell-props :coords [(Integer/parseInt x) (Integer/parseInt y)] :props props}})

      ;; Player-items (multiple)
      (re-find #"player-items\s+are\s+(.*)" no-given)
      (let [[_ items-str] (re-find #"player-items\s+are\s+(.*)" no-given)
            items (mapv str/trim (str/split items-str #",\s*"))]
        {:directive :player-items
         :ir {:type :player-items :items items}})

      ;; Player-items (single)
      (re-find #"player-items\s+(\w+)" no-given)
      (let [[_ item] (re-find #"player-items\s+(\w+)" no-given)]
        {:directive :player-items
         :ir {:type :player-items :items [item]}})

      ;; Waiting-for-input (bare)
      (re-find #"^waiting-for-input$" no-given)
      {:directive :waiting-for-input-bare
       :ir {:type :waiting-for-input-state}}

      ;; Unit target - "A's target is +"
      (re-find #"(\w+)'s\s+target\s+is\s+(\S+)" no-given)
      (let [[_ unit target] (re-find #"(\w+)'s\s+target\s+is\s+(\S+)" no-given)]
        {:directive :unit-target
         :ir {:type :unit-target :unit unit :target target}})

      ;; Container state
      (parse-container-state-line line)
      (let [ir (parse-container-state-line line)]
        {:directive :container-state :ir ir})

      ;; Unit properties
      (parse-unit-props-line line)
      (let [ir (parse-unit-props-line line)]
        {:directive :unit-props :ir ir})

      :else
      {:directive :unrecognized
       :ir {:type :unrecognized :text clean}})))

(defn parse-given
  "Parse GIVEN lines into IR. Returns {:givens [...] :context updated-context}"
  [lines context]
  (let [context (atom (merge {:units-with-mode #{}} context))
        givens (atom [])
        i (atom 0)]
    (while (< @i (count lines))
      (let [line (nth lines @i)
            trimmed (str/trim line)]
        (if (blank-or-comment? line)
          (swap! i inc)
          (let [parsed (parse-given-line trimmed @context)]
            (case (:directive parsed)
              :map-start
              (let [target (:target parsed)
                    _ (swap! i inc)
                    rows (atom [])]
                (while (and (< @i (count lines))
                            (map-row? (nth lines @i)))
                  (swap! rows conj (str/trim (nth lines @i)))
                  (swap! i inc))
                (swap! givens conj {:type :map :target target :rows @rows}))

              :waiting-for-input
              (do
                (swap! givens conj (:ir parsed))
                (swap! i inc))

              :unit-props
              (let [ir (:ir parsed)]
                (when (:mode (:props ir))
                  (swap! context update :units-with-mode conj (:unit ir)))
                (swap! givens conj ir)
                (swap! i inc))

              :container-state
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              (:production :no-production :round :destination :cell-props
               :player-items :waiting-for-input-bare :unit-target)
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              :unrecognized
              (do (swap! givens conj (:ir parsed))
                  (swap! i inc))

              ;; default
              (swap! i inc))))))
    {:givens @givens :context @context}))

;; --- WHEN parsing ---

(defn- determine-key-type [key-str context]
  (let [k (keyword key-str)]
    (cond
      (= key-str "space") {:key :space :input-fn :key-down}
      (uppercase-direction? k) {:key k :input-fn :key-down}
      (and (lowercase-direction? k) (:has-waiting-for-input context)) {:key k :input-fn :handle-key}
      :else {:key k :input-fn :key-down})))

(defn- determine-combat-type [context]
  (let [unit-types (or (:unit-types context) #{})]
    (if (some ship-unit-chars unit-types)
      :ship
      :army)))

(defn parse-when
  "Parse WHEN lines into IR. Returns {:whens [...]}"
  [lines context]
  (let [whens (atom [])]
    (doseq [line lines]
      (let [clean (str/trim line)
            stripped (strip-trailing-period clean)
            no-when (strip-keyword-prefix stripped)]
        (when-not (blank-or-comment? clean)
          (cond
            ;; Backtick command
            (re-find #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*backtick\s+then\s+(\w)" no-when)
            (let [[_ x y k] (re-find #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*backtick\s+then\s+(\w)" no-when)]
              (swap! whens conj {:type :backtick
                                 :key (keyword k)
                                 :mouse-cell [(Integer/parseInt x) (Integer/parseInt y)]}))

            ;; Mouse at cell + key press
            (re-find #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*presses\s+(\w+)" no-when)
            (let [[_ x y k] (re-find #"mouse\s+is\s+at\s+cell\s+\[(\d+)\s+(\d+)\]\s+and.*presses\s+(\w+)" no-when)]
              (swap! whens conj {:type :mouse-at-key
                                 :coords [(Integer/parseInt x) (Integer/parseInt y)]
                                 :key (keyword k)}))

            ;; Key press with battle
            (re-find #"player\s+presses\s+(\w+)\s+and\s+(wins|loses)\s+the\s+battle" no-when)
            (let [[_ k outcome] (re-find #"player\s+presses\s+(\w+)\s+and\s+(wins|loses)\s+the\s+battle" no-when)
                  outcome-kw (case outcome "wins" :win "loses" :lose (keyword outcome))]
              (swap! whens conj {:type :battle
                                 :key (keyword k)
                                 :outcome outcome-kw
                                 :combat-type (determine-combat-type context)}))

            ;; Key press + advance until unit waiting
            (re-find #"player\s+presses\s+(\w+)\s+and\s+(?:the\s+game\s+advances\s+until\s+)?(\w+)\s+is\s+waiting\s+for\s+input" no-when)
            (let [[_ k unit] (re-find #"player\s+presses\s+(\w+)\s+and\s+(?:the\s+game\s+advances\s+until\s+)?(\w+)\s+is\s+waiting\s+for\s+input" no-when)
                  key-info (determine-key-type k context)]
              (swap! whens conj (merge {:type :key-press} key-info))
              (swap! whens conj {:type :advance-until-waiting :unit unit}))

            ;; Key press (simple)
            (re-find #"player\s+presses\s+(\w+)" no-when)
            (let [[_ k] (re-find #"player\s+presses\s+(\w+)" no-when)
                  key-info (determine-key-type k context)]
              (when (re-find #"presses\s+\w+\s+and\s+" no-when)
                (println (str "WARNING: unconsumed trailing text in WHEN: " clean)))
              (swap! whens conj (merge {:type :key-press} key-info)))

            ;; Player types multiple keys
            (re-find #"player\s+types\s+(.*)" no-when)
            (let [[_ keys-str] (re-find #"player\s+types\s+(.*)" no-when)
                  keys (str/split (str/trim keys-str) #"\s+")]
              (doseq [k keys]
                (let [key-info (determine-key-type k context)]
                  (swap! whens conj (merge {:type :key-press} key-info)))))

            ;; Mouse click
            (re-find #"player\s+clicks\s+(?:cell\s+)?\[(\d+)\s+(\d+)\]" no-when)
            (let [[_ x y] (re-find #"player\s+clicks\s+(?:cell\s+)?\[(\d+)\s+(\d+)\]" no-when)]
              (swap! whens conj {:type :mouse-click
                                 :coords [(Integer/parseInt x) (Integer/parseInt y)]}))

            ;; New round + advance until unit waiting
            (re-find #"new\s+round\s+starts\s+and\s+(\w+)\s+is\s+waiting\s+for\s+input" no-when)
            (let [[_ unit] (re-find #"new\s+round\s+starts\s+and\s+(\w+)\s+is\s+waiting\s+for\s+input" no-when)]
              (swap! whens conj {:type :start-new-round})
              (swap! whens conj {:type :advance-until-waiting :unit unit}))

            ;; New round / next round
            (or (re-find #"new\s+round\s+starts" no-when)
                (re-find #"next\s+round\s+begins" no-when))
            (do
              (when (re-find #"starts\s+and\s+" no-when)
                (println (str "WARNING: unconsumed trailing text in WHEN: " clean)))
              (swap! whens conj {:type :start-new-round}))

            ;; Game advances one batch
            (re-find #"game\s+advances\s+one\s+batch" no-when)
            (swap! whens conj {:type :advance-game-batch})

            ;; Game advances
            (re-find #"game\s+advances" no-when)
            (swap! whens conj {:type :advance-game})

            ;; Player items processed
            (re-find #"player\s+items\s+are\s+processed" no-when)
            (swap! whens conj {:type :process-player-items})

            ;; Production updates
            (re-find #"production\s+updates" no-when)
            (swap! whens conj {:type :update-production})

            ;; Standalone waiting for input
            (re-find #"(\w+)\s+is\s+waiting\s+for\s+input" no-when)
            (let [[_ unit] (re-find #"(\w+)\s+is\s+waiting\s+for\s+input" no-when)
                  mode-already-set (contains? (:units-with-mode context) unit)]
              (swap! whens conj {:type :waiting-for-input :unit unit :set-mode (not mode-already-set)}))

            :else
            (swap! whens conj {:type :unrecognized :text clean})))))
    {:whens @whens}))

;; --- THEN parsing ---

(def ^:private word->number
  {"one" 1 "two" 2 "three" 3 "four" 4 "five" 5
   "six" 6 "seven" 7 "eight" 8 "nine" 9 "ten" 10})

(defn- parse-count [s]
  (or (get word->number (str/lower-case s))
      (parse-number s)))

(defn- parse-single-then-clause [clause]
  (let [clean (str/trim clause)
        stripped (strip-trailing-period clean)
        ;; Remove leading THEN/and
        no-prefix (-> stripped
                      (str/replace #"^(?:THEN|and)\s+" "")
                      str/trim)
        ;; Strip "at the next round/step/move" prefix, tracking which keyword was used
        timing-match (re-find #"^[Aa]t\s+(?:the\s+)?next\s+(round|step|move)\s+" no-prefix)
        timing-word (when timing-match (nth timing-match 1))
        no-timing (if timing-match
                    (str/trim (str/replace-first no-prefix (first timing-match) ""))
                    no-prefix)
        timing-key (case timing-word
                     "round" :at-next-round
                     ("step" "move") :at-next-step
                     nil)
        tag-timing (fn [result]
                     (if timing-key
                       (if (map? result)
                         (assoc result timing-key true)
                         (update result 0 assoc timing-key true))
                       result))]
    (tag-timing
    (cond
      ;; After N moves unit will be at target
      (re-find #"^after\s+(\w+)\s+moves?\s+(\w+)\s+will\s+be\s+at\s+(\S+)" no-prefix)
      (let [[_ n unit target] (re-find #"^after\s+(\w+)\s+moves?\s+(\w+)\s+will\s+be\s+at\s+(\S+)" no-prefix)]
        {:type :unit-after-moves :unit unit :moves (parse-count n) :target target})

      ;; After N steps there is a unit at [x y]
      (re-find #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)
      (let [[_ n unit x y] (re-find #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)]
        {:type :unit-after-steps :unit unit :steps (parse-count n)
         :coords [(Integer/parseInt x) (Integer/parseInt y)]})

      ;; After N steps there is a unit at target
      (re-find #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)" no-prefix)
      (let [[_ n unit target] (re-find #"^after\s+(\w+)\s+steps?\s+there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)" no-prefix)]
        {:type :unit-after-steps :unit unit :steps (parse-count n) :target target})

      ;; Unit waiting for input
      (re-find #"^(\w+)\s+is\s+waiting\s+for\s+input$" no-prefix)
      (let [[_ unit] (re-find #"^(\w+)\s+is\s+waiting\s+for\s+input$" no-prefix)]
        {:type :unit-waiting-for-input :unit unit})

      ;; F wakes up and asks for input
      (re-find #"^(\w+)\s+wakes\s+up\s+and\s+asks\s+for\s+input" no-prefix)
      (let [[_ unit] (re-find #"^(\w+)\s+wakes\s+up\s+and\s+asks\s+for\s+input" no-prefix)]
        {:type :unit-waiting-for-input :unit unit})

      ;; Unit at next round (must be before unit-at)
      (re-find #"^(\w+)\s+will\s+be\s+at\s+(\S+)$" no-timing)
      (let [[_ unit target] (re-find #"^(\w+)\s+will\s+be\s+at\s+(\S+)$" no-timing)]
        (if-let [coords (parse-coords (str "[" (str/replace target #"[\[\]]" "") "]"))]
          {:type :unit-at-next-round :unit unit :coords coords}
          {:type :unit-at-next-round :unit unit :target target}))

      ;; Eventually at
      (re-find #"eventually\s+(\w+)\s+will\s+be\s+at\s+(\S+)" no-prefix)
      (let [[_ unit target] (re-find #"eventually\s+(\w+)\s+will\s+be\s+at\s+(\S+)" no-prefix)]
        {:type :unit-eventually-at :unit unit :target target})

      ;; Unit occupies cell
      (re-find #"^(\w+)\s+occupies\s+the\s+(\w+)\s+cell" no-timing)
      (let [[_ unit target] (re-find #"^(\w+)\s+occupies\s+the\s+(\w+)\s+cell" no-timing)]
        {:type :unit-occupies-cell :unit unit :target-unit target})

      ;; Unit remains unmoved
      (re-find #"^(\w+)\s+remains\s+unmoved" no-timing)
      (let [[_ unit] (re-find #"^(\w+)\s+remains\s+unmoved" no-timing)]
        {:type :unit-unmoved :unit unit})

      ;; Unit at position with mode
      (re-find #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]\s+in\s+mode\s+(\w+)" no-prefix)
      (let [[_ unit x y mode] (re-find #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]\s+in\s+mode\s+(\w+)" no-prefix)]
        [{:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]}
         {:type :unit-prop :unit unit :property :mode :expected (keyword mode)}])

      ;; Unit at position
      (re-find #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)
      (let [[_ unit x y] (re-find #"^(\w+)\s+is\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)]
        {:type :unit-at :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

      ;; Unit at named target (e.g. "F is at =")
      (re-find #"^(\w+)\s+is\s+at\s+(\S+)$" no-prefix)
      (let [[_ unit target] (re-find #"^(\w+)\s+is\s+at\s+(\S+)$" no-prefix)]
        {:type :unit-at :unit unit :target target})

      ;; Container - city airport has fighter(s)
      (re-find #"^(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport" no-timing)
      (let [[_ target] (re-find #"^(\w+)\s+has\s+one\s+fighter\s+in\s+its\s+airport" no-timing)]
        {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :city})

      ;; Container - unit has fighter(s) aboard
      (re-find #"^(\w+)\s+has\s+one\s+fighter\s+aboard" no-timing)
      (let [[_ target] (re-find #"^(\w+)\s+has\s+one\s+fighter\s+aboard" no-timing)]
        {:type :container-prop :target target :property :fighter-count :expected 1 :lookup :unit})

      ;; Container - no fighters (on city)
      (re-find #"^(\w+)\s+has\s+no\s+fighters" no-timing)
      (let [[_ target] (re-find #"^(\w+)\s+has\s+no\s+fighters" no-timing)]
        (let [lookup (if (contains? city-chars target) :city :unit)]
          {:type :container-prop :target target :property :fighter-count :expected 0 :lookup lookup}))

      ;; Unit absent - "there is no X on the map"
      (re-find #"there\s+is\s+no\s+(\w+)\s+on\s+the\s+map" no-prefix)
      (let [[_ unit] (re-find #"there\s+is\s+no\s+(\w+)\s+on\s+the\s+map" no-prefix)]
        {:type :unit-absent :unit (normalize-unit-ref unit)})

      ;; Unit absent - "there is no X" (short form)
      (re-find #"there\s+is\s+no\s+(\w+)$" no-timing)
      (let [[_ unit] (re-find #"there\s+is\s+no\s+(\w+)$" no-timing)]
        {:type :unit-absent :unit (normalize-unit-ref unit)})

      ;; Unit present - "there is an/a X at [coords]"
      (re-find #"there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]" no-timing)
      (let [[_ unit x y] (re-find #"there\s+is\s+an?\s+(\w+)\s+at\s+\[(\d+)\s+(\d+)\]" no-timing)]
        {:type :unit-present :unit unit :coords [(Integer/parseInt x) (Integer/parseInt y)]})

      ;; Unit present at target cell
      (re-find #"there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)" no-timing)
      (let [[_ unit target] (re-find #"there\s+is\s+an?\s+(\w+)\s+at\s+(\S+)" no-timing)]
        {:type :unit-present :unit unit :target target})

      ;; No message
      (re-find #"there\s+is\s+no\s+(attention|turn|error)\s+message" no-prefix)
      (let [[_ area] (re-find #"there\s+is\s+no\s+(attention|turn|error)\s+message" no-prefix)]
        {:type :no-message :area (keyword area)})

      ;; Message for unit contains config key (e.g. "attention message for F contains :fighter-bingo")
      (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+for\s+(\w+)\s+contains\s+:(\S+)" no-prefix)
      (let [[_ area unit key-str] (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+for\s+(\w+)\s+contains\s+:(\S+)" no-prefix)]
        {:type :message-for-unit :area (keyword area) :unit unit :config-key (keyword (strip-trailing-period key-str))})

      ;; Message contains literal string
      (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+\"([^\"]+)\"" no-prefix)
      (let [[_ area text] (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+\"([^\"]+)\"" no-prefix)]
        {:type :message-contains :area (keyword area) :text text})

      ;; Message contains config key
      (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+:(\S+)" no-prefix)
      (let [[_ area key-str] (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+contains\s+:(\S+)" no-prefix)]
        {:type :message-contains :area (keyword area) :config-key (keyword (strip-trailing-period key-str))})

      ;; Message is config key
      (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+:(\S+)" no-prefix)
      (let [[_ area key-str] (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+:(\S+)" no-prefix)]
        {:type :message-is :area (keyword area) :config-key (keyword (strip-trailing-period key-str))})

      ;; Message is format function
      (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+\(fmt\s+:(\S+)\s+(.*)\)" no-prefix)
      (let [[_ area key-str args-str] (re-find #"(?:the\s+)?(attention|turn|error)\s+message\s+is\s+\(fmt\s+:(\S+)\s+(.*)\)" no-prefix)
            args (mapv #(let [s (str/trim %)]
                          (or (parse-number s) s))
                       (str/split args-str #"\s+"))]
        {:type :message-is :area (keyword area) :format {:key (keyword key-str) :args args}})

      ;; Bare message contains (backward compat) - literal
      (re-find #"(?:the\s+)?message\s+contains\s+\"([^\"]+)\"" no-prefix)
      (let [[_ text] (re-find #"(?:the\s+)?message\s+contains\s+\"([^\"]+)\"" no-prefix)]
        {:type :message-contains :area :attention :text text})

      ;; Bare message contains (backward compat) - config key
      (re-find #"(?:the\s+)?message\s+contains\s+:(\S+)" no-prefix)
      (let [[_ key-str] (re-find #"(?:the\s+)?message\s+contains\s+:(\S+)" no-prefix)]
        {:type :message-contains :area :attention :config-key (keyword (strip-trailing-period key-str))})

      ;; Out-of-fuel message displayed (special case)
      (re-find #"out-of-fuel\s+message\s+is\s+displayed" no-prefix)
      {:type :message-contains :area :attention :config-key :fighter-out-of-fuel}

      ;; Cell property
      (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(\S+)\s+(\S+)" no-prefix)
      (let [[_ x y prop val] (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+has\s+(\S+)\s+(\S+)" no-prefix)]
        {:type :cell-prop :coords [(Integer/parseInt x) (Integer/parseInt y)]
         :property (keyword prop) :expected (keyword val)})

      ;; Cell type
      (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+(?:a\s+)?(\w+)" no-prefix)
      (let [[_ x y t] (re-find #"cell\s+\[(\d+)\s+(\d+)\]\s+is\s+(?:a\s+)?(\w+)" no-prefix)]
        {:type :cell-type :coords [(Integer/parseInt x) (Integer/parseInt y)]
         :expected (keyword t)})

      ;; Waiting-for-input (bare)
      (re-find #"^waiting-for-input$" no-prefix)
      {:type :waiting-for-input :expected true}

      ;; Not waiting-for-input
      (re-find #"^not\s+waiting-for-input$" no-prefix)
      {:type :waiting-for-input :expected false}

      ;; Game is paused
      (re-find #"game\s+is\s+paused" no-prefix)
      {:type :game-paused :expected true}

      ;; Round is N
      (re-find #"round\s+is\s+(\d+)" no-prefix)
      (let [[_ n] (re-find #"round\s+is\s+(\d+)" no-prefix)]
        {:type :round :expected (Integer/parseInt n)})

      ;; Destination is [x y]
      (re-find #"destination\s+is\s+\[(\d+)\s+(\d+)\]" no-prefix)
      (let [[_ x y] (re-find #"destination\s+is\s+\[(\d+)\s+(\d+)\]" no-prefix)]
        {:type :destination :expected [(Integer/parseInt x) (Integer/parseInt y)]})

      ;; Production at city is item
      (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)" no-prefix)
      (let [[_ city item] (re-find #"production\s+at\s+(\w+)\s+is\s+(\w+)" no-prefix)]
        {:type :production :city city :expected (keyword item)})

      ;; No production at city
      (re-find #"(?:there\s+is\s+)?no\s+production\s+at\s+(\w+)" no-prefix)
      (let [[_ city] (re-find #"no\s+production\s+at\s+(\w+)" no-prefix)]
        {:type :no-production :city city})

      ;; No unit at coords
      (re-find #"no\s+unit\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)
      (let [[_ x y] (re-find #"no\s+unit\s+at\s+\[(\d+)\s+(\d+)\]" no-prefix)]
        {:type :no-unit-at :coords [(Integer/parseInt x) (Integer/parseInt y)]})

      ;; Unit has property (generic)
      (re-find #"^(\w+)\s+has\s+(\w[\w-]*)\s+(\S+)$" no-prefix)
      (let [[_ unit prop val] (re-find #"^(\w+)\s+has\s+(\w[\w-]*)\s+(\S+)$" no-prefix)]
        (when (city-or-unit-char? unit)
          {:type :unit-prop :unit unit
           :property (keyword prop)
           :expected (or (parse-number val) (keyword val))}))

      ;; Unit is mode
      (re-find #"^(\w+)\s+(?:has\s+mode|is)\s+(\w+)$" no-prefix)
      (let [[_ unit val] (re-find #"^(\w+)\s+(?:has\s+mode|is)\s+(\w+)$" no-prefix)]
        (when (city-or-unit-char? unit)
          {:type :unit-prop :unit unit :property :mode :expected (keyword val)}))

      ;; Unit does not have property
      (re-find #"^(\w+)\s+does\s+not\s+have\s+(\S+)" no-prefix)
      (let [[_ unit prop] (re-find #"^(\w+)\s+does\s+not\s+have\s+(\S+)" no-prefix)]
        {:type :unit-prop-absent :unit unit :property (keyword prop)})

      :else
      {:type :unrecognized :text clean}))))

(defn- split-then-continuations [lines]
  (let [result (atom [])
        current (atom nil)]
    (doseq [line lines]
      (let [trimmed (str/trim line)]
        (cond
          (blank-or-comment? trimmed)
          nil

          (str/starts-with? (str/upper-case trimmed) "THEN ")
          (do
            (when @current (swap! result conj @current))
            (reset! current trimmed))

          (re-matches #"^and\s+.*" trimmed)
          (if @current
            (do (swap! result conj @current)
                (reset! current trimmed))
            (reset! current trimmed))

          ;; Continuation of previous line (e.g., multi-line THEN)
          @current
          (swap! current str " " trimmed)

          :else
          (reset! current trimmed))))
    (when @current (swap! result conj @current))
    @result))

(defn- split-compound-then [clause]
  (let [clean (str/trim clause)
        ;; Split on " and " but be careful not to split inside phrases like "wakes up and asks"
        ;; We split on " and " that follows a complete assertion pattern
        ;; Strategy: split on " and " then check if subparts are valid
        ;; Simple approach: check for known compound patterns
        ]
    ;; Check for "X has one fighter in its airport and there is no fighter on the map"
    (if-let [[_ part1 part2] (re-find #"(.*?\b(?:airport|aboard))\s+and\s+(there\s+is\s+.*)" clean)]
      [part1 part2]
      ;; Check for "X occupies the Y cell and there is no Y"
      (if-let [[_ part1 part2] (re-find #"(.*?cell)\s+and\s+(there\s+is\s+.*)" clean)]
        [part1 part2]
        ;; Check for "s remains unmoved" followed by "and there is no D"
        ;; Already handled by split-then-continuations
        [clean]))))

(defn parse-then
  "Parse THEN lines into IR. Returns {:thens [...]}"
  [lines context]
  (let [clauses (split-then-continuations lines)
        thens (atom [])]
    (doseq [clause clauses]
      (let [parts (split-compound-then clause)]
        (doseq [part parts]
          (let [parsed (parse-single-then-clause part)]
            (if (vector? parsed)
              (doseq [p parsed]
                (when p (swap! thens conj p)))
              (when parsed (swap! thens conj parsed)))))))
    {:thens @thens}))

;; --- Test splitting ---

(defn split-into-tests
  "Split lines (with 1-based line numbers) into test groups.
   Returns [{:line N :description \"...\" :given-lines [...] :when-lines [...] :then-lines [...]}]"
  [lines]
  (let [indexed (map-indexed (fn [i l] [(inc i) l]) lines)
        tests (atom [])
        current-test (atom nil)
        current-section (atom nil)
        in-header (atom false)
        header-desc (atom nil)]
    (doseq [[line-num line] indexed]
      (let [trimmed (str/trim line)]
        (cond
          ;; Separator line
          (separator-line? trimmed)
          (if @in-header
            ;; End of header
            (do (reset! in-header false))
            ;; Start of header — save any current test
            (do
              (when @current-test
                (swap! tests conj @current-test))
              (reset! in-header true)
              (reset! header-desc nil)
              (reset! current-test nil)
              (reset! current-section nil)))

          ;; Comment line inside header → description
          (and @in-header (str/starts-with? trimmed ";"))
          (reset! header-desc (str/trim (subs trimmed 1)))

          ;; GIVEN line
          (str/starts-with? trimmed "GIVEN")
          (do
            (when (nil? @current-test)
              (reset! current-test {:line line-num
                                    :description (or @header-desc "")
                                    :given-lines []
                                    :when-lines []
                                    :then-lines []}))
            (reset! current-section :given)
            (swap! current-test update :given-lines conj trimmed))

          ;; WHEN line
          (str/starts-with? trimmed "WHEN")
          (do
            (reset! current-section :when)
            (when @current-test
              (swap! current-test update :when-lines conj trimmed)))

          ;; THEN line or and-continuation
          (or (str/starts-with? trimmed "THEN")
              (re-matches #"^and\s+.*" trimmed))
          (do
            (reset! current-section :then)
            (when @current-test
              (swap! current-test update :then-lines conj trimmed)))

          ;; Blank or comment — ignore
          (blank-or-comment? trimmed)
          nil

          ;; Content line — add to current section
          :else
          (when @current-test
            (case @current-section
              :given (swap! current-test update :given-lines conj trimmed)
              :when (swap! current-test update :when-lines conj trimmed)
              :then (swap! current-test update :then-lines conj trimmed)
              nil)))))
    (when @current-test
      (swap! tests conj @current-test))
    @tests))

;; --- Context building ---

(defn- extract-unit-types-from-givens [givens]
  (let [types (atom #{})]
    (doseq [g givens]
      (case (:type g)
        :map (doseq [row (:rows g)]
               (doseq [ch (seq row)]
                 (let [s (str ch)]
                   (when (or (contains? player-unit-chars s)
                             (contains? computer-unit-chars s))
                     (swap! types conj s)))))
        :unit-props (swap! types conj (:unit g))
        :waiting-for-input (swap! types conj (:unit g))
        nil))
    @types))

(defn- has-waiting-for-input? [givens]
  (some #(= :waiting-for-input (:type %)) givens))

;; --- Top-level parsing ---

(defn parse-test
  "Parse a single test group into IR."
  [{:keys [line description given-lines when-lines then-lines]}]
  (let [{:keys [givens context]} (parse-given given-lines {})
        unit-types (extract-unit-types-from-givens givens)
        wfi (has-waiting-for-input? givens)
        when-ctx {:has-waiting-for-input wfi
                  :unit-types unit-types
                  :units-with-mode (or (:units-with-mode context) #{})}
        {:keys [whens]} (parse-when when-lines when-ctx)
        {:keys [thens]} (parse-then then-lines {})]
    {:line line
     :description description
     :givens givens
     :whens whens
     :thens thens}))

(defn parse-file
  "Parse a .txt acceptance test file into structured EDN IR."
  [path]
  (let [content (slurp path)
        lines (str/split-lines content)
        source (last (str/split path #"/"))
        raw-tests (split-into-tests lines)
        tests (mapv parse-test raw-tests)]
    {:source source
     :tests tests}))

;; --- Config key validation ---

(defn validate-config-keys
  "Print warnings for config keys referenced in thens that don't exist in config/messages."
  [source-name tests]
  (doseq [{:keys [line thens]} tests]
    (doseq [{:keys [config-key]} thens]
      (when (and config-key (not (contains? config/messages config-key)))
        (println (str "WARNING: " source-name ":" line " - config key :" (name config-key) " not found in config/messages"))))))

;; --- CLI entry point ---

(defn- write-edn [path data]
  (spit path (pr-str data)))

(defn -main [& args]
  (let [dir (or (first args) "acceptanceTests")
        edn-dir (str dir "/edn")
        files (->> (io/file dir)
                   .listFiles
                   (filter #(str/ends-with? (.getName %) ".txt"))
                   (sort-by #(.getName %)))]
    (io/make-parents (io/file edn-dir "dummy"))
    (doseq [f files]
      (let [txt-path (.getPath f)
            base-name (str/replace (.getName f) #"\.txt$" ".edn")
            edn-path (str edn-dir "/" base-name)]
        (println (str "Parsing " txt-path " -> " edn-path))
        (let [result (parse-file txt-path)]
          (validate-config-keys (.getName f) (:tests result))
          (write-edn edn-path result)
          (println (str "  " (count (:tests result)) " tests parsed")))))))
