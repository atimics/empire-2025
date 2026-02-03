# Display Area Redesign Plan

## Goal

Replace the ad-hoc bottom display area (line-1, line-2, line-3) with a
well-defined three-region layout and convert all inline message strings
to named config constants with format functions.

## Current Layout

```
|  Line 1: main message                   Round: 42   |
|  Line 2: confirmation/combat log          PAUSED    |
|  Line 3: flashing red warning       [50,30] hover   |
```

Two regions (left-justified messages, right-justified status) sharing
all three lines with no formal width boundaries. Debug message is
an afterthought centered on line 3.

## New Layout

Three named regions with explicit pixel boundaries and text justification:

```
| Game Info (37.5%)  |  Debug (25%)   | Game Status (37.5%) |
| left-justified     |  centered      | right-justified     |
|--------------------|----------------|---------------------|
| Attention          |  debug line 1  | Round Status        |
| Turn               |  debug line 2  | Hover Info          |
| Error (red)        |  debug line 3  | Production          |
```

- **Game Info** — left-justified. Player-facing notifications about the current turn.
- **Debug** — centered. Development-only diagnostic output. Removed in production.
- **Game Status** — right-justified. Persistent game state summary.

---

## Part 1 — Layout and Rendering

### 1.1 Config Constants (config.cljc)

Add width-fraction constants for the three named regions:

```clojure
(def game-info-width-fraction 0.375)    ;; Game Info (left)
(def debug-width-fraction 0.25)         ;; Debug (center)
(def game-status-width-fraction 0.375)  ;; Game Status (right)
```

Rename existing line-y constants for clarity (optional but recommended):

```
msg-line-1-y  →  msg-row-1-y
msg-line-2-y  →  msg-row-2-y
msg-line-3-y  →  msg-row-3-y
```

### 1.2 Region Boundary Calculation

Add to `rendering.cljc` or a helper:

```clojure
(defn compute-region-bounds [text-x text-w]
  (let [info-w   (* text-w game-info-width-fraction)
        debug-w  (* text-w debug-width-fraction)
        status-w (* text-w game-status-width-fraction)]
    {:game-info   {:x text-x                      :w info-w}
     :debug       {:x (+ text-x info-w)           :w debug-w}
     :game-status {:x (+ text-x info-w debug-w)   :w status-w}}))
```

### 1.3 Rendering Functions (rendering.cljc)

Replace `draw-line-1`, `draw-line-2`, `draw-line-3`, `draw-status`,
`draw-debug-window` with region-based functions. Each region draws its
own three rows using the text justification for that region.

**Game Info region (left-justified):**

| Row | Name | Atom | Color | Behavior |
|-----|------|------|-------|----------|
| 1 | Attention | `atoms/message` | White | Persistent until cleared |
| 2 | Turn | `atoms/turn-message` | White | Timed (was `line2-message`); also shows destination |
| 3 | Error | `atoms/error-message` | Red | Flashing, timed (was `line3-message`) |

- `draw-game-info` — master function for the Game Info region.
- `draw-attention` — draws `@atoms/message` left-justified in Game Info region, row 1.
- `draw-turn` — draws `@atoms/turn-message` left-justified in Game Info region, row 2. When no turn message is active and `@atoms/destination` is set, show destination.
- `draw-error` — draws `@atoms/error-message` in red, flashing, left-justified in Game Info region, row 3. Subject to `error-until` timestamp and 500ms blink.

All Game Info text is left-justified against the region's left edge
with `msg-left-padding` offset.

**Debug region (centered):**

- `draw-debug` — draws `@atoms/debug-message` in cyan, centered horizontally within the Debug region. Debug messages may span all three rows (split on newlines or truncate). This region is for development and will be removed in production.

All Debug text is centered within the region's horizontal bounds.

**Game Status region (right-justified):**

