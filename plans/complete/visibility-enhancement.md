# Plan: THEN player map directive for visibility assertions

## Problem
The visibility acceptance test (`acceptanceTests/visibility.txt`) has many repetitive coordinate assertions like:
```
THEN player-map cell [1 0] is not nil.
THEN player-map cell [2 0] is not nil.
... (9 lines for a 3x3 area)
```

## Solution
Add a `THEN player map` directive that accepts an ASCII map (like `GIVEN player map`), builds a test map, and compares visibility (nil vs not-nil) cell-by-cell. The test becomes:
```
THEN player map
  .###.
  .###.
  .###.
```

The generated spec will use `build-test-map` + a new `visibility-mask` helper to compare.

## Changes

### 1. `src/empire/test_utils.cljc` — Add `visibility-mask`
```clojure
(defn visibility-mask [grid]
  (mapv (fn [col] (mapv some? col)) grid))
```
Converts a 2D map to booleans: `true` where cell is non-nil, `false` where nil.

**Test** in `spec/empire/test_utils_spec.clj`: verify mask output for a small map with mix of nil/non-nil cells.

### 2. `src/empire/acceptance/parser.cljc` — Parse `THEN player map`

Add a pre-processing step in `parse-then` that extracts map blocks before clause splitting:

- New function `extract-then-map-blocks` that scans lines for `THEN player map`, collects subsequent `map-row?` lines, and produces IR: `{:type :player-map-visibility :rows [...]}`
- Modify `parse-then` to call this first, then process remaining lines normally.

**Test** in `spec/empire/acceptance/parser_spec.clj`: verify IR output for `THEN player map` with map rows.

### 3. `src/empire/acceptance/generator.cljc` — Generate visibility assertion

- New function `generate-player-map-visibility-then`:
  ```clojure
  (should= (visibility-mask (build-test-map ["..." "..."]))
           (visibility-mask @atoms/player-map))
  ```
- Add `:player-map-visibility` case to `generate-then`
- Add need-rule for `:visibility-mask` that checks for `:player-map-visibility` type in thens
- Add `visibility-mask` to `refers` in `generate-ns-form` when `:visibility-mask` need is present

**Test** in `spec/empire/acceptance/generator_spec.clj`: verify generated code string.

### 4. `acceptanceTests/visibility.txt` — Rewrite tests

| Test | Before | After |
|------|--------|-------|
| Army 3x3 | 9 coordinate assertions | `THEN player map` with 3 rows |
| Unexplored | 3 nil assertions | `THEN player map` with 3 rows (all `.`) |
| City reveals | 9 coordinate assertions | `THEN player map` with 3 rows (all `#`) |
| Enemy hidden | 2 not-nil + 1 nil | `THEN player map` with 1 row (`##....`) |
| Satellite 5x5 | 5 coordinate assertions | `THEN player map` with 5 rows (all `#`) |

### 5. Regenerate pipeline artifacts
- `rm -rf acceptanceTests/edn/*.edn generated-acceptance-specs/acceptance/*.clj`
- `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`

## File list
1. `src/empire/test_utils.cljc`
2. `spec/empire/test_utils_spec.clj`
3. `src/empire/acceptance/parser.cljc`
4. `spec/empire/acceptance/parser_spec.clj`
5. `src/empire/acceptance/generator.cljc`
6. `spec/empire/acceptance/generator_spec.clj`
7. `acceptanceTests/visibility.txt`

## Verification
1. `clj -M:spec spec/empire/test_utils_spec.clj` — unit tests pass
2. `clj -M:spec spec/empire/acceptance/` — parser/generator tests pass
3. Full acceptance pipeline: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`
