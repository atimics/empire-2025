# Module Restructuring Recommendations

## File Size Overview

| File | Lines | Issue |
|------|------:|-------|
| `ui/input.cljc` | 515 | Largest file, 17 requires, mixes Quil with game logic |
| `game_loop.cljc` | 485 | 17 requires, orchestrates everything |
| `movement/movement.cljc` | 399 | 11 requires, handles all unit types |
| `computer/transport.cljc` | 295 | Large but focused on one concern |
| `movement/wake_conditions.cljc` | 208 | Long nested conditionals |
| `pathfinding.cljc` | 202 | Standalone, well-contained |

42 other files are under 200 lines and generally well-structured.

---

## 1. Break Up ui/input.cljc (515 lines, High Priority)

This file mixes Quil event handling with pure game logic. Per CLAUDE.md, Quil-independent functions should be extracted.

**Current responsibilities mixed together:**
- Quil keyboard/mouse event capture
- Player command dispatch (movement keys, production keys, backtick commands)
- Container operations (wake/sleep armies, launch fighters)
- Movement order logic (explore, sentry, march)
- Production selection
- Combat initiation

**Proposed split:**
- `ui/input.cljc` (~100 lines) — Thin Quil event handlers that translate raw key/mouse events into game actions. Only file that requires `quil.core`.
- `player/commands.cljc` (~200 lines) — Pure command dispatch: given a key and game state, produce a game action. No Quil, no atoms.
- `player/orders.cljc` (~150 lines) — Unit order logic: explore, sentry, march, wake/sleep containers. Currently scattered across input.cljc.

**Benefit:** The bulk of input logic becomes testable without Quil.

---

## 2. Break Up game_loop.cljc (485 lines, High Priority)

This file is the central orchestrator with 17 requires. It handles round start, unit movement, visibility, production, cleanup, and attention building.

**Proposed split:**
- `game_loop.cljc` (~120 lines) — Round orchestration only: `start-new-round`, `advance-game`, `update-map`. Calls into submodules.
- `game_loop/round_setup.cljc` (~100 lines) — Round initialization: satellite moves, fuel consumption, sentry waking, dead unit removal, repair, step resets.
- `game_loop/item_processing.cljc` (~150 lines) — `process-one-player-item`, `process-one-computer-item`, movement execution with sidestep logic.

**Benefit:** Each file has a clear single responsibility. The orchestrator becomes a readable sequence of calls.

---

## 3. Consolidate Distance Functions (High Priority, Low Risk)

Six identical Manhattan distance implementations exist (documented in source-code-improvements.md). This is also a token efficiency issue — an LLM reading any computer AI module encounters another copy.

**Current:** `computer/core.cljc`, `computer/threat.cljc`, `computer/ship.cljc`, `computer/fighter.cljc`, `pathfinding.cljc`, `computer/continent.cljc` (inline).

**Proposed:** Single `core/distance` used everywhere. The private `distance-to` variants in `ship.cljc` and `fighter.cljc` already require `core`, so just replace them.

---

## 4. Consolidate Simple Unit Modules (Medium Priority)

Four ship units are nearly identical — 25 lines each with the same structure:

```
patrol_boat.cljc  — 25 lines: speed, cost, hits, display-char, visibility-radius, can-move-to?, needs-attention?
destroyer.cljc    — 25 lines: same structure
submarine.cljc    — 25 lines: same structure
battleship.cljc   — 25 lines: same structure
```

**Option A:** Merge into `units/ships.cljc` (~60 lines). Each ship becomes a map entry rather than a full namespace. Dispatcher reads from data rather than dispatching to 4 separate namespaces.

**Option B:** Keep separate files (current). They're small and follow a clear pattern.

Option A saves 4 files and ~40 lines, and reduces dispatcher boilerplate. Dispatcher currently has 9 `case` branches repeated across 10+ functions — data-driven dispatch would collapse that.

---

## 5. Extract Quil Bridge (Medium Priority)

`ui/input.cljc` and `ui/rendering.cljc` both require `quil.core` directly. CLAUDE.md says to isolate Quil dependencies.

**Proposed:** `ui/quil_bridge.cljc` — wraps all Quil calls (mouse position, key state, drawing primitives) behind plain Clojure functions. The rest of UI code never imports Quil directly.

**Benefit:** Enables testing rendering logic and input translation without a Quil runtime.

---

## 6. Move `player/combat.cljc` to Shared Location (Low Priority)

`player/combat.cljc` is in the player directory but is used by computer AI modules (`computer/core.cljc`, `computer/fighter.cljc`, `computer/ship.cljc`). It's the shared combat rules engine, not player-specific.

**Proposed:** Move to `rules/combat.cljc` or root level `combat.cljc`.

---

## 7. Move `pathfinding.cljc` to `movement/` (Low Priority)

Pathfinding is only used by computer AI modules and movement. It sits at the root level alongside `atoms.cljc` and `config.cljc`. Logically it belongs in `movement/` or `computer/`.

---

## Token Efficiency Notes

**Biggest wins for AI readability:**
- Breaking up 500-line files means the LLM can read only the relevant 100-150 line module instead of the entire file.
- Consolidating distance functions eliminates 5 redundant definitions that inflate every computer AI file.
- Data-driven unit dispatch (item 4) would replace ~100 lines of repetitive `case` statements with a single map lookup.

**Already well-structured (no changes needed):**
- `containers/helpers.cljc` — Pure, focused
- `movement/map_utils.cljc` — Clean utilities
- `movement/visibility.cljc` — Single concern
- `ui/coordinates.cljc` — Exemplary pure module (16 lines)
- `computer/threat.cljc` — Focused single concern
- All individual unit modules — Config-like, minimal logic
