# Spec Refactoring Plan

## Overview

43 spec files were reviewed. Most specs (60%+) already use `build-test-map` well. The main opportunities are:

1. Replacing manual map construction with `build-test-map` in a handful of files
2. Reducing visual noise from large blank maps in movement specs
3. Eliminating repetitive `player-map` setup

---

## 1. Manual Map Construction → `build-test-map`

### continent_spec.clj (lines 30-31, 50, 65-68, 107-108)

Four tests construct maps with inline cell maps instead of `build-test-map`. The reason: they need unexplored cells adjacent to land, and `build-test-map` already supports `.` or `-` for nil/unexplored.

**Current** (line 30-31):
```clojure
(reset! atoms/computer-map [[{:type :land} nil {:type :land}]
                             [{:type :land} nil {:type :land}]])
```

**Proposed**:
```clojure
(reset! atoms/computer-map (build-test-map ["#.#"
                                             "#.#"]))
```

This already works — `.` maps to `nil` in `char->cell`. These four tests can be converted directly.

**Affected tests:**
- "marks but does not expand through unexplored territory" (line 28)
- "finds isolated landmass when separated by unexplored" (line 48)
- "counts unexplored cells" (lines 65-68) — both `computer-map` and `game-map`
- "finds nearest unexplored cell" (lines 107-108)

### rendering_util_spec.clj (lines 152-191)

Seven tests in `group-cells-by-color` construct maps with inline cell maps.

**Current** (line 152-153):
```clojure
(let [the-map [[{:type :land} {:type :sea}]
               [{:type :land} {:type :sea}]]
```

**Proposed**:
```clojure
(let [the-map (build-test-map ["#~"
                                "#~"])
```

All seven can be converted. The city test (line 176) uses `{:type :city :city-status :player}` which maps to `O` in `build-test-map`.

**Note**: `rendering_util_spec.clj` does not currently require `test-utils`. It would need to add the require.

---

## 2. Blank Map Noise in Movement Specs

### movement_spec.clj — repeated 9x9 blank maps

Every test constructs a 9x9 map where only 2-3 cells are non-blank. The test's intent is buried in 9 lines of dashes.

**Current** (lines 24-32, repeated ~15 times):
```clojure
(reset! atoms/game-map (build-test-map ["---------"
                                         "---------"
                                         "---------"
                                         "---------"
                                         "----A#---"
                                         "---------"
                                         "---------"
                                         "---------"
                                         "---------"]))
```

**Proposed**: Add a helper to `test_utils.cljc`:
```clojure
(defn build-sparse-test-map
  "Builds a rows x cols map of unexplored cells, then overlays specific cells.
   overlays is a map of [row col] -> character."
  [rows cols overlays]
  (let [base (vec (repeat rows (vec (repeat cols nil))))]
    (reduce (fn [m [[r c] ch]]
              (assoc-in m [r c] (char->cell ch)))
            base overlays)))
```

Then tests become:
```clojure
(reset! atoms/game-map (build-sparse-test-map 9 9 {[4 4] \A [4 5] \#}))
```

This makes the test's intent — "army at [4,4], land at [4,5], everything else unexplored" — immediately clear instead of buried in a wall of dashes.

### movement_spec.clj — repeated blank player-map setup

Almost every test also resets `player-map` to a fully blank 9x9 map. This is pure boilerplate.

**Current** (lines 33-41, 59-67, 84-92, 109-117, repeated ~12 times):
```clojure
(reset! atoms/player-map (build-test-map ["---------"
                                           "---------"
                                           ...
                                           "---------"]))
```

**Proposed**: Move to the `(before)` block or use `make-initial-test-map`:
```clojure
(before
  (reset-all-atoms!)
  (reset! atoms/player-map (make-initial-test-map 9 9 nil)))
```

This eliminates ~100 lines of repeated blank player-map construction.

---

## 3. Summary of Changes

| File | Change | Lines Saved (approx) |
|------|--------|---------------------|
| `src/empire/test_utils.cljc` | Add `build-sparse-test-map` helper; make `char->cell` public | +10 |
| `spec/empire/computer/continent_spec.clj` | Convert 4 tests from inline maps to `build-test-map` | ~10 |
| `spec/empire/ui/rendering_util_spec.clj` | Convert 7 tests from inline maps to `build-test-map`; add require | ~15 |
| `spec/empire/movement/movement_spec.clj` | Use `build-sparse-test-map` for game-map; move blank player-map to `before` block | ~150 |

### Files NOT recommended for changes

- **repair_spec.clj**: The inline maps (e.g. `{:type :city :shipyard [...]}`) test shipyard helpers that operate on individual cell maps, not full game maps. `build-test-map` doesn't apply here.
- **init_spec.clj**: Tests numeric smoothing arrays, not game maps.
- **units/*_spec.clj**: Test individual unit config functions with simple maps. No game map construction involved.
- **containers/helpers_spec.clj**: Tests unit container math on individual unit maps, not full maps.
