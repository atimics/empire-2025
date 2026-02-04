
# Plan: Acceptance Test Framework

## Overview

Acceptance tests are plain-text `.txt` files in the `acceptanceTests/` directory, written in Given/When/Then format. An automated three-stage pipeline translates them into runnable Speclj specs:

```
.txt → Parser → .edn → Generator → .clj → Speclj runner
```

The source filename and GIVEN line number are embedded in each spec's `it` description so that failures trace back to the original acceptance test.

**When to read this file:** Only when modifying or debugging the parser (`src/empire/acceptance/parser.cljc`) or generator (`src/empire/acceptance/generator.cljc`). This file is not needed for writing acceptance tests or running the pipeline.

## Design Principle

Prefer tests based on observable behavior rather than implementation details. Assert what the player would see (unit position after movement, messages, city status) rather than internal state (mode, target, steps-remaining).

## Execution Mechanism

### Pipeline

The pipeline has three stages, each with its own CLI alias:

1. **Parse** (`clj -M:parse-tests`): The parser (`src/empire/acceptance/parser.cljc`) scans `acceptanceTests/` for `.txt` files and produces an EDN intermediate representation for each one (e.g., `acceptanceTests/army.txt` → `acceptanceTests/army.edn`). The EDN captures test structure (descriptions, line numbers, GIVEN/WHEN/THEN directives) in a machine-readable format.

2. **Generate** (`clj -M:generate-specs`): The generator (`src/empire/acceptance/generator.cljc`) reads `.edn` files from `acceptanceTests/` and produces complete Speclj spec files in `generated-acceptance-specs/acceptance/` (e.g., `army.edn` → `generated-acceptance-specs/acceptance/army_spec.clj`). The generator determines required namespaces automatically based on the directives used.

3. **Run** (`clj -M:spec generated-acceptance-specs/`): Standard Speclj execution.

Shorthand to run the full pipeline:
```bash
clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/
```

### Freshness Checks

Before running, compare modification dates through the chain:
- If `.txt` is newer than `.edn`, re-parse.
- If `.edn` is newer than `.clj`, regenerate.

### File Conventions

- Naming: `acceptanceTests/transports.txt` → `acceptanceTests/transports.edn` → `generated-acceptance-specs/acceptance/transports_spec.clj`
- Namespace: `acceptance.transports-spec`
- Generated specs and `.edn` files are tracked in git and should be committed after regeneration.
- Never modify generated specs or `.edn` files directly; only delete and regenerate from the `.txt` source.

### Generated Spec Structure

For a file `acceptanceTests/fighter-fuel-attention.txt` with a test starting at line 3, the generated spec lives at `generated-acceptance-specs/acceptance/fighter_fuel_attention_spec.clj`:

```clojure
(ns acceptance.fighter-fuel-attention-spec
  (:require [speclj.core :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                       get-test-city reset-all-atoms!]]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.game-loop.item-processing :as item-processing]
            [empire.ui.input :as input]))

(describe "fighter-fuel-attention.txt"
  (it "fighter-fuel-attention.txt:3 - Regular fighter shows fuel in attention message"
    (reset-all-atoms!)
    ;; GIVEN
    (reset! atoms/game-map (build-test-map ["F#"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20)
    (reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "F"))])
    ;; WHEN
    (item-processing/process-player-items-batch)
    ;; THEN
    (should @atoms/waiting-for-input)
    (should-contain "fuel:20" @atoms/message)
    (should-contain "fighter" @atoms/message))

  (it "fighter-fuel-attention.txt:14 - Airport fighter shows full fuel after refueling"
    (reset-all-atoms!)
    ;; ...
    ))
```

Each test becomes one `it` block. The `it` description contains the source filename and line number of the first GIVEN line, plus the preceding comment as a human-readable label.

### Failure Reporting

When a test fails, Speclj displays:

```
1) fighter-fuel-attention.txt:3 - Regular fighter shows fuel in attention message
   Expected: "fuel:20"
   to be in: "fighter needs attention"
   generated-acceptance-specs/acceptance/fighter_fuel_attention_spec.clj:14
```

The `it` description traces back to the acceptance test source. The generated spec file path is a stable, version-controlled path, making failures easier to debug.

## File Format

```
; Comment lines start with semicolons
; Blank lines are ignored

;===============================================================
; Army moves east when player presses d.
;===============================================================
GIVEN game map
~~A~~
#####
A2 is awake.

WHEN the player presses d.

THEN A is at [0 3].
THEN the message contains "army".
```