| Row | Name | Content | Color |
|-----|------|---------|-------|
| 1 | Round Status | "PAUSED Round: N" or "Round: N" | PAUSED in red, rest white |
| 2 | Hover Info | `@atoms/hover-message` | White |
| 3 | Production | Unit counts + exploration % | White |

- `draw-game-status` — master function for the Game Status region.
- `draw-round-status` — right-justified in Game Status region, row 1. If paused, prepend red "PAUSED " before "Round: N".
- `draw-hover-info` — right-justified in Game Status region, row 2. (Moved from row 3.)
- `draw-production-status` — right-justified in Game Status region, row 3. New function.

All Game Status text is right-justified against the region's right edge.

### 1.4 Production Status Display (new)

Format: `A:5 F:3 T:1 D:2 S:1 P:1 C:0 B:1 Z:0 | 42%`

Add a function (in `rendering_util.cljc` or a new `status.cljc`):

```clojure
(defn format-production-status [game-map player-map]
  ;; Count player units by type
  ;; Count explored cells in player-map (non-:unexplored)
  ;; Compute % = explored / total * 100
  ;; Format: "A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
  )
```

This should be computed periodically (e.g., once per round or on a timer),
not every frame. Store result in a new atom `atoms/production-status`.

### 1.5 Atom Renames (atoms.cljc)

| Old | New | Notes |
|-----|-----|-------|
| `message` | `message` | Keep — this is the Attention line |
| `line2-message` | `turn-message` | Semantic name |
| `confirmation-until` | `turn-message-until` | Follows rename |
| `line3-message` | `error-message` | Semantic name |
| `line3-until` | `error-until` | Follows rename |
| `hover-message` | `hover-message` | Keep — moves to status row 2 |
| `debug-message` | `debug-message` | Keep |

Rename helper functions:

| Old | New |
|-----|-----|
| `set-line3-message` | `set-error-message` |
| `set-confirmation-message` | `set-turn-message` |

Add new atom:

```clojure
(def production-status
  "Formatted string showing player unit counts and exploration %."
  (atom ""))
```

Add to `reset-all-atoms!` in `test_utils.cljc`.

---

## Part 2 — Message Constants and Format Functions

### 2.1 Format Function

No custom format function. Use `clojure.core/format` directly at callsites:

```clojure
(format (:marching-orders-set config/messages) 50 30)
;; => "Marching orders set to 50,30"
```

Message constants with `%s`/`%d` placeholders are format strings passed
to `clojure.core/format`. Constants without placeholders are used as-is.

### 2.2 New Message Constants

Add to `config/messages` map. Messages with `%s`/`%d` placeholders are
passed through `clojure.core/format`.

**Turn messages** (displayed in Turn line via `set-turn-message`):

| Key | Format String | Args | Current Location |
|-----|--------------|------|------------------|
| `:marching-orders-set` | `"Marching orders set to %d,%d"` | col, row | input.cljc:357,364,433; orders.cljc:43,50,110 |
| `:marching-orders-lookaround` | `"Marching orders set to lookaround"` | — | input.cljc:324; orders.cljc:23 |
| `:flight-path-set` | `"Flight path set to %d,%d"` | col, row | input.cljc:387,394; orders.cljc:70,77 |
| `:waypoint-placed` | `"Waypoint placed at %d,%d"` | col, row | input.cljc:408; orders.cljc:88 |
| `:waypoint-removed` | `"Waypoint removed from %d,%d"` | col, row | input.cljc:409; orders.cljc:89 |
| `:waypoint-orders-set` | `"Waypoint orders set to %d,%d"` | col, row | waypoint.cljc:33,51 |
| `:docked-for-repair` | `"%s docked for repair."` | unit-name | movement.cljc:231 |
| `:combat-result` | `"%s. %s destroyed."` | exchange-str, loser | combat.cljc:114 |

**Error messages** (displayed in Error line via `set-error-message`):

