# Dead Code Report

Post-merge with master (commit f4d3651).

## Dead Functions/Vars (13)

### `src/empire/ui/coordinates.cljc` — 7 of 8 functions dead

Only `screen->cell` is used (from `debug.cljc`). These 7 are never referenced:

- `in-map-bounds?` (line 5)
- `cell->screen` (line 25)
- `cell-center->screen` (line 33)
- `adjacent?` (line 42)
- `chebyshev-distance` (line 50)
- `manhattan-distance` (line 56)
- `extend-to-edge` (line 61)

### Other dead functions

| File | Line | Name | Notes |
|------|------|------|-------|
| `src/empire/computer/core.cljc` | 138 | `army-should-board-transport?` | Never called anywhere |
| `src/empire/units/dispatcher.cljc` | 15 | `unit-modules` | Vestige — `initial-state` uses `case` instead |
| `src/empire/movement/map_utils.cljc` | 12 | `get-cell` | Never referenced |
| `src/empire/movement/map_utils.cljc` | 19 | `set-cell` | Never referenced |
| `src/empire/movement/movement.cljc` | 15 | `is-players?` | Duplicate of private version in `visibility.cljc` |
| `src/empire/ui/input.cljc` | 456 | `debug-drag-cancel!` | Never called |

## Dead Atom (1)

| File | Line | Name | Notes |
|------|------|------|-------|
| `src/empire/atoms.cljc` | 123 | `used-unloading-beaches` | Defined and reset in test_utils but never read/written |

## Unused Requires (3)

| File | Unused Require |
|------|----------------|
| `src/empire/profiling.cljc` | `clojure.java.io` (alias `io`) |
| `src/empire/computer/threat.cljc` | `empire.movement.map-utils` (alias `map-utils`) |
| `src/empire/ui/rendering.cljc` | `empire.debug` (alias `debug`) |