### Structure Rules

- GIVEN, WHEN, and THEN are **case-sensitive** keywords that start directive blocks.
- A directive begins at a GIVEN/WHEN/THEN keyword and continues until the next GIVEN/WHEN/THEN keyword, a comment line, or a blank line. Directives may span multiple lines.
- Semicolon-prefixed lines are comments.
- Blank lines are ignored.
- Brackets and quotes may be used freely (e.g., `[0 2]`, `["~~A~" "####"]`) but are not required.
- A test consists of a group of GIVEN lines, followed by a group of WHEN lines, followed by a group of THEN lines. The next GIVEN after a THEN starts a new test.
- Context is cleared (via `reset-all-atoms!`) before each test.
- Each test's comment header is surrounded by separator lines: `;===============================================================`

## GIVEN Directives

GIVEN directives set up the game state. Each line (or multi-line block) is interpreted by Claude. Examples:

### Maps

Maps default to `game-map`. The target map can be specified explicitly.

**Bare lines (default to game-map):**
```
GIVEN map
~~A~
~~#~
```

**Explicit map target:**
```
GIVEN game map
~~A~~
#####

GIVEN computer map
~~a~~
#####

GIVEN player map
~.A..
.....
```

**EDN format:**
```
GIVEN computer map ["~~a~" "####"]
```

**Whitespace stripping:** Many acceptance tests indent map lines with leading spaces (e.g., `  A#`). Since `build-test-map` maps space characters to nil cells, **always strip leading whitespace** from map text lines before passing them to `build-test-map`.

All map formats translate to:
```clojure
(reset! atoms/<target>-map (build-test-map <rows>))
```

### Unit Properties

Multi-line GIVEN blocks describe unit state in natural language:

```
GIVEN
A2 is awake.
F has fuel 20 and is in mode moving.
T is in sentry mode with army-count 3.
```

Or single-line:
```
GIVEN A is awake with fuel 20.
GIVEN unit T mode sentry army-count 3
```

These translate to `set-test-unit` calls:
```clojure
(set-test-unit atoms/game-map "A2" :mode :awake)
(set-test-unit atoms/game-map "F" :fuel 20 :mode :moving)
```

### Other State

```
GIVEN production at O is army.
GIVEN no production.
GIVEN round 5.
GIVEN destination [3 7].
GIVEN cell [0 0] has awake-fighters 1 and fighter-count 1.
GIVEN waiting-for-input.
GIVEN player-items are A, T, O.
```

### Waiting for Input

```
GIVEN A is waiting for input.
GIVEN T is waiting for input.
```

Combines the verbose setup pattern (set mode awake, set player-items, process items) into one line. Translates to:

```clojure
;; Set mode :awake only if no mode was explicitly set in prior GIVEN lines
(set-test-unit atoms/game-map "A" :mode :awake)
(let [cols (count @atoms/game-map)
      rows (count (first @atoms/game-map))
      pos (:pos (get-test-unit atoms/game-map "A"))]
  (reset! atoms/player-map (make-initial-test-map rows cols nil))
  (reset! atoms/player-items [pos])
  (item-processing/process-player-items-batch))
```

Replaces the three-line pattern:
```
A is awake.
GIVEN player-items A.
...
WHEN player items are processed.
```

If unit properties were set in earlier GIVEN lines (e.g., `T is sentry with army-count 3`), the mode is NOT overridden to `:awake`.

## WHEN Directives

WHEN directives perform actions. Claude interprets the intent and generates the corresponding Clojure call.

### Keyboard Input

```
WHEN the player presses d.
WHEN the player presses space.
WHEN the player presses Q.
WHEN the player types d d d.
```

**Critical: `handle-key` vs `key-down`**

`input/key-down` dispatches through several handlers that call `q/mouse-x`/`q/mouse-y` before reaching `handle-key`. These include `set-city-marching-orders-by-direction` (direction keys), `set-lookaround-at-mouse` (`:l`), `wake-at-mouse` (`:u`), and others. Without Quil running, these crash.

| Key type | Function | Notes |
|----------|----------|-------|
| Lowercase direction (q/w/e/a/d/z/x/c) when unit has attention | `(input/handle-key :d)` | Single-step movement. Bypasses key-down dispatch entirely. |
| Uppercase direction (Q/W/E/A/D/Z/X/C) | Mock Quil + `(input/key-down :D)` | Extended movement to map edge. |
| Non-direction keys (s, l, u, space, etc.) | Mock Quil + `(input/key-down :s)` | Mode changes, skip, etc. |