| Key | Format String | Args | Current Location |
|-----|--------------|------|------------------|
| `:coastal-city-required` | `"Must be coastal city to produce %s."` | unit-name | input.cljc:79; commands.cljc:23 |

(`:conquest-failed` and `:fighter-destroyed-by-city` already exist in config.)

**Attention messages** (displayed in Attention line):

| Key | Format String | Args | Current Location |
|-----|--------------|------|------------------|
| `:fighter-airport-attention` | `"Fighter needs attention - Landed and refueled.%s"` | fuel-str | attention.cljc:136 |
| `:fighter-carrier-attention` | `"Fighter needs attention - aboard carrier (%d fighters)%s"` | count, fuel | attention.cljc:139 |
| `:army-transport-attention` | `"Army needs attention - aboard transport (%d armies) - At beach."` | count | attention.cljc:142 |
| `:damaged-unit-attention` | `"Damaged %s needs attention%s%s%s"` | name, cargo, reason, fuel | attention.cljc:122 |
| `:unit-attention` | `"%s needs attention%s%s%s"` | name, cargo, reason, fuel | attention.cljc:122 |

### 2.3 Messages NOT in Attention/Turn/Error Lines

These messages are used elsewhere and do NOT currently appear in
the three display lines. They should still be converted to config
constants for consistency, but are lower priority:

| Context | Current String | Where Used |
|---------|---------------|------------|
| Hover display | `"city:player producing:fighter..."` | rendering_util.cljc:56-69 |
| Hover display | `"player army [8/10] fuel:16..."` | rendering_util.cljc:11-41 |
| Hover display | `"waypoint -> 50,60"` | rendering_util.cljc:71-77 |
| Combat log entries | `"c-3,S-1,S-1"` | combat.cljc:98-105 |
| Debug dump | Various debug format strings | debug.cljc |

These are internal/diagnostic formats, not player-facing messages in the
traditional sense. The hover display strings are structural formats rather
than notification messages — they could be templatized but serve a different
purpose than the Attention/Turn/Error messages.

---

## Part 3 — Callsite Updates

### 3.1 All `set-confirmation-message` → `set-turn-message`

Every callsite of `atoms/set-confirmation-message` must be updated to
`atoms/set-turn-message` and use `format` with config message keys instead of inline `str`:

| File | Lines | Change |
|------|-------|--------|
| `ui/input.cljc` | 324, 357, 364, 387, 394, 408, 409, 433 | Use `format` with config message keys |
| `player/orders.cljc` | 23, 43, 50, 70, 77, 88, 89, 110 | Use `format` with config message keys |
| `movement/waypoint.cljc` | 33, 51 | Use `format` with config message keys |
| `combat.cljc` | 256 | Use `format` with `:combat-result` |

### 3.2 All `set-line3-message` → `set-error-message`

| File | Lines | Change |
|------|-------|--------|
| `ui/input.cljc` | 79 | Use `format` with `:coastal-city-required` |
| `player/commands.cljc` | 23 | Use `format` with `:coastal-city-required` |
| `combat.cljc` | 65, 85 | Already uses config keys — just rename function |
| `movement/wake_conditions.cljc` | 200 | Already uses config key — just rename function |

### 3.3 All `reset! atoms/line2-message` → `reset! atoms/turn-message`

| File | Lines | Change |
|------|-------|--------|
| `movement/movement.cljc` | 231 | Use `format` with `:docked-for-repair` |

### 3.4 Attention message construction (attention.cljc)

Refactor `set-attention-message` (lines 127-148) to use `format` with
the new attention message keys instead of inline `str` concatenation.

### 3.5 Destination display move

Remove destination display from `draw-status`. Instead, when destination is
set and no turn message is active, show destination in the Turn line (row 2
left). This means `draw-turn` checks: if `turn-message` is active show it,
else if `destination` is set show `(format (:destination config/messages) col row)`.

Add to config:

```clojure
:destination "Dest: %d,%d"
```

---

