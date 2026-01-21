# Phase 2: Pathfinding and Threat Avoidance Implementation Plan

## Goal
Units navigate intelligently using A* pathfinding and avoid death by assessing threats and retreating when damaged.

---

## 2.1 A* Pathfinding

**New file: `src/empire/pathfinding.cljc`**

### Core A* Algorithm

```clojure
(ns empire.pathfinding
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.units.dispatcher :as dispatcher]))

(defn heuristic
  "Manhattan distance heuristic for A*."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn passable?
  "Returns true if unit-type can move through the cell."
  [unit-type cell]
  (and cell
       (not= (:type cell) :unexplored)
       (dispatcher/can-move-to? unit-type cell)))

(defn get-passable-neighbors
  "Returns neighbors that the unit type can traverse."
  [pos unit-type game-map]
  ...)

(defn a-star
  "Finds shortest path from start to goal for unit-type.
   Returns vector of positions from start to goal (inclusive), or nil if no path."
  [start goal unit-type game-map]
  ...)

(defn next-step
  "Returns the next step toward goal, or nil if unreachable.
   This is the main function computer.cljc will call."
  [start goal unit-type]
  ...)
```

### Path Caching

To avoid recalculating paths every turn, cache computed paths:

```clojure
(def path-cache (atom {}))  ; {[start goal unit-type] {:path [...] :computed-round n}}

(defn cached-path
  "Returns cached path if still valid, otherwise computes and caches new path."
  [start goal unit-type]
  ...)

(defn invalidate-cache
  "Clears cache entries involving the given position (called when map changes)."
  [pos]
  ...)

(defn clear-path-cache
  "Clears entire path cache (called at start of each round)."
  []
  (reset! path-cache {}))
```

### A* Implementation Details

Standard A* with priority queue (sorted set or min-heap):

```clojure
(defn a-star [start goal unit-type game-map]
  (let [passable-fn #(passable? unit-type (get-in game-map %))
        neighbors-fn #(filter passable-fn (get-passable-neighbors % unit-type game-map))]
    (loop [open-set (sorted-set-by (fn [a b] (compare (first a) (first b)))
                                   [(heuristic start goal) 0 start [start]])
           closed-set #{}]
      (when-let [[_f g current path] (first open-set)]
        (cond
          (= current goal) path
          (closed-set current) (recur (disj open-set (first open-set)) closed-set)
          :else
          (let [new-closed (conj closed-set current)
                neighbors (remove closed-set (neighbors-fn current))
                new-entries (for [n neighbors
                                  :let [new-g (inc g)
                                        new-f (+ new-g (heuristic n goal))]]
                              [new-f new-g n (conj path n)])]
            (recur (into (disj open-set (first open-set)) new-entries)
                   new-closed)))))))
```

**Note:** The actual implementation should use a proper priority queue. Clojure's sorted-set works for small maps but may need optimization for larger ones.

---

## 2.2 Threat Assessment

**Add to `src/empire/computer.cljc`:**

### Threat Level Calculation

```clojure
(defn- unit-threat
  "Returns threat value for a unit type.
   Higher values = more dangerous."
  [unit-type]
  (case unit-type
    :battleship 10
    :carrier 8
    :destroyer 6
    :submarine 5
    :patrol-boat 3
    :fighter 4
    :army 2
    :transport 1
    0))

(defn threat-level
  "Calculates threat level at position based on nearby enemy units.
   Checks all cells within radius 2 of position.
   Returns sum of threat values for nearby enemy units."
  [computer-map position]
  (let [radius 2
        [px py] position]
    (reduce + 0
      (for [dx (range (- radius) (inc radius))
            dy (range (- radius) (inc radius))
            :let [x (+ px dx)
                  y (+ py dy)
                  cell (get-in computer-map [x y])]
            :when (and cell
                       (:contents cell)
                       (= (:owner (:contents cell)) :player))]
        (unit-threat (:type (:contents cell)))))))
```

### Safe Moves Filter

```clojure
(defn safe-moves
  "Filters moves to avoid high-threat areas when unit is damaged.
   Returns moves sorted by threat level (safest first).
   If unit is at full health, returns all moves unchanged."
  [computer-map position unit possible-moves]
  (let [max-hits (dispatcher/hits (:type unit))
        current-hits (:hits unit max-hits)
        damaged? (< current-hits max-hits)]
    (if damaged?
      ;; Sort by threat level, prefer lower threat
      (sort-by #(threat-level computer-map %) possible-moves)
      possible-moves)))
```

---

## 2.3 Retreat Logic

### Retreat Conditions