**Quil mocking:** All `key-down` calls in specs must wrap Quil mouse functions with `with-redefs`:

```clojure
(with-redefs [quil.core/mouse-x (constantly 0)
              quil.core/mouse-y (constantly 0)]
  (reset! atoms/last-key nil)
  (input/key-down :l))
```

With `map-screen-dimensions` at `[0 0]` (from `reset-all-atoms!`), `on-map?` returns false for pixel `(0, 0)`, causing all mouse-dependent handlers to return nil. The key then falls through to `handle-key`.

```clojure
;; "WHEN the player presses d" (lowercase direction, unit has attention)
(input/handle-key :d)

;; "WHEN the player presses D" (uppercase direction, extended movement)
(with-redefs [quil.core/mouse-x (constantly 0)
              quil.core/mouse-y (constantly 0)]
  (reset! atoms/last-key nil)
  (input/key-down :D))

;; "WHEN the player presses s" (non-direction key)
(with-redefs [quil.core/mouse-x (constantly 0)
              quil.core/mouse-y (constantly 0)]
  (reset! atoms/last-key nil)
  (input/key-down :s))
```

### Randomized Outcomes

When a directive needs a deterministic result from `rand`, describe the **observable outcome** rather than the mock value. The natural-language clause tells the reader *why* rand is being mocked.

```
WHEN the player presses d and wins the battle.
WHEN the player presses d and loses the battle.
WHEN the game advances and the dice choose east.
```

Translates to `with-redefs [rand (constantly <value>)]` wrapping the action. Choose the value that produces the described outcome.

**Important:** For ship-to-ship combat, `handle-key` only sets `:mode :moving` with a target. The actual combat resolves during `advance-game` when `move-current-unit` processes the movement. The `with-redefs [rand ...]` wrapper must cover both `handle-key` and `advance-game`:

```clojure
;; "and wins the battle" — rand < 0.5 → attacker always hits
(with-redefs [rand (constantly 0.0)]
  (input/handle-key :d)
  (game-loop/advance-game))

;; "and loses the battle" — rand >= 0.5 → defender always hits
(with-redefs [rand (constantly 1.0)]
  (input/handle-key :d)
  (game-loop/advance-game))
```

For army city conquest, combat resolves immediately in `handle-key` (via `attempt-conquest`), so `advance-game` is not needed.

### Mouse Input

```
WHEN the player clicks cell [3 5].
WHEN the player clicks [3 5].
```

**Mocking approach:** Bypass pixel-to-cell conversion and call `handle-cell-click` directly with cell coordinates.

```clojure
;; "WHEN the player clicks cell [3 5]"
(reset! atoms/last-clicked-cell [3 5])
(input/handle-cell-click 3 5)
```

For commands that read mouse position (marching orders `m`, waypoints `*`, destination `.`), mock `q/mouse-x` and `q/mouse-y` to return pixel values mapping to the target cell using `config/cell-size`, then call `input/key-down`.

```
WHEN the mouse is at cell [3 5] and the player presses m.
```

### Game Advancement

```
WHEN the game advances.
WHEN the game advances one batch.
WHEN a new round starts.
WHEN the next round begins.
WHEN player items are processed.
```

**Synonyms:** "a new round starts" and "the next round begins" are equivalent.

Translates to:
```clojure
(game-loop/advance-game)          ;; advances
(game-loop/advance-game-batch)    ;; advances one batch
(game-loop/start-new-round)       ;; new round starts
(item-processing/process-player-items-batch)  ;; player items are processed
```

## THEN Directives

THEN directives are assertions. Claude translates them into Speclj `should` / `should=` / `should-contain` calls.

### Unit Assertions

```
THEN A is at [0 2].
THEN unit A at [0 2]
THEN A is at [0 2] in mode moving.
THEN A has fuel 19.
THEN A has mode sentry.
THEN T has army-count 3.
THEN there is no A on the map.
THEN no unit at [1 3].
```

Translates to:
```clojure
;; "THEN A is at [0 2] in mode moving"
(let [{:keys [pos unit]} (get-test-unit atoms/game-map "A")]
  (should= [0 2] pos)
  (should= :moving (:mode unit)))

;; "THEN there is no A on the map"
(should-be-nil (get-test-unit atoms/game-map "A"))
```

### Message Assertions

