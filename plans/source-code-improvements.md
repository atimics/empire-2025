# Source Code Improvement Recommendations

## 1. Manhattan Distance Duplication (High Priority)

The same Manhattan distance calculation is implemented 6 times across the codebase:

| Location | Name | Visibility |
|----------|------|-----------|
| `computer/core.cljc:14` | `distance` | public |
| `computer/threat.cljc:77` | `distance` | public |
| `computer/ship.cljc:69` | `distance-to` | private |
| `computer/fighter.cljc:64` | `distance-to` | private |
| `pathfinding.cljc:16` | `heuristic` | public |
| `computer/continent.cljc:133` | inline in `min-key` | inline |

Additionally, `computer/transport.cljc:50` has the same inline formula in `find-nearest-army`.

All are identical: `(+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1)))`.

**Recommendation:** Keep `core/distance` as the single source. Replace all others with calls to `core/distance`. The private versions in `ship.cljc` and `fighter.cljc` already require `core`, so no new dependencies needed.

---

## 2. Direction/Signum Calculation Duplication (Medium Priority)

Two patterns for computing step direction toward a target:

**Pattern A — manual cond** (2 locations):
- `movement/movement.cljc:24-29` in `next-step-pos`
- `containers/ops.cljc:172-173` in `launch-fighter-from-carrier`

```clojure
dx (cond (zero? (- tx x)) 0 (pos? (- tx x)) 1 :else -1)
```

**Pattern B — Integer/signum** (4 locations):
- `units/satellite.cljc:44-45`
- `units/satellite.cljc:92-93`
- `movement/satellite.cljc:21-22`
- `movement/satellite.cljc:78-79`

```clojure
dx (Integer/signum (- tx ux))
```

These are equivalent. `Integer/signum` is clearer.

**Recommendation:** Standardize on `Integer/signum`. Or extract a shared `step-direction` function to `map_utils.cljc`:
```clojure
(defn step-toward [[x y] [tx ty]]
  [(+ x (Integer/signum (- tx x)))
   (+ y (Integer/signum (- ty y)))])
```

---

## 3. Dead Production Code: `is-computers?` in movement.cljc (High Priority)

**File:** `movement/movement.cljc:15-19`

```clojure
(defn is-computers? [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))
```

This public function is never called by any production code. A private duplicate exists in `visibility.cljc:10-14` and is the one actually used at runtime. The public version is only exercised by its own spec (`movement_spec.clj:608-627`).

**Recommendation:** Delete from `movement.cljc`. Delete the tests in `movement_spec.clj:608-627`. The private version in `visibility.cljc` covers the runtime need.

---

## 4. `chebyshev-distance` Uses Bare `abs` (Low Priority)

**File:** `movement/movement.cljc:35`

```clojure
(max (abs (- x2 x1)) (abs (- y2 y1)))
```

Every other file uses `Math/abs`. The bare `abs` works (it's in `clojure.core`) but is inconsistent with the rest of the codebase.

**Recommendation:** Change to `Math/abs` for consistency.

---

## 5. Magic Numbers in game_loop.cljc (Medium Priority)

| Line | Value | Meaning |
|------|-------|---------|
| `63` | `10` | Max consecutive sidesteps before giving up |
| `162` | `1` | Fighter fuel decrement per sentry turn |

These are behavioral constants embedded in function signatures/logic.

**Recommendation:** Move to `config.cljc` as named constants (e.g., `max-sidesteps`, `sentry-fuel-cost`).

---

## 6. Magic Numbers in init.cljc (Medium Priority)

| Line | Value | Meaning |
|------|-------|---------|
| `126` | `1000` | Max placement attempts for free cities |
| `139` | `1000` | Max placement attempts for starting armies |
| `155` | `10` | Minimum surrounding land cells for city placement |

**Recommendation:** Move to `config.cljc`.

---

## Summary

| # | Issue | Severity | Files | Type |
|---|-------|----------|-------|------|
| 1 | Manhattan distance duplicated 6x | High | 6 files | Duplication |
| 2 | Direction calculation duplicated 6x | Medium | 4 files | Duplication |
| 3 | `is-computers?` dead in movement.cljc | High | 1 file + spec | Dead code |
| 4 | `abs` vs `Math/abs` inconsistency | Low | 1 file | Consistency |
| 5 | Magic numbers in game_loop.cljc | Medium | 1 file | Readability |
| 6 | Magic numbers in init.cljc | Medium | 1 file | Readability |