Units should retreat when:
1. **Damaged ships/armies:** hits < max-hits
2. **Transports with cargo:** carrying armies (high-value target)
3. **Low fuel fighters:** fuel < distance to nearest base + safety margin

### Retreat Target Selection

```clojure
(defn- find-nearest-friendly-base
  "Finds the nearest computer-owned city or carrier (for fighters)."
  [pos unit-type]
  (let [cities (find-visible-cities #{:computer})]
    (when (seq cities)
      (apply min-key #(distance pos %) cities))))

(defn should-retreat?
  "Returns true if the unit should retreat rather than engage."
  [pos unit computer-map]
  (let [unit-type (:type unit)
        max-hits (dispatcher/hits unit-type)
        current-hits (:hits unit max-hits)
        threat (threat-level computer-map pos)]
    (or
      ;; Damaged and under threat
      (and (< current-hits max-hits) (> threat 3))
      ;; Transport carrying armies - always cautious
      (and (= unit-type :transport)
           (> (:army-count unit 0) 0)
           (> threat 5))
      ;; Severely damaged (< 50% health)
      (< current-hits (/ max-hits 2)))))

(defn retreat-move
  "Returns best retreat move toward nearest friendly city.
   Returns nil if no safe retreat available."
  [pos unit computer-map passable-moves]
  (let [nearest-city (find-nearest-friendly-base pos (:type unit))]
    (when nearest-city
      (let [safe (safe-moves computer-map pos unit passable-moves)]
        (when (seq safe)
          ;; Pick move that's both safe and moves toward base
          (apply min-key #(+ (distance % nearest-city)
                             (* 2 (threat-level computer-map %)))
                 safe))))))
```

### Fighter Return-to-Base Logic

The current `decide-fighter-move` already returns fighters to base when fuel is low. Enhance it to use A* for better pathfinding:

```clojure
(defn decide-fighter-move-v2
  "Enhanced fighter movement using pathfinding."
  [pos fuel]
  (let [adjacent-target (find-adjacent-target pos)
        passable (find-passable-fighter-neighbors pos)
        nearest-city (find-nearest-friendly-city pos)]
    (cond
      adjacent-target adjacent-target
      (empty? passable) nil

      ;; Return to base using A* when fuel is low
      (and nearest-city (<= fuel (+ (distance pos nearest-city) 2)))
      (pathfinding/next-step pos nearest-city :fighter)

      :else (first passable))))
```

---

## 2.4 Integration with Existing Decision Functions

### Modify `decide-army-move`

```clojure
(defn decide-army-move
  "Decides where a computer army should move.
   Enhanced with pathfinding and retreat logic."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-army-target pos)
        passable (find-passable-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target adjacent-target

      ;; Check if should retreat
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid moves
      (empty? passable) nil

      ;; Use pathfinding to reach goals
      :else
      (let [free-cities (find-visible-cities #{:free})
            player-cities (find-visible-cities #{:player})]
        (cond
          (seq free-cities)
          (let [nearest (apply min-key #(distance pos %) free-cities)]
            (or (pathfinding/next-step pos nearest :army)
                (move-toward pos nearest passable)))

          (seq player-cities)
          (let [nearest (apply min-key #(distance pos %) player-cities)]
            (or (pathfinding/next-step pos nearest :army)
                (move-toward pos nearest passable)))

          :else (first passable))))))
```

### Modify `decide-ship-move`

```clojure
(defn decide-ship-move
  "Decides where a computer ship should move.
   Enhanced with pathfinding, threat avoidance, and retreat logic."
  [pos ship-type]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-target pos)
        passable (find-passable-ship-neighbors pos)]
    (cond
      ;; Attack adjacent target
      adjacent-target adjacent-target

      ;; Check if should retreat
      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      ;; No valid moves
      (empty? passable) nil

      ;; Use pathfinding to reach visible enemy
      :else
      (let [player-units (find-visible-player-units)
            safe-passable (safe-moves @atoms/computer-map pos unit passable)]
        (if (seq player-units)
          (let [nearest (apply min-key #(distance pos %) player-units)]
            (or (pathfinding/next-step pos nearest ship-type)
                (move-toward pos nearest safe-passable)))
          ;; Patrol - pick safe random neighbor
          (rand-nth (or (seq safe-passable) passable)))))))
```

---

## 2.5 Files to Modify/Create

