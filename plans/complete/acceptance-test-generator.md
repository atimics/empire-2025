# Plan: Acceptance Test Parser → EDN Intermediate Representation

## Goal

Write a Clojure program that parses `.txt` acceptance test files into structured EDN, eliminating the need for Claude to re-read the 850-line framework doc and re-interpret natural language directives each time a spec is generated.

## Current Workflow (expensive)

1. Claude reads `.txt` file + 850-line framework doc (~1000 lines of context)
2. Claude interprets natural language directives (ambiguous, error-prone)
3. Claude generates Clojure spec code
4. Repeat for every file

## New Workflow (cheap)

1. Run `clj -M:parse-tests` (Clojure parser, runs in seconds)
2. Parser outputs `.edn` files with structured IR
3. Claude reads compact `.edn` + short codegen reference (~50 lines)
4. Claude mechanically translates IR → spec code

## Files to Create/Modify

| File | Action |
|------|--------|
| `src/empire/acceptance/parser.cljc` | **Create** - the parser |
| `deps.edn` | **Modify** - add `:parse-tests` alias |
| `spec/empire/acceptance/parser_spec.clj` | **Create** - tests for the parser |

## Output Location

Each `acceptanceTests/<name>.txt` produces `acceptanceTests/<name>.edn` alongside it.

## IR Format

Each `.edn` file contains:

```edn
{:source "army.txt"
 :tests
 [{:line 7
   :description "Army put to sentry mode"
   :givens [<given-ir> ...]
   :whens  [<when-ir> ...]
   :thens  [<then-ir> ...]}
  ...]}
```

### GIVEN IR Types

```edn
;; Map setup
{:type :map :target :game-map :rows ["A#"]}
{:type :map :target :player-map :rows ["A." ".."]}}

;; Unit properties
{:type :unit-props :unit "A" :props {:mode :awake}}
{:type :unit-props :unit "F" :props {:fuel 20 :mode :moving}}

;; Compound: waiting-for-input (parser tracks if mode was already set)
{:type :waiting-for-input :unit "A" :set-mode true}
{:type :waiting-for-input :unit "F" :set-mode false}  ; mode set in prior GIVEN

;; Container state (city airport, carrier fighters)
{:type :container-state :target "O" :props {:fighter-count 1 :awake-fighters 1}}
{:type :container-state :target "C" :props {:fighter-count 0}}

;; Production
{:type :production :city "O" :item :army}
{:type :production :city "O" :item :transport :remaining-rounds 1}

;; Other state
{:type :round :value 5}
{:type :destination :coords [3 7]}
{:type :cell-props :coords [0 0] :props {:awake-fighters 1 :fighter-count 1}}
{:type :player-items :items ["A" "T" "O"]}
```

### WHEN IR Types

```edn
;; Key press - parser determines input function
{:type :key-press :key :s :input-fn :key-down}         ; non-direction → key-down + Quil mock
{:type :key-press :key :d :input-fn :handle-key}        ; lowercase direction + attention → handle-key
{:type :key-press :key :D :input-fn :key-down}          ; uppercase direction → key-down + Quil mock

;; Battle outcomes - parser determines unit type for advance-game wrapping
{:type :battle :key :d :outcome :win :combat-type :army}    ; army → handle-key only
{:type :battle :key :d :outcome :win :combat-type :ship}    ; ship → handle-key + advance-game
{:type :battle :key :d :outcome :lose :combat-type :ship}

;; Backtick commands
{:type :backtick :key :A :mouse-cell [0 0]}

;; Mouse
{:type :mouse-click :coords [3 5]}
{:type :mouse-at-key :coords [3 5] :key :m}

;; Game advancement
{:type :advance-game}
{:type :start-new-round}
{:type :process-player-items}
```

### THEN IR Types