## Part 4 — Implementation Order

1. **Add config constants** — new message keys in `config.cljc`
2. **Rename atoms** — `line2-message` → `turn-message`, `line3-message` → `error-message`, rename helpers
3. **Update all callsites** — mechanical find-and-replace across ~11 files
4. **Implement region layout** — width fractions, `compute-region-bounds`
5. **Rewrite rendering functions** — replace `draw-line-1/2/3`, `draw-status`, `draw-debug-window` with region-based functions
6. **Add production status** — new atom, counting function, rendering
7. **Move destination** — from status area to turn line
8. **Move PAUSED** — prepend to round status line
9. **Move hover** — from row 3 right to row 2 right
10. **Update test_utils.cljc** — add new atoms to `reset-all-atoms!`
11. **Run all tests** — verify nothing broke

Each step should have tests written first where applicable (per project rules).

---

## Files Modified

| File | Changes |
|------|---------|
| `config.cljc` | Add message keys, Game Info/Debug/Game Status width-fraction constants |
| `atoms.cljc` | Rename atoms and helpers, add `production-status` |
| `ui/rendering.cljc` | Rewrite message area with three regions |
| `ui/rendering_util.cljc` | Add `format-production-status`, move `should-show-paused?` logic |
| `ui/input.cljc` | Update ~8 callsites to use `set-turn-message`/`set-error-message` + `format` |
| `player/orders.cljc` | Update ~8 callsites |
| `player/commands.cljc` | Update ~2 callsites |
| `player/attention.cljc` | Refactor `set-attention-message` to use `format` with config keys |
| `movement/movement.cljc` | Update 1 callsite |
| `movement/waypoint.cljc` | Update 2 callsites |
| `movement/wake_conditions.cljc` | Update 1 callsite |
| `combat.cljc` | Update 2 callsites |
| `test_utils.cljc` | Add new atoms to `reset-all-atoms!` |

---

## Part 5 — Acceptance Test Framework Updates

This section describes changes to `plans/permanent/acceptance-test-framework.md`
needed to support the new display area in acceptance tests.

### 5.1 Display Area Reference

Add the following reference section to the acceptance test framework document.

#### Display Areas

The bottom of the screen has three named regions, each three rows tall:

```
| Game Info (left, 37.5%)   | Debug (center, 25%) | Game Status (right, 37.5%) |
| left-justified            | centered            | right-justified            |
|---------------------------|---------------------|----------------------------|
| Row 1: Attention          | debug line 1        | Round Status               |
| Row 2: Turn               | debug line 2        | Hover Info                 |
| Row 3: Error (red)        | debug line 3        | Production                 |
```

**Game Info** — left-justified, 37.5% width.

| Row | Display Name | Atom | Description |
|-----|-------------|------|-------------|
| 1 | Attention | `atoms/message` | Which unit or city needs player input. Persistent until cleared. |
| 2 | Turn | `atoms/turn-message` | Action confirmations (orders set, combat results, docked). Timed. Falls back to destination when no active message. |
| 3 | Error | `atoms/error-message` | Warnings and failures (conquest failed, fighter destroyed, coastal requirement). Red, flashing, timed. |

**Game Status** — right-justified, 37.5% width.

| Row | Display Name | Atom | Description |
|-----|-------------|------|-------------|
| 1 | Round Status | `atoms/round-number`, `atoms/paused` | "Round: N" or "PAUSED Round: N" |
| 2 | Hover Info | `atoms/hover-message` | Cell details on mouse-over |
| 3 | Production | `atoms/production-status` | Player unit counts and exploration % |

**Debug** — centered, 25% width. Development only, not tested in acceptance tests.

### 5.2 THEN Directives for Display Areas

Replace the existing "Message Assertions" section in the framework with
these directives using semantic display area names:

```
THEN the attention message contains "fuel:20".
THEN the attention message is "City needs attention".
THEN the turn message contains "Marching orders".
THEN the turn message is "Flight path set to 3,7".
THEN the error message contains "Conquest Failed".
THEN the error message is "Must be coastal city to produce transport.".
THEN there is no attention message.
THEN there is no turn message.
THEN there is no error message.
THEN round-status contains "PAUSED".
THEN production-status contains "A:5".
```

**Translation table:**

| Directive Pattern | Clojure Assertion |
|-------------------|-------------------|
| `the attention message contains "X"` | `(should-contain "X" @atoms/message)` |
| `the attention message is "X"` | `(should= "X" @atoms/message)` |
| `the turn message contains "X"` | `(should-contain "X" @atoms/turn-message)` |
| `the turn message is "X"` | `(should= "X" @atoms/turn-message)` |
| `the error message contains "X"` | `(should-contain "X" @atoms/error-message)` |
| `the error message is "X"` | `(should= "X" @atoms/error-message)` |
| `there is no attention message` | `(should= "" @atoms/message)` |
| `there is no turn message` | `(should= "" @atoms/turn-message)` |
| `there is no error message` | `(should= "" @atoms/error-message)` |
| `round-status contains "X"` | `(should-contain "X" (str @atoms/round-number))` or check paused |
| `production-status contains "X"` | `(should-contain "X" @atoms/production-status)` |

### 5.3 Asserting Against Named Message Constants

Tests can reference message keys by name rather than duplicating the
literal string. Use the `:key` syntax to look up from `config/messages`:

```
THEN the attention message is :city-needs-attention.
THEN the error message is :conquest-failed.
THEN the turn message contains :marching-orders-lookaround.
THEN the attention message contains :fighter-bingo.
```

**Translation:**

```clojure
;; "THEN the attention message is :city-needs-attention."
(should= (:city-needs-attention config/messages) @atoms/message)

;; "THEN the error message is :conquest-failed."
(should= (:conquest-failed config/messages) @atoms/error-message)

;; "THEN the turn message contains :marching-orders-lookaround."
(should-contain (:marching-orders-lookaround config/messages) @atoms/turn-message)
```

This decouples acceptance tests from the exact wording of messages.
If a message string changes in config, tests using `:key` syntax
continue to pass. Tests using literal strings will break — use literal
strings only when the exact wording is part of the acceptance criterion.

### 5.4 Formatted Message Assertions

For messages with arguments (coordinates, unit names), acceptance tests
can use `(fmt ...)` syntax, which translates to `clojure.core/format`:

```
THEN the turn message is (fmt :marching-orders-set 3 7).
THEN the turn message is (fmt :flight-path-set 5 10).
THEN the error message is (fmt :coastal-city-required "transport").
THEN the turn message is (fmt :docked-for-repair "Destroyer").
```

**Translation:**

```clojure
;; "THEN the turn message is (fmt :marching-orders-set 3 7)."
(should= (format (:marching-orders-set config/messages) 3 7) @atoms/turn-message)

;; "THEN the error message is (fmt :coastal-city-required \"transport\")."
(should= (format (:coastal-city-required config/messages) "transport") @atoms/error-message)
```

### 5.5 Backward Compatibility

The old directive forms remain valid as aliases during transition:

| Old Form | New Form | Notes |
|----------|----------|-------|
| `the message contains "X"` | `the attention message contains "X"` | `message` alone = attention |
| `message contains "X"` | `the attention message contains "X"` | Bare `message` = attention |
| `line2-message contains "X"` | `the turn message contains "X"` | Deprecated |
| `line3-message contains "X"` | `the error message contains "X"` | Deprecated |

Existing acceptance tests using the old names will still generate correct
specs (mapping to the renamed atoms). New tests should use the semantic names.

### 5.6 Complete Message Constant Reference

All named message constants available for acceptance test `:key` syntax:

**Attention messages:**