| File | Changes |
|------|---------|
| `src/empire/pathfinding.cljc` | **NEW** - A* algorithm, path caching |
| `src/empire/computer.cljc` | Add threat-level, safe-moves, should-retreat?, retreat-move; Update decide-army-move, decide-ship-move, decide-fighter-move |
| `src/empire/atoms.cljc` | (optional) Add path-cache atom if using cross-module caching |
| `src/empire/test_utils.cljc` | Add path-cache reset if added to atoms |
| `src/empire/game_loop.cljc` | Call clear-path-cache at start of round |
| `spec/empire/pathfinding_spec.clj` | **NEW** - Pathfinding tests |
| `spec/empire/computer_spec.clj` | Add threat/retreat tests |

---

## Implementation Order (Test-First)

| Step | Test File | Implementation |
|------|-----------|----------------|
| 1 | `pathfinding_spec.clj` | `heuristic` - Manhattan distance |
| 2 | `pathfinding_spec.clj` | `passable?` - terrain checking |
| 3 | `pathfinding_spec.clj` | `get-passable-neighbors` |
| 4 | `pathfinding_spec.clj` | `a-star` - basic path finding |
| 5 | `pathfinding_spec.clj` | `a-star` - respects terrain (army stays on land, ship on sea) |
| 6 | `pathfinding_spec.clj` | `a-star` - returns nil for unreachable goals |
| 7 | `pathfinding_spec.clj` | `next-step` - returns first step of path |
| 8 | `computer_spec.clj` | `unit-threat` values |
| 9 | `computer_spec.clj` | `threat-level` sums nearby enemies |
| 10 | `computer_spec.clj` | `safe-moves` sorts by threat |
| 11 | `computer_spec.clj` | `should-retreat?` conditions |
| 12 | `computer_spec.clj` | `retreat-move` returns safe move toward base |
| 13 | `computer_spec.clj` | Updated `decide-army-move` uses pathfinding |
| 14 | `computer_spec.clj` | Updated `decide-ship-move` avoids threats |
| 15 | `computer_spec.clj` | Damaged units retreat |
| 16 | `pathfinding_spec.clj` | Path caching (optional, can defer) |

---

## Testing Strategy

### Pathfinding Tests (`spec/empire/pathfinding_spec.clj`)

```clojure
(describe "heuristic"
  (it "returns Manhattan distance"
    (should= 5 (heuristic [0 0] [3 2]))))

(describe "a-star"
  (it "finds direct path on clear terrain"
    (should= [[0 0] [0 1] [0 2]] (a-star [0 0] [0 2] :army test-map)))

  (it "navigates around obstacles"
    ;; Set up map with obstacle, verify path goes around
    )

  (it "keeps armies on land"
    ;; Army path should not cross water
    )

  (it "keeps ships on sea"
    ;; Ship path should not cross land
    )

  (it "returns nil for unreachable goal"
    ;; Army trying to reach island with no land bridge
    ))

(describe "next-step"
  (it "returns first step of computed path"
    ;; Verify it returns position adjacent to start
    ))
```

### Threat Assessment Tests (`spec/empire/computer_spec.clj`)

```clojure
(describe "threat-level"
  (it "returns 0 with no enemies nearby"
    ...)

  (it "sums threat of adjacent enemies"
    ;; Place battleship and destroyer near position
    ;; Verify threat = 10 + 6 = 16
    )

  (it "ignores enemies beyond radius"
    ...))

(describe "should-retreat?"
  (it "returns true when damaged and threatened"
    ...)

  (it "returns true for loaded transport under threat"
    ...)

  (it "returns false for healthy unit"
    ...))

(describe "retreat-move"
  (it "moves toward nearest friendly city"
    ...)

  (it "prefers lower-threat path"
    ...))
```

### Integration Tests

```clojure
(describe "decide-army-move with pathfinding"
  (it "uses A* to navigate around water"
    ;; Army on one side of lake, city on other
    ;; Verify it takes path around lake
    ))

(describe "decide-ship-move with threat avoidance"
  (it "avoids moving next to enemy battleship when damaged"
    ...))
```

---

## Edge Cases to Handle

1. **No path exists:** A* returns nil - fall back to greedy move-toward
2. **All moves are threatening:** Pick least threatening option
3. **Trapped unit:** No passable neighbors - return nil (unit stays put)
4. **Fighter at exact fuel distance:** Include 1-2 step buffer for safety
5. **Path blocked by friendly unit:** Path around, don't attack own units
6. **Carrier as retreat target:** Fighters should consider carriers as valid bases

---

## Performance Considerations

1. **A* on large maps:** May need optimization (better priority queue, early termination)
2. **Cache invalidation:** Only invalidate paths through changed cells
3. **Limit path length:** Don't compute paths longer than 20-30 steps
4. **Batch pathfinding:** Compute paths at round start rather than per-move

For Phase 2, simple A* without heavy optimization should suffice. Performance tuning can happen in later phases if needed.