Tests assert against three named display areas: **attention** (row 1),
**turn** (row 2), and **error** (row 3).

**By literal string** (quotes required):

```
THEN the attention message contains "fuel:20".
THEN the attention message is "City needs attention".
THEN the turn message contains "Submarine destroyed".
THEN the error message contains "Conquest Failed".
THEN there is no attention message.
THEN there is no turn message.
THEN there is no error message.
```

Translates to:
```clojure
(should-contain "fuel:20" @atoms/message)
(should= "City needs attention" @atoms/message)
(should-contain "Submarine destroyed" @atoms/turn-message)
(should-contain "Conquest Failed" @atoms/error-message)
(should= "" @atoms/message)
(should= "" @atoms/turn-message)
(should= "" @atoms/error-message)
```

**By config key** (`:key` syntax — preferred, decouples test from exact wording):

```
THEN the attention message contains :army-found-city.
THEN the error message is :conquest-failed.
THEN the attention message contains :cant-move-into-city.
```

Translates to:
```clojure
(should-contain (:army-found-city config/messages) @atoms/message)
(should= (:conquest-failed config/messages) @atoms/error-message)
(should-contain (:cant-move-into-city config/messages) @atoms/message)
```

**By format function** (for messages with `%s`/`%d` placeholders):

```
THEN the turn message is (fmt :marching-orders-set 3 7).
THEN the error message is (fmt :coastal-city-required "transport").
```

Translates to:
```clojure
(should= (format (:marching-orders-set config/messages) 3 7) @atoms/turn-message)
(should= (format (:coastal-city-required config/messages) "transport") @atoms/error-message)
```

**Backward compatibility:** Bare `message` (without "attention", "turn", or
"error") maps to the attention message (`@atoms/message`). `line2-message`
maps to `@atoms/turn-message`. `line3-message` maps to `@atoms/error-message`.
These old forms are deprecated; new tests should use the semantic names.

### Cell Assertions

```
THEN cell [0 0] is a city.
THEN cell [0 0] has city-status player.
THEN cell [1 0] is sea.
```

### State Assertions

```
THEN production at O is army.
THEN there is no production at O.
THEN waiting-for-input.
THEN not waiting-for-input.
THEN the game is paused.
THEN round is 5.
THEN destination is [3 7].
```

### Position After Movement

```
THEN at next round A will be at %.
THEN at next round D will be at =.
```

Asserts the unit's position after one full round of movement. Use the `advance-until-next-round` helper to drain the current round, cross the round boundary, and process the first batch of the new round:

```clojure
(advance-until-next-round)
(let [{:keys [pos]} (get-test-unit atoms/game-map "A")
      target-pos (:pos (get-test-cell atoms/game-map "%"))]
  (should= target-pos pos))
```

**Important:** Do NOT use `(dotimes [_ 3] (game-loop/advance-game))` or any fixed number of advances. The number of `advance-game` calls needed to reach a round boundary varies depending on game state (player cities needing attention, number of items in lists, etc.). Always use `advance-until-next-round` which watches `atoms/round-number` for the transition.

**Map sizing:** The target cell (`%` or `=`) must be placed at a distance of `1 + speed` from the unit's starting position — 1 cell for the drain step plus `speed` cells for the full round. For example, a fighter (speed 8) starting at position 0 needs the target at position 9: `F~~~~~~~~=` (10 cells).

**Timing after combat WHENs:** When the WHEN clause includes `advance-game` (e.g., ship combat with `with-redefs`), the THEN assertions check state **immediately after** — no additional advances needed. The `advance-until-next-round` pattern only applies after WHENs that only press a key. The phrase "at the next round" in a THEN after a combat WHEN means "after the combat resolves," not "after more advances."

```
THEN eventually A will be at %.
```

For movements that take multiple rounds (target farther than one round of movement). Advances through rounds until the unit arrives at the target:

```clojure
(let [target-pos (:pos (get-test-cell atoms/game-map "%"))]
  (loop [rounds 0]
    (when (and (< rounds 20)
               (not= target-pos (:pos (get-test-unit atoms/game-map "A"))))
      (advance-until-next-round)
      (recur (inc rounds))))
  (should= target-pos (:pos (get-test-unit atoms/game-map "A"))))
```

## Rules