| Key | Format String | Args |
|-----|--------------|------|
| `:city-needs-attention` | `"City needs attention"` | — |
| `:unit-needs-attention` | `" needs attention"` | — |
| `:fighter-airport-attention` | `"Fighter needs attention - Landed and refueled.%s"` | fuel-str |
| `:fighter-carrier-attention` | `"Fighter needs attention - aboard carrier (%d fighters)%s"` | count, fuel |
| `:army-transport-attention` | `"Army needs attention - aboard transport (%d armies) - At beach."` | count |
| `:unit-attention` | `"%s needs attention%s%s%s"` | name, cargo, reason, fuel |
| `:damaged-unit-attention` | `"Damaged %s needs attention%s%s%s"` | name, cargo, reason, fuel |
| `:army-found-city` | `"Army found a city!"` | — |
| `:fighter-bingo` | `"Bingo! Refuel?"` | — |
| `:fighter-out-of-fuel` | `"Fighter out of fuel."` | — |
| `:fighter-landed-and-refueled` | `"Landed and refueled."` | — |
| `:fighter-over-defended-city` | `"Fighter about to fly over defended city."` | — |
| `:enemy-spotted` | `"Enemy spotted."` | — |
| `:transport-at-beach` | `"At beach."` | — |
| `:transport-found-land` | `"Found land!"` | — |
| `:found-a-bay` | `"Found a bay!"` | — |
| `:somethings-in-the-way` | `"Something's in the way."` | — |
| `:cant-move-into-water` | `"Can't move into water."` | — |
| `:cant-move-into-city` | `"Can't move into city."` | — |
| `:ships-cant-drive-on-land` | `"Ships don't drive on land."` | — |
| `:not-on-map` | `"That's not on the map!"` | — |
| `:returned-to-start` | `"Returned to start."` | — |
| `:hit-edge` | `"Hit map edge."` | — |
| `:blocked` | `"Blocked."` | — |
| `:steps-exhausted` | `"Lookaround limit reached."` | — |
| `:not-near-coast` | `"Not near coast."` | — |
| `:skipping-this-round` | `"Skipping this round."` | — |

**Turn messages:**

| Key | Format String | Args |
|-----|--------------|------|
| `:marching-orders-set` | `"Marching orders set to %d,%d"` | col, row |
| `:marching-orders-lookaround` | `"Marching orders set to lookaround"` | — |
| `:flight-path-set` | `"Flight path set to %d,%d"` | col, row |
| `:waypoint-placed` | `"Waypoint placed at %d,%d"` | col, row |
| `:waypoint-removed` | `"Waypoint removed from %d,%d"` | col, row |
| `:waypoint-orders-set` | `"Waypoint orders set to %d,%d"` | col, row |
| `:docked-for-repair` | `"%s docked for repair."` | unit-name |
| `:combat-result` | `"%s. %s destroyed."` | exchange-str, loser |
| `:destination` | `"Dest: %d,%d"` | col, row |

**Error messages:**

| Key | Format String | Args |
|-----|--------------|------|
| `:conquest-failed` | `"Conquest Failed"` | — |
| `:failed-to-conquer` | `"Failed to conquer city."` | — |
| `:fighter-shot-down` | `"Incoming anti-aircraft fire!"` | — |
| `:fighter-destroyed-by-city` | `"Fighter destroyed by city defenses."` | — |
| `:coastal-city-required` | `"Must be coastal city to produce %s."` | unit-name |

### 5.7 Example Acceptance Test Using New Directives

```
;===============================================================
; Conquest failure shows error message.
;===============================================================
GIVEN game map
  A+
A is waiting for input.

WHEN the player presses d and loses the battle.

THEN there is no A on the map.
THEN the error message is :conquest-failed.

;===============================================================
; Marching orders confirmation appears in turn message.
;===============================================================
GIVEN game map
  A###
A is waiting for input.

WHEN the mouse is at cell [3 0] and the player presses m.

THEN the turn message is (fmt :marching-orders-set 3 0).
THEN A has mode moving.

;===============================================================
; Fighter attention shows fuel.
;===============================================================
GIVEN game map
  F#
F is awake with fuel 20.
GIVEN F is waiting for input.

THEN waiting-for-input.
THEN the attention message contains "fuel:20".
THEN the attention message contains :unit-needs-attention.
```

