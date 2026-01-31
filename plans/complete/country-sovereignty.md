# Plan: Country Sovereignty

## Summary

Computer armies respect territorial boundaries. An army with country-id C cannot move into a land cell stamped with country-id D (where D != C). This prevents armies from one computer "country" from trespassing through another country's territory, creating meaningful borders on the map.

## Rule

A computer army is **blocked by foreign territory** when ALL of these are true:

1. The army has a non-nil `:country-id`
2. The target land cell has a non-nil `:country-id`
3. The two country-ids differ

**Passable** when any of these hold:

- The army has no `:country-id` (newly unloaded / pre-conquest armies are free to roam)
- The target cell has no `:country-id` (unclaimed land is open to all)
- The country-ids match (own territory)
- The target cell is a **city** (armies must be able to approach and conquer free/player cities regardless of surrounding territory stamps)

## Affected Code Paths

### 1. Immediate Movement — `computer/army.cljc`

**`get-passable-neighbors`** (line 44) — Currently checks `#{:land :city}`. Add sovereignty filter: exclude land cells with a foreign country-id. Cities remain passable.

**`get-empty-passable-neighbors`** (line 54) — Calls `get-passable-neighbors`, so inherits the filter automatically.

These two functions feed into:
- `explore-randomly` — random neighbor selection
- `coast-walk-candidates` — coastline following
- `move-toward-objective` — fallback neighbors when preferred step is blocked
- `find-and-board-transport` — moving toward loading transport

All of these gain sovereignty awareness without further changes.

### 2. Pathfinding — `pathfinding.cljc`

**`passable?`** (line 31) — Currently checks terrain only via `dispatcher/can-move-to?`. It has no access to the moving army's country-id.

**Problem**: `next-step` takes `[start goal unit-type]` — no army identity. If pathfinding ignores sovereignty, it computes paths through foreign territory, and the army refuses each step, getting stuck.

**Solution**: Add an optional `passability-fn` parameter to `next-step` and `a-star`. When provided, use it instead of the default `passable?`. The computer army module passes a sovereignty-aware passability function that closes over the army's country-id.

```clojure
;; In pathfinding.cljc:
(defn a-star
  ([start goal unit-type game-map]
   (a-star start goal unit-type game-map nil))
  ([start goal unit-type game-map passability-fn]
   ;; Use (or passability-fn (partial passable? unit-type)) for cell checks
   ...))

(defn next-step
  ([start goal unit-type]
   (next-step start goal unit-type nil))
  ([start goal unit-type passability-fn]
   ;; Pass passability-fn through to a-star
   ...))
```

**Cache key**: When `passability-fn` is provided, include the army's country-id in the cache key: `[start goal unit-type country-id]`. Armies of the same country share cached paths since they have identical passability constraints.

```clojure
;; In computer/army.cljc:
(defn- sovereignty-passable? [country-id cell]
  (and cell
       (not= (:type cell) :unexplored)
       (dispatcher/can-move-to? :army cell)
       (or (nil? country-id)
           (= :city (:type cell))
           (nil? (:country-id cell))
           (= country-id (:country-id cell)))))
```

### 3. Land Objective Selection — `computer/army.cljc`

**`find-land-objective`** (line 103) — Uses `continent/find-all-objectives-on-continent` which returns free/player cities and unexplored cells. No change needed: cities are always approachable, and unexplored cells won't have country-ids. However, the A* path to the objective will respect sovereignty, so if an objective is unreachable due to foreign territory, pathfinding returns nil and the army falls through to the next priority.

### 4. Attack — No Change

**`find-adjacent-enemy`** (line 63) — Priority 1 in `process-army`. Attacks happen before movement checks and bypass passability entirely. An army standing at the border of foreign territory can still attack an adjacent enemy.

### 5. Transport Boarding — No Change to Core Logic

**`find-and-board-transport`** (line 137) — Uses `move-toward-objective` which already uses `get-empty-passable-neighbors` (gains sovereignty filter) and `pathfinding/next-step` (will use sovereignty-aware passability). If a transport is in foreign territory waters, the army simply can't reach it — falls through to explore.

### 6. Coast Walk — Inherits Filter

**`coast-walk-candidates`** (line 161) — Calls `get-empty-passable-neighbors`. Gains sovereignty awareness automatically. A coast-walking army that hits a foreign territory boundary will run out of candidates and terminate coast-walk (switching to explore mode).

### 7. Safety Check — `computer/core.cljc`

**`move-unit-to`** (line 64) — Add a sovereignty check as defense in depth. If the moving unit is a computer army with a country-id, and the target land cell has a different country-id, return nil (move refused). This catches any code path that bypasses the army module's filtering.

## Edge Cases

### Newly Produced Armies
Armies produced at a computer city inherit the city's country-id (existing behavior in production). They start in their own territory and respect borders from the first move.

### Unloaded Armies (Transport Invasion)
Armies unloaded from transports have `:unload-event-id` but no `:country-id` until they conquer a city and mint a new country. These armies are **exempt** from sovereignty (rule condition 1 fails) — they can move freely through any territory to find and conquer a city.

### Army Surrounded by Foreign Territory
An army whose country is enclosed by foreign territory will have no passable neighbors (except possibly cities). It will attempt each priority in order and ultimately do nothing. This is correct behavior — sovereignty means borders matter. In practice this is unlikely: territory is stamped as armies move, so an army's territory generally connects back to its city.

### Territory Gaps
Unclaimed land cells (no country-id) between two countries act as neutral corridors. Any army can traverse them. Territory is only stamped as armies walk through, so early-game maps have large unclaimed areas.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/army.cljc` | Add `sovereignty-passable?`. Modify `get-passable-neighbors` to accept army's country-id and filter foreign territory. Thread country-id through `move-toward-objective`, `explore-randomly`, `coast-walk-candidates`, `find-and-board-transport`, `process-army`. |
| `src/empire/movement/pathfinding.cljc` | Add optional `passability-fn` parameter to `a-star`, `next-step`, and `get-passable-neighbors`. Include country-id in cache key when provided. |
| `src/empire/computer/core.cljc` | Add sovereignty check in `move-unit-to` as defense-in-depth. |
| `spec/empire/computer/army_spec.clj` | Tests: army blocked by foreign territory, army passes through own territory, army passes through unclaimed land, army with no country-id passes through any territory, army can still approach cities in foreign territory, coast-walk terminates at sovereignty boundary. |
| `spec/empire/movement/pathfinding_spec.clj` | Tests: sovereignty-aware passability function, cache key includes country-id, path avoids foreign territory. |

## Key Design Decisions

### Cities exempt from sovereignty
Armies must be able to pathfind to and approach enemy/free cities regardless of whose territory they sit in. Without this exemption, a city deep in foreign territory becomes unreachable and unconquerable.

### Unloaded armies exempt
Transported armies are the mechanism for establishing new countries on foreign continents. They must be free to move until they conquer a city and receive a country-id.

### Defense in depth
The sovereignty check appears in three layers: (1) neighbor filtering in army.cljc, (2) pathfinding passability, (3) move-unit-to safety check. The first two prevent wasted computation; the third catches bugs.

### Pathfinding uses closure, not global state
The sovereignty-aware passability function is passed as a closure rather than reading the army's country-id from global state. This keeps pathfinding.cljc decoupled from computer AI concepts and allows the cache to work correctly with different country-ids.