1. Always ask permission before modifying an acceptance test file.
2. Clear context (`reset-all-atoms!`) before each test.
3. Before a push, ask whether acceptance tests should be run.
4. On failure, report file name and line number of the first GIVEN line of the failing test.
5. If a directive is ambiguous, report the ambiguity rather than guessing.
6. Generated specs in `generated-acceptance-specs/` should be committed after regeneration.
7. Never modify a generated spec file. Generated specs may only be created (from the `.txt` source) or deleted — never edited. If a spec needs to change, delete it and regenerate from the `.txt`.

## CLAUDE.md Updates

The Acceptance Tests section in CLAUDE.md should be updated to:
- Reference this plan for the full directive catalog.
- Include the six rules above.
- Note that acceptance tests are translated to Speclj specs in `generated-acceptance-specs/` and run with `clj -M:spec`.

## Example Acceptance Test File

File: `acceptanceTests/fighter-fuel-attention.txt`

```
; Fighter attention message should display fuel.

;===============================================================
; Regular fighter shows fuel in attention message.
;===============================================================
GIVEN game map
  F#
F is awake with fuel 20.
GIVEN player-items F.

WHEN player items are processed.

THEN waiting-for-input.
THEN message contains "fuel:20".
THEN message contains "fighter".

;===============================================================
; Airport fighter shows full fuel after refueling.
;===============================================================
GIVEN game map
  O~
GIVEN cell [0 0] has awake-fighters 1 and fighter-count 1.
GIVEN player-items O.

WHEN player items are processed.

THEN waiting-for-input.
THEN message contains "fuel:32".
THEN message contains "Landed and refueled".
```

## Translation Reference

This section documents the Clojure functions and calling patterns needed to translate acceptance test directives into Speclj specs.

### Required Namespaces

```clojure
(:require [speclj.core :refer :all]
          [empire.test-utils :refer [build-test-map set-test-unit get-test-unit
                                     get-test-city reset-all-atoms!
                                     make-initial-test-map]]
          [empire.atoms :as atoms]
          [empire.config :as config]
          [empire.game-loop :as game-loop]
          [empire.game-loop.item-processing :as item-processing]
          [empire.player.production :as production]
          [empire.containers.ops :as container-ops]
          [empire.ui.input :as input]
          [quil.core :as q])
```

Add only namespaces actually used by the generated spec. Include `[quil.core :as q]` when the spec uses `key-down` (for Quil mocking).

### Game Loop Functions

| Directive | Function | Notes |
|-----------|----------|-------|
| "the game advances" | `(game-loop/advance-game)` | Advances one step: starts round, processes one player batch, or processes computer items |
| "the game advances one batch" | `(game-loop/advance-game-batch)` | Calls advance-game up to `config/advances-per-frame` times |
| "a new round starts" | `(game-loop/start-new-round)` | Calls `production/update-production`, builds player-items and computer-items lists |
| "player items are processed" | `(item-processing/process-player-items-batch)` | Processes player-items until empty, waiting-for-input, or 100 items |
| "production updates" | `(production/update-production)` | Decrements remaining-rounds, spawns units at zero |

### Item Processing Internals (`game-loop.item-processing`)

`process-player-items-batch` loops calling `process-one-item` which:
1. Checks for auto-launch-fighter (airport with flight-path)
2. Checks for auto-disembark-army (transport with awake armies + marching-orders)
3. If `attention/item-needs-attention?` → sets `waiting-for-input` and `cells-needing-attention`
4. Otherwise → `process-auto-movement` (handles :explore, :coastline-follow, :moving)

`move-current-unit` at `item_processing.cljc:14` moves a :moving unit one step.

### Keyboard Input (`ui.input`)

`input/key-down` dispatches keys. When `waiting-for-input` is true and `cells-needing-attention` has coords:

| Key | Handler | What it does |
|-----|---------|-------------|
| direction keys (q/w/e/a/d/z/x/c) | `handle-unit-movement-key` | Moves unit or disembarks army from transport |
| :space | `handle-space-key` | Skip / next unit |
| :u | `handle-unload-key` (`input.cljc:195`) | Wakes armies on transport via `container-ops/wake-armies-on-transport` |
| :s | `handle-sentry-key` | Sets unit to sentry mode |
| :l | `handle-look-around-key` | Sets unit to explore mode |
| production keys (a/f/t/d/s/c/b/p/z) | `handle-city-production-key` | Sets city production (when attention is on a city) |

**Important:** `handle-key` (called from key-down at `input.cljc:516`) reads `cells-needing-attention` to find the active coords. For key-based tests, `waiting-for-input` must be true and `cells-needing-attention` must contain the target unit's coords.