---

## Part 6 — Acceptance Test Framework Document Update

The acceptance test framework document (`plans/permanent/acceptance-test-framework.md`)
must be updated to use the new display area names instead of the raw atom names.
This part describes the specific edits to that document.

### 6.1 Replace the "Message Assertions" Section

The current section (lines 320-335) reads:

```
### Message Assertions

THEN the message contains "fuel:20".
THEN message contains fuel:20
THEN the message is "fighter needs attention (fuel:20)".
THEN line2-message contains "Submarine destroyed".
THEN line3-message contains "Conquest Failed".

Translates to:
(should-contain "fuel:20" @atoms/message)
(should= "fighter needs attention (fuel:20)" @atoms/message)
(should-contain "Submarine destroyed" @atoms/line2-message)
```

Replace with:

```
### Message Assertions

Tests assert against the three named display areas: **attention** (row 1),
**turn** (row 2), and **error** (row 3). See "Display Area Reference" below
for the full layout.

**By literal string:**

THEN the attention message contains "fuel:20".
THEN the attention message is "City needs attention".
THEN the turn message contains "Submarine destroyed".
THEN the error message contains "Conquest Failed".
THEN there is no attention message.
THEN there is no turn message.
THEN there is no error message.

**By config key** (preferred — decouples test from exact wording):

THEN the attention message is :city-needs-attention.
THEN the error message is :conquest-failed.
THEN the attention message contains :fighter-bingo.

**By format function** (for messages with arguments):

THEN the turn message is (fmt :marching-orders-set 3 7).
THEN the error message is (fmt :coastal-city-required "transport").
THEN the turn message is (fmt :docked-for-repair "Destroyer").

Translates to:
(should-contain "fuel:20" @atoms/message)
(should= "City needs attention" @atoms/message)
(should-contain "Submarine destroyed" @atoms/turn-message)
(should-contain "Conquest Failed" @atoms/error-message)
(should= "" @atoms/message)
(should= "" @atoms/turn-message)
(should= "" @atoms/error-message)
(should= (:city-needs-attention config/messages) @atoms/message)
(should= (:conquest-failed config/messages) @atoms/error-message)
(should-contain (:fighter-bingo config/messages) @atoms/message)
(should= (format (:marching-orders-set config/messages) 3 7) @atoms/turn-message)
(should= (format (:coastal-city-required config/messages) "transport") @atoms/error-message)

Backward compatibility: bare "message" (without "attention", "turn", or
"error") maps to the attention message (@atoms/message). "line2-message"
maps to @atoms/turn-message. "line3-message" maps to @atoms/error-message.
These old forms are deprecated; new tests should use the semantic names.
```

### 6.2 Add "Display Area Reference" Section

Insert a new section after "State Assertions" (after line 355) in the
framework document:

```
### Display Area Reference

The message area at the bottom of the screen has three named regions:

| Game Info (left)    | Debug (center) | Game Status (right)  |
| left-justified      | centered       | right-justified      |
|---------------------|----------------|----------------------|
| Attention           | debug          | Round Status         |
| Turn                | debug          | Hover Info           |
| Error (red)         | debug          | Production           |

**Game Info** — left-justified:
- Attention (@atoms/message) — which unit/city needs input. Persistent.
- Turn (@atoms/turn-message) — confirmations, combat results, orders.
  Timed. Falls back to destination display when inactive.
- Error (@atoms/error-message) — warnings and failures. Red, flashing, timed.

**Game Status** — right-justified:
- Round Status — "Round: N" or "PAUSED Round: N"
- Hover Info (@atoms/hover-message) — cell details on mouse-over
- Production (@atoms/production-status) — player unit counts + exploration %

**Debug** — centered. Development only.

**Testable display areas:** attention, turn, error, production-status.
Round status and hover info depend on Quil mouse/rendering state and are
generally not tested in acceptance tests.
```

