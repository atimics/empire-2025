# Plan: Acceptance Test Framework

## Overview

Acceptance tests are plain-text `.txt` files in the `acceptanceTests/` directory, written in Given/When/Then format. Claude Code is the test runner — it reads each `.txt` file, interprets the directives, translates each test into a Speclj spec file in `generated-acceptance-specs/`, and runs it with `clj -M:spec`. The source filename and GIVEN line number are embedded in the spec's `it` description so that failures trace back to the original acceptance test.

## Execution Mechanism

Generated specs live in `generated-acceptance-specs/` at the project root, tracked in git. Claude translates each acceptance test into a Speclj spec file in that directory. Each test (GIVEN...WHEN...THEN group) becomes one `it` block. The `it` description contains the source filename and line number of the first GIVEN line, plus the preceding comment as a human-readable label.

- Naming convention: `acceptanceTests/transports.txt` → `generated-acceptance-specs/acceptance/transports_spec.clj`
- Namespace convention: `acceptance.transports-spec`

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

### Execution Flow

1. For each `.txt` file in `acceptanceTests/`:
   a. Compute the corresponding spec path in `generated-acceptance-specs/acceptance/`.
   b. Compare file modification dates: if the spec doesn't exist, or the `.txt` is newer than the `.clj`, regenerate the spec.
   c. If the spec is up-to-date, skip regeneration.
2. Run all specs: `clj -M:spec generated-acceptance-specs/`.
3. Report results. Speclj failure output will include the `it` description which contains the source file and line number.

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

## WHEN Directives

WHEN directives perform actions. Claude interprets the intent and generates the corresponding Clojure call.

### Keyboard Input

```
WHEN the player presses d.
WHEN the player presses space.
WHEN the player presses Q.
WHEN the player types d d d.
```

**Mocking approach:** Call `input/key-down` directly with the key keyword. No Quil dependency — it operates entirely on atoms. Reset `atoms/last-key` to nil between keys to simulate key release.

```clojure
;; "WHEN the player presses d"
(reset! atoms/last-key nil)
(input/key-down :d)
```

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
WHEN player items are processed.
```

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

```
THEN the message contains "fuel:20".
THEN message contains fuel:20
THEN the message is "fighter needs attention (fuel:20)".
THEN line2-message contains "Submarine destroyed".
THEN line3-message contains "Conquest Failed".
```

Translates to:
```clojure
(should-contain "fuel:20" @atoms/message)
(should= "fighter needs attention (fuel:20)" @atoms/message)
(should-contain "Submarine destroyed" @atoms/line2-message)
```

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

## Rules

1. Always ask permission before modifying an acceptance test file.
2. Clear context (`reset-all-atoms!`) before each test.
3. Before a push, ask whether acceptance tests should be run.
4. On failure, report file name and line number of the first GIVEN line of the failing test.
5. If a directive is ambiguous, report the ambiguity rather than guessing.
6. Generated specs in `generated-acceptance-specs/` should be committed after regeneration.

## CLAUDE.md Updates

The Acceptance Tests section in CLAUDE.md should be updated to:
- Reference this plan for the full directive catalog.
- Include the six rules above.
- Note that acceptance tests are translated to Speclj specs in `generated-acceptance-specs/` and run with `clj -M:spec`.

## Example Acceptance Test File

File: `acceptanceTests/fighter-fuel-attention.txt`

```
; Fighter attention message should display fuel.

; Regular fighter shows fuel in attention message.
GIVEN game map
  F#
F is awake with fuel 20.
GIVEN player-items F.

WHEN player items are processed.

THEN waiting-for-input.
THEN message contains "fuel:20".
THEN message contains "fighter".

; Airport fighter shows full fuel after refueling.
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
          [empire.ui.input :as input])
```

Add only namespaces actually used by the generated spec.

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

**CRITICAL: Strings in `build-test-map` are COLUMNS, not rows.**

`build-test-map` creates a 2D vector indexed as `(get-in game-map [x y])` where:
- **x** = column (left-right, increases going right) — the string index
- **y** = row (top-bottom, increases going down) — the character index within a string

Each string in the input vector represents one **column** of the map, read top-to-bottom.

Example: `(build-test-map ["~T#" "~~~"])`
- String 0 `"~T#"` = column 0: `[0,0]=sea, [0,1]=transport, [0,2]=land`
- String 1 `"~~~"` = column 1: `[1,0]=sea, [1,1]=sea, [1,2]=sea`

Visual layout (x across, y down):
```
     x=0  x=1
y=0   ~    ~
y=1   T    ~
y=2   #    ~
```

**Direction keys** use `[dx, dy]` which is `[dcol, drow]`:
- `:w` north = `[0, -1]` (up)
- `:x` south = `[0, 1]` (down)
- `:a` west = `[-1, 0]` (left)
- `:d` east = `[1, 0]` (right)
- `:q` NW = `[-1, -1]`, `:e` NE = `[1, -1]`, `:z` SW = `[-1, 1]`, `:c` SE = `[1, 1]`

**Movement applies direction to coords:** `[(+ x dx) (+ y dy)]` (`input.cljc:153`).

**`determine-cell-coordinates`** (`map_utils.cljc:84`) converts pixel coords:
- `cols = (count @atoms/game-map)` = number of strings = number of columns
- `rows = (count (first @atoms/game-map))` = chars per string = number of rows
- Returns `[int(pixel-x / cell-w), int(pixel-y / cell-h)]` = `[col, row]` = `[x, y]`

**`find-unit-pos` / `get-test-unit`** returns `[string-idx, char-idx]` = `[x, y]`.

**Common mistake:** Reading acceptance test maps as rows. The string `"~T#"` is a vertical column (sea above, transport middle, land below), NOT a horizontal row.

**Designing test maps:** To place land **south** of a transport, put the land character AFTER the transport in the SAME string. To place land **east** of a transport, put land at the same character position in the NEXT string.

### Test Map Characters

From `test-utils.cljc` `char->cell`:

| Char | Cell |
|------|------|
| `~` | `{:type :sea}` |
| `#` | `{:type :land}` |
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

## Status: permanent