**key-down dispatch order** (`input.cljc:473`):
1. If `backtick-pressed` → debug/cheat commands (spawn units, own city)
2. Else checks in order: backtick, P (pause), space (step), + (toggle map), . (destination), m (marching orders), f (flight path), **u (wake-at-mouse)**, l (lookaround), city marching orders by direction, **handle-key** (the attention-based handler), * (waypoint)
3. The `:u` key at line 513 calls `wake-at-mouse` first (uses Quil `q/mouse-x`/`q/mouse-y`) — this will fail or no-op in tests without Quil mocking. Then falls through to `handle-key` at line 516 which calls `handle-unload-key`.
4. **For `:u` key tests:** Set up `waiting-for-input` true and `cells-needing-attention` with the transport coords. The `wake-at-mouse` call at line 513 will return nil (no Quil context), then `handle-key` picks it up.
5. **For direction key tests (disembark):** Same setup needed. `handle-key` → `handle-unit-movement-key` → `execute-unit-movement` which handles disembark when active unit is army-aboard-transport.

**handle-unload-key** (`input.cljc:195`):
- Checks `uc/transport-with-armies?` → calls `container-ops/wake-armies-on-transport`, then `game-loop/item-processed`
- Also handles carriers with fighters
- Called from `handle-key` when `:u` is pressed and attention is on a unit

**handle-unit-movement-key** (`input.cljc:165`):
- Extracts direction from key via `config/key->direction` or `config/key->extended-direction`
- Gets active unit via `movement/get-active-unit` (returns synthetic army if transport has awake armies)
- Calls `execute-unit-movement` which routes to disembark logic when aboard transport

### Transport-Specific Behavior

**Player transport production:**
- Spawned via `production/update-production` → `spawn-unit`
- Gets: `:type :transport`, `:hits 1`, `:mode :awake`, `:owner :player`, `:produced-at coords`
- Does NOT get: `:transport-mission`, `:stuck-since-round`, `:transport-id` (computer-only, in `computer.stamping`)

**Computer transport production:**
- Same base fields plus `computer-stamping/stamp-computer-fields` adds: `:transport-mission :idle`, `:stuck-since-round @round-number`, `:transport-id <next-id>`

**Transport needs-attention?** (`units/transport.cljc:25`):
- Returns true when `:mode :awake` OR `(:awake-armies unit) > 0`
- A sentry transport with no awake armies does NOT need attention

**Loading armies** (`containers/ops.cljc`):
- `load-adjacent-sentry-armies` loads nearby sentry armies onto a sentry transport
- Called during movement wake conditions, NOT during item processing directly
- For testing loading, call `container-ops/load-adjacent-sentry-armies` directly

**Wake armies** (`containers/ops.cljc`):
- `wake-armies-on-transport` sets transport to `:sentry`, sets `:awake-armies` = `:army-count`, clears `:reason`, clears `:steps-remaining`
- Triggered by `:u` key via `handle-unload-key`

**Disembark** (`containers/ops.cljc`):
- `disembark-army-from-transport` places army on land, decrements counts
- `disembark-army-with-target` disembarks army in :moving mode toward target
- `disembark-army-to-explore` disembarks army in :explore mode
- Triggered by direction keys when active unit is an army aboard transport

**Movement wake reasons:**
- `:transport-at-beach` — transport with armies reaches a cell adjacent to land (requires `:been-to-sea true`)
- `:transport-found-land` — transport moves from open sea (completely surrounded by sea) to a position with visible land

**Player-map requirement:** Movement tests typically need `(reset! atoms/player-map (make-initial-test-map rows cols nil))` for visibility updates.

**Transport config** (`units/transport.cljc`):
- speed: 2, cost: 30, hits: 1, capacity: 6, visibility-radius: 1
- `initial-state`: `{:army-count 0, :awake-armies 0, :been-to-sea true}`
- `can-move-to?`: sea cells only
- `full?`: army-count >= 6

### Coordinate System

**Strings in `build-test-map` are ROWS — the visual layout matches the game map.**

`build-test-map` parses each string as a row, then transposes into the column-major game map indexed as `(get-in game-map [x y])` where:
- **x** = column (left-right, increases going right) — the character index within a string
- **y** = row (top-bottom, increases going down) — the string index

Each string in the input vector represents one **row** of the map, read left-to-right.

Example: `(build-test-map ["~T#" "~~~"])`
- String 0 `"~T#"` = row 0: `[0,0]=sea, [1,0]=transport, [2,0]=land`
- String 1 `"~~~"` = row 1: `[0,1]=sea, [1,1]=sea, [2,1]=sea`

