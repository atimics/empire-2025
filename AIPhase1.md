# Phase 1: Basic Infrastructure Implementation Plan

## Goal
Computer units move toward objectives each round. Computer cities produce units.

---

## 1.1 Add Computer Turn State Management

**File: `src/empire/atoms.cljc`**

Add two new atoms:
```clojure
(def computer-items (atom []))   ; List of computer units/cities to process
(def computer-turn (atom false)) ; Flag: true when processing computer turn
```

**File: `src/empire/test_utils.cljc`**

Update `reset-all-atoms!` to include:
```clojure
(reset! atoms/computer-items [])
(reset! atoms/computer-turn false)
```

---

## 1.2 Build Computer Items List

**File: `src/empire/game_loop.cljc`**

New function mirroring `build-player-items`:
```clojure
(defn build-computer-items []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :computer)
                  (= (:owner (:contents cell)) :computer))]
    [i j]))
```

---

## 1.3 Integrate Computer Turn into Round Progression

**File: `src/empire/game_loop.cljc`**

### Modify `start-new-round`
Build both player and computer items:
```clojure
(reset! atoms/player-items (vec (build-player-items)))
(reset! atoms/computer-items (vec (build-computer-items)))
```

### Modify `advance-game`
New flow:
1. If paused → do nothing
2. If player-items empty AND computer-items empty → start new round (pause check here)
3. If player-items not empty → process player items (existing logic)
4. Else → process computer items

```clojure
(defn advance-game []
  (cond
    @atoms/paused nil

    (and (empty? @atoms/player-items) (empty? @atoms/computer-items))
    (if @atoms/pause-requested
      (do (reset! atoms/paused true) (reset! atoms/pause-requested false))
      (start-new-round))

    @atoms/waiting-for-input nil

    (seq @atoms/player-items)
    (loop [processed 0]
      ;; existing player processing logic
      ...)

    :else  ; computer-items not empty
    (process-computer-items)))
```

### New function `process-computer-items`
- No waiting for input (computer decides instantly)
- Uses computer decision functions
- Processes multiple items per frame (like player processing)

---

## 1.4 Create Computer Decision Module

**New file: `src/empire/computer.cljc`**

```clojure
(ns empire.computer
  (:require [empire.atoms :as atoms]
            [empire.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.production :as production]))

(defn decide-army-move [computer-map position]
  "Decide where an army should move. Returns target coords or nil.")

(defn decide-ship-move [computer-map position unit-type]
  "Decide where a ship should move. Returns target coords or nil.")

(defn decide-fighter-move [computer-map position fuel]
  "Decide where a fighter should move. Returns target coords or nil.")

(defn decide-production [computer-map city-position]
  "Decide what a city should produce. Returns unit type keyword.")

(defn process-computer-unit [position]
  "Process one computer unit's turn. Executes movement/attack.")

(defn process-computer-city [position]
  "Process one computer city. Sets production if needed.")
```

---

## 1.5 Simple Movement Heuristics (Phase 1 Only)

### Armies
1. If adjacent to attackable target (player unit or city) → attack
2. If free city visible on computer-map → move toward nearest
3. If player city visible → move toward nearest
4. Else → move toward unexplored area (pick random passable neighbor)

### Ships
1. If adjacent to attackable target → attack
2. Move toward nearest enemy unit/city visible
3. Patrol randomly if nothing visible

### Fighters
1. If adjacent to attackable target → attack
2. If fuel > distance to nearest friendly base → explore toward unexplored
3. Return to nearest city/carrier when fuel low

### Production
- Simple heuristic: always produce armies
- (Later phases add naval units at coastal cities, counter-strategy, etc.)

**Note:** `update-production` already handles computer cities correctly - it uses `:city-status` as the unit owner (line 24-27 in production.cljc). We just need to call `set-city-production` for computer cities.

---

## Implementation Order (Test-First)

| Step | Test File | Implementation |
|------|-----------|----------------|
| 1 | `computer_spec.clj` | Add atoms + reset |
| 2 | `computer_spec.clj` | `build-computer-items` |
| 3 | `computer_spec.clj` | `decide-army-move` basic cases |
| 4 | `computer_spec.clj` | `process-computer-unit` for armies |
| 5 | `game_loop_spec.clj` | Integrate into `advance-game` |
| 6 | `computer_spec.clj` | Ship/fighter decisions |
| 7 | `computer_spec.clj` | `decide-production` and `process-computer-city` |

---

## Files to Modify/Create

| File | Changes |
|------|---------|
| `src/empire/atoms.cljc` | Add 2 atoms |
| `src/empire/test_utils.cljc` | Update `reset-all-atoms!` |
| `src/empire/game_loop.cljc` | Add `build-computer-items`, modify `start-new-round`, `advance-game`, add `process-computer-items` |
| `src/empire/computer.cljc` | **NEW** - All AI decision logic |
| `spec/empire/computer_spec.clj` | **NEW** - All Phase 1 tests |

---

## Testing Strategy

**Phase 1 Tests (`spec/empire/computer_spec.clj`):**
- `build-computer-items` returns computer cities and units
- `decide-army-move` returns valid adjacent cell
- `decide-army-move` prioritizes free cities over exploration
- `decide-army-move` attacks adjacent enemies
- `decide-ship-move` returns valid sea cell
- `decide-fighter-move` returns to base when fuel low
- `decide-production` returns valid unit type
- `process-computer-unit` updates game-map correctly
- `process-computer-city` sets production when none exists
- Computer turn processes all computer units
- Round progression includes computer turn after player turn