```edn
;; Unit position
{:type :unit-at :unit "A" :coords [0 2]}

;; Unit properties
{:type :unit-prop :unit "A" :property :mode :expected :sentry}
{:type :unit-prop :unit "F" :property :fuel :expected 19}
{:type :unit-prop :unit "A" :property :owner :expected :player}

;; Unit absent/present
{:type :unit-absent :unit "A"}
{:type :unit-present :unit "A" :coords [0 0]}

;; Messages (three display areas)
{:type :message-contains :area :attention :text "fuel:20"}
{:type :message-contains :area :attention :config-key :army-found-city}
{:type :message-contains :area :turn :text "Destroyer destroyed"}
{:type :message-is :area :turn :config-key :conquest-failed}
{:type :message-is :area :turn :format {:key :marching-orders-set :args [3 7]}}
{:type :no-message :area :attention}

;; Cell assertions
{:type :cell-prop :coords [0 0] :property :city-status :expected :player}
{:type :cell-type :coords [0 0] :expected :city}

;; State assertions
{:type :waiting-for-input :expected true}
{:type :waiting-for-input :expected false}
{:type :round :expected 5}
{:type :destination :expected [3 7]}
{:type :production :city "O" :expected :army}
{:type :no-production :city "O"}

;; Position after movement (parser encodes advance strategy)
{:type :unit-at-next-round :unit "D" :target "="}     ; 3 advances
{:type :unit-eventually-at :unit "A" :target "%"}      ; 20 advances

;; Container assertions
{:type :container-prop :target "O" :property :fighter-count :expected 1 :lookup :city}
{:type :container-prop :target "C" :property :fighter-count :expected 1 :lookup :unit}

;; Compound (parsed from natural language)
{:type :unit-occupies-cell :unit "D" :target-unit "s"} ; D is at where s was
{:type :unit-unmoved :unit "s"}                         ; s is still at original pos
{:type :unit-waiting-for-input :unit "F"}               ; F is awake + waiting-for-input true
```

### Unrecognized Directives

```edn
{:type :unrecognized :text "some directive we can't parse" :line 42}
```

Parser flags these rather than guessing. Claude handles only the edge cases.

## Parser Architecture

### Top-level flow

```
read-file → split-into-tests → for each test:
  extract-comment-header
  parse-givens  → [given-ir ...]
  parse-whens   → [when-ir ...]
  parse-thens   → [then-ir ...]
```

### Key parsing functions

1. **`parse-file [path]`** — reads file, returns full IR map
2. **`split-into-tests [lines]`** — splits on `; ====...====` headers, groups GIVEN/WHEN/THEN blocks
3. **`parse-given [lines context]`** — dispatches on patterns:
   - `game map` / `player map` / `computer map` → collect subsequent indented/map lines
   - `<unit> is waiting for input` → compound expansion
   - `<unit> has/is ...` → property extraction
   - `production at ...` → production setup
   - etc.
4. **`parse-when [lines context]`** — dispatches on patterns:
   - `the player presses <key>` → determine input-fn from key + context
   - `... and wins/loses the battle` → battle with outcome
   - `the mouse is at ... backtick then <key>` → backtick command
   - `a new round starts` / `the next round begins` → game advancement
5. **`parse-then [lines context]`** — dispatches on patterns:
   - Split on ` and ` / `and ` continuations first
   - Then match each clause against assertion patterns

### Context tracking

The parser maintains context per test:
- Which units' modes were explicitly set (for `waiting-for-input` → `:set-mode`)
- What unit types are on the map (for battle → `:combat-type`)
- Whether test has waiting-for-input (for key press → `:input-fn`)

## deps.edn Alias

```clojure
:parse-tests {:main-opts ["-m" "empire.acceptance.parser"]
              :extra-paths ["spec"]}
```

## Verification

1. Run `clj -M:parse-tests` — should produce `.edn` files for all 4 `.txt` files
2. Manually inspect each `.edn` file against the `.txt` source
3. Run `clj -M:spec spec/empire/acceptance/parser_spec.clj` — parser unit tests pass
4. Verify that the existing `army_spec.clj` could be mechanically regenerated from `army.edn`