Visual layout (x across, y down):
```
     x=0  x=1  x=2
y=0   ~    T    #
y=1   ~    ~    ~
```

**Direction keys** use `[dx, dy]` which is `[dcol, drow]`:
- `:w` north = `[0, -1]` (up)
- `:x` south = `[0, 1]` (down)
- `:a` west = `[-1, 0]` (left)
- `:d` east = `[1, 0]` (right)
- `:q` NW = `[-1, -1]`, `:e` NE = `[1, -1]`, `:z` SW = `[-1, 1]`, `:c` SE = `[1, 1]`

**Movement applies direction to coords:** `[(+ x dx) (+ y dy)]` (`input.cljc:153`).

**`determine-cell-coordinates`** (`map_utils.cljc:84`) converts pixel coords:
- `cols = (count @atoms/game-map)` = number of columns
- `rows = (count (first @atoms/game-map))` = number of rows
- Returns `[int(pixel-x / cell-w), int(pixel-y / cell-h)]` = `[col, row]` = `[x, y]`

**`find-unit-pos` / `get-test-unit`** returns `[col, row]` = `[x, y]`.

**Designing test maps:** To place land **east** of a transport, put the land character AFTER the transport in the SAME string. To place land **south** of a transport, put land at the same character position in the NEXT string.

### Test Map Characters

From `test-utils.cljc` `char->cell`:

| Char | Cell |
|------|------|
| `~` | `{:type :sea}` |
| `#` | `{:type :land}` |
| `=` | `{:type :sea :label "="}` (referenceable sea) |
| `%` | `{:type :land :label "%"}` (referenceable land) |
| space `.` `-` | `nil` (unexplored/empty) |
| `+` | `{:type :city :city-status :free}` |
| `O` | `{:type :city :city-status :player}` |
| `X` | `{:type :city :city-status :computer}` |
| `A` | land + player army |
| `T` | sea + player transport |
| `D` | sea + player destroyer |
| `F` | land + player fighter |
| `a` `t` `d` `f` etc. | lowercase = computer units |

### Production Setup Pattern

```
GIVEN production at O is transport with 1 round remaining.
```
Translates to:
```clojure
(let [city-coords (:pos (get-test-city atoms/game-map "O"))]
  (swap! atoms/production assoc city-coords {:item :transport :remaining-rounds 1}))
```

For "O2" (second player city): `(get-test-city atoms/game-map "O2")`

### Player-Items Setup Pattern

```
GIVEN player-items T.
```
Translates to:
```clojure
(reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "T"))])
```

Multiple items: `GIVEN player-items are A, T, O.`
```clojure
(reset! atoms/player-items [(:pos (get-test-unit atoms/game-map "A"))
                            (:pos (get-test-unit atoms/game-map "T"))
                            (:pos (get-test-city atoms/game-map "O"))])
```

### Negative Assertions

```
THEN T does not have transport-mission.
```
Translates to:
```clojure
(let [{:keys [unit]} (get-test-unit atoms/game-map "T")]
  (should-not-contain :transport-mission unit))
```

## Spec Generation Pitfalls

Lessons learned from translating all acceptance test files into Speclj specs.

### City Attention Interference

When a test calls `start-new-round` and the map contains a player city (O), the city may intercept attention before the unit being tested. The city gets added to `player-items` and `process-player-items-batch` stops at it, blocking the target unit from being processed.

**Fix:** Set dummy production for any player city not under test:

```clojure
(let [o-pos (:pos (get-test-city atoms/game-map "O"))]
  (swap! atoms/production assoc o-pos {:item :army :remaining-rounds 10}))
```

This is required whenever:
- The test map has a player city AND
- The test is about a non-city unit AND
- The test calls `start-new-round` or advances through a round boundary

### City vs Unit Key Dispatch

When a **city** has attention, lowercase direction keys are **production keys** (d→destroyer, s→submarine, a→army, etc.), not movement keys. When a **unit** has attention, those same keys are movement/mode keys. The dispatch is determined by what `cells-needing-attention` points to.

When translating tests, check whether the GIVEN sets attention on a city or a unit — this determines what "the player presses d" means.

### "and" Continuations

Lines beginning with "and" (lowercase) are continuations of the preceding THEN directive, not new directives:

```
THEN F wakes up and asks for input,
and the out-of-fuel message is displayed.
```