### 6.3 Update the "State Assertions" Section

Add production-status and round assertions to the existing state
assertions section (around line 345):

```
THEN production-status contains "A:5".
THEN round-status contains "PAUSED".
```

Translates to:

```clojure
(should-contain "A:5" @atoms/production-status)
;; round-status is composite: check round-number and paused atoms
(should @atoms/paused)
```

### 6.4 Update the Example Spec Structure

The example spec in section "Generated Spec Structure" (lines 22-51)
references `@atoms/message`. This remains correct since `atoms/message`
is not renamed. However, update the example to also show the turn and
error atoms:

```clojure
    ;; THEN
    (should @atoms/waiting-for-input)
    (should-contain "fuel:20" @atoms/message)           ;; attention message
    (should-contain "fighter" @atoms/message))           ;; attention message
```

And add to the example file the comment showing which display area the
atom corresponds to, for clarity in generated specs.

### 6.5 Update the Translation Reference Namespace

In the "Required Namespaces" section (lines 453-467), the require for
`empire.atoms` already covers the renamed atoms. No namespace change needed.
However, add `empire.config` usage note:

```
;; For :key and (format ...) message assertions:
[empire.config :as config]
```

This is already listed but should be called out as required when tests
use `:key` or `(fmt ...)` syntax. The `(fmt ...)` directive syntax in
acceptance tests translates to `clojure.core/format` in generated specs.

### 6.6 Existing Acceptance Test Migration

These existing acceptance test `.txt` files use old display area names
and should be updated (with permission) to the new semantic names:

| File | Line | Old Directive | New Directive |
|------|------|--------------|---------------|
| `unit-movement.txt` | 322 | `THEN line3-message contains "Fighter destroyed".` | `THEN the error message contains "Fighter destroyed".` |
| `player-production.txt` | 48 | `THEN line3-message contains "coastal".` | `THEN the error message contains "coastal".` |
| `fighter.txt` | 101 | `THEN message contains "fuel:20".` | `THEN the attention message contains "fuel:20".` |
| `submarine.txt` | 20 | `THEN message contains "Ships don't drive on land".` | `THEN the attention message contains :ships-cant-drive-on-land.` |
| `carrier.txt` | 46 | `THEN message contains "carrier".` | `THEN the attention message contains "carrier".` |
| `carrier.txt` | 47 | `THEN message contains "3 fighters".` | `THEN the attention message contains "3 fighters".` |
| `carrier.txt` | 58 | `THEN message contains "Damaged".` | `THEN the attention message contains "Damaged".` |
| `unit-movement.txt` | 262 | `THEN message contains "Can't move into water".` | `THEN the attention message contains :cant-move-into-water.` |
| `unit-movement.txt` | 274 | `THEN message contains "Ships don't drive on land".` | `THEN the attention message contains :ships-cant-drive-on-land.` |
| `unit-movement.txt` | 287 | `THEN message contains "Something's in the way".` | `THEN the attention message contains :somethings-in-the-way.` |
| `army.txt` | 38 | `THEN message contains "Army found a city".` | `THEN the attention message contains :army-found-city.` |
| `army.txt` | 71 | `THEN message contains "Can't move into city".` | `THEN the attention message contains :cant-move-into-city.` |

After migration, the generated specs in `generated-acceptance-specs/`
must be regenerated from the updated `.txt` files. The generated specs
will then reference `@atoms/turn-message` and `@atoms/error-message`
instead of the old atom names.

**Rule:** Do not modify acceptance test `.txt` files without explicit
permission. This table is a migration checklist to be executed when
authorized.

## Status: reviewed
