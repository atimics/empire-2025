# Plan: Acceptance Test Framework

## Overview

Acceptance tests are plain-text `.txt` files in the `acceptanceTests/` directory, written in Given/When/Then format. Claude Code is the test runner — it reads each `.txt` file, interprets the directives, translates each test into a temporary Speclj spec file, and runs it with `clj -M:spec`. The source filename and GIVEN line number are embedded in the spec's `it` description so that failures trace back to the original acceptance test.

## Execution Mechanism

Claude translates each acceptance test into a temporary Speclj spec file in the scratchpad directory. Each test (GIVEN...WHEN...THEN group) becomes one `it` block. The `it` description contains the source filename and line number of the first GIVEN line, plus the preceding comment as a human-readable label.

### Generated Spec Structure

For a file `acceptanceTests/fighter-fuel-attention.txt` with a test starting at line 3:

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

1. Read each `.txt` file from `acceptanceTests/`.
2. Parse into tests (each GIVEN...WHEN...THEN group).
3. Generate a temporary `.clj` spec file in the scratchpad directory.
4. Run with `clj -M:spec <path-to-generated-spec>`.
5. Report results. Speclj failure output will include the `it` description which contains the source file and line number.
6. The temporary spec file is disposable — regenerated fresh each run.

### Failure Reporting

When a test fails, Speclj displays:

```
1) fighter-fuel-attention.txt:3 - Regular fighter shows fuel in attention message
   Expected: "fuel:20"
   to be in: "fighter needs attention"
   /path/to/scratchpad/acceptance_fighter_fuel_attention_spec.clj:14
```

The `it` description traces back to the acceptance test source. The Speclj file path points to the generated spec for debugging the translation if needed.

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

## CLAUDE.md Updates

The Acceptance Tests section in CLAUDE.md should be updated to:
- Reference this plan for the full directive catalog.
- Include the five rules above.
- Note that acceptance tests are translated to temporary Speclj specs and run with `clj -M:spec`.

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

## Status: permanent