Both clauses are assertions in the same `it` block. Translate as separate `should` calls.

### Untranslatable Directives

Some acceptance tests use directives not in the catalog above. When encountered:

1. Report the specific directive and file/line to the user.
2. Generate a **failing test** that documents the desired behavior (per CLAUDE.md rules).
3. Use a `(pending "directive not yet supported: ...")` or a clear `should` that will fail with context.

Common untranslatable patterns seen in practice:
- `"WHEN computer city at X is processed"` — requires computer item processing internals
- `"WHEN visibility updates"` — no direct function to call
- `"GIVEN all computer armies have country-id 1"` — bulk state mutation not in test-utils
- `"GIVEN game map with 11 computer cities"` — procedural map generation
- Prose-style THENs: `"THEN combat ensues and if the army wins..."` — conditional outcomes

### Bingo / Out-of-Fuel / Crash Pattern

For tests about fighter fuel events during round start:

```clojure
;; WHEN a new round starts
(game-loop/start-new-round)
;; Advance to process items and trigger wake/attention
(game-loop/advance-game)
```

`start-new-round` calls `consume-sentry-fighter-fuel` (decrements fuel, sets hits to 0 if fuel reaches 0) and `remove-dead-units` (removes units with 0 hits). Then `advance-game` processes the surviving units, triggering bingo/out-of-fuel wake conditions and setting messages.

### Advance Until Next Round

**This is the canonical pattern for all "at next round" / "at the next round" THEN assertions.** Never use `(dotimes [_ N] ...)` with a fixed count — the number of `advance-game` calls needed varies with game state.

Every spec that uses "at next round" assertions must include this helper:

```clojure
(defn- advance-until-next-round []
  (let [start-round @atoms/round-number]
    (loop [n 100]
      (cond
        (not= start-round @atoms/round-number)
        (do (game-loop/advance-game) :ok)

        (zero? n) :timeout

        :else
        (do (game-loop/advance-game)
            (recur (dec n)))))))
```

The loop drains the current round (processes all movement, explore, etc.) up to a maximum of 100 iterations. When `start-new-round` increments the round number, it exits the loop, calls one final `advance-game` (to process the first batch of the new round), and returns `:ok`. If the round never advances after 100 iterations, it returns `:timeout`.

Generated specs assert the return value:
```clojure
(should= :ok (advance-until-next-round))
```

This prevents tests from hanging indefinitely when game state prevents round advancement (e.g., missing config keys causing nil messages, broken movement logic).

Use this when:
- Any THEN says "at next round" or "at the next round" (position, state, or message assertions)
- `handle-key` sets a unit to `:moving` and the THEN checks position, wake reason, or message
- `start-new-round` triggers explore processing and the THEN checks the result
- Any WHEN that starts automated movement followed by THENs about the outcome

**Do NOT use** `(dotimes [_ 3] (game-loop/advance-game))` or any other fixed advance count. Player cities without production, extra units on the map, and other state can change how many advances are needed to cross a round boundary.

### Config Key Validation

Defense-in-depth for config key references in acceptance tests:

1. **Parser warning (parse time):** When `clj -M:parse-tests` runs, the parser checks every `:config-key` referenced in THEN clauses against `config/messages`. If a key doesn't exist, it prints:
   ```
   WARNING: army.txt:29 - config key :army-found-enemy-city not found in config/messages
   ```
   The EDN is still emitted as-is — this is a warning, not an error.

2. **Generator existence check (test time):** For every THEN that references a `:config-key`, the generator emits:
   ```clojure
   (should-not-be-nil (:army-found-city config/messages))
   (should-contain (:army-found-city config/messages) @atoms/attention-message)
   ```
   The `should-not-be-nil` assertion fails immediately with a clear message if the key doesn't exist, rather than passing `nil` to `should-contain` which would produce a confusing error.

### Additional THEN Patterns

These assertion patterns appear frequently across test files:

| Acceptance test phrasing | Speclj translation |
|---|---|
| `O has one fighter in its airport` | `(should= 1 (:fighter-count (get-in @atoms/game-map (:pos (get-test-city atoms/game-map "O")))))` |
| `C has one fighter aboard` | `(should= 1 (:fighter-count (:unit (get-test-unit atoms/game-map "C"))))` |
| `F wakes up and asks for input` | `(should= :awake (:mode (:unit (get-test-unit atoms/game-map "F")))) (should @atoms/waiting-for-input)` |

## Status: permanent
