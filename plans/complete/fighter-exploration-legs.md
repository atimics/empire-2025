# Plan: Fighter Exploration Legs

## Summary

When a computer fighter is ready to launch from a refueling site, a random roll determines the flight type: 50% regular leg (fly between refueling sites with mild unexplored preference), 50% exploration. Of exploration flights, 1 in 20 is a drone (flies into unexplored territory until fuel runs out and dies). The other 19 in 20 are exploration sorties — fly 16 steps straight into unexplored territory, then return to origin. Exploration paths maximize coverage of unexplored cells.

## Flight Types

### Regular Leg (50%)

Existing leg-based coverage between refueling sites. Enhancement: when fuel margin exists, allow unexplored cells up to 1 cell farther from target than current position (`<= (inc direct-dist)` instead of `<= direct-dist`). Falls back to direct flight when no unexplored neighbors qualify or fuel is tight.

### Exploration Sortie (50% * 19/20 = 47.5%)

The fighter does not choose a target refueling site. Instead:

1. **Pick heading**: From the current refueling site, evaluate each of the 8 compass directions. For each direction, project 16 cells and count how many cells in the projection (plus their visibility-radius neighbors) are unexplored on the computer-map. Pick the direction with the highest unexplored count. Break ties randomly.

2. **Outbound (16 steps)**: Fly 16 steps. Each step, among unoccupied passable neighbors, pick the one that:
   - Is closest to the projected endpoint (16 cells out in the chosen direction)
   - Among ties, has the most unexplored neighbors on the computer-map

   This keeps the path generally straight while jinking to maximize fog revelation.

3. **Return (up to 16 steps)**: After 16 outbound steps, the fighter's target becomes its origin refueling site. It flies back using `navigate-toward-target` with regular-leg behavior (direct flight, mild unexplored preference). With 16 fuel remaining and 16 steps of distance, it arrives with a small margin.

4. **On return**: `handle-arrival` processes normally — record leg (origin to origin is a no-op for leg records), refuel, choose next flight.

### Drone (50% * 1/20 = 2.5%)

Expendable reconnaissance. The fighter flies into unexplored territory until fuel runs out:

1. **Pick heading**: Same as exploration sortie — evaluate 8 compass directions, pick the one with the most unexplored cells along the full 32-cell projection.

2. **Fly until death**: Each step, among unoccupied passable neighbors, pick the one with the most unexplored neighbors on the computer-map. Among ties, prefer the one closest to the projected endpoint. No fuel reservation — the fighter burns all 32 fuel.

3. **Death**: When fuel hits 0, the fighter is removed (existing `consume-fighter-fuel` behavior). No return, no arrival handling.

## Maximizing Unexplored Coverage

### Heading Selection

```clojure
(defn- count-unexplored-along-direction
  "Count unexplored cells (and their visibility neighbors) along a direction for n steps."
  [start direction n]
  (let [comp-map @atoms/computer-map]
    (reduce (fn [count step]
              (let [pos (mapv + start (mapv * direction (repeat step)))]
                (+ count
                   (count (filter #(nil? (get-in comp-map %))
                                  (cons pos (core/get-neighbors pos)))))))
            0
            (range 1 (inc n)))))
```

Evaluate all 8 directions: `[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]`. Pick the direction with the highest count. The projection length is 16 for sorties, 32 for drones.

### Step Selection — Exploration & Drone

At each step, score unoccupied passable neighbors by unexplored-neighbor count:

```clojure
(defn- count-unexplored-neighbors
  "Count how many of pos's neighbors are unexplored on computer-map."
  [pos]
  (count (filter #(nil? (get-in @atoms/computer-map %))
                 (core/get-neighbors pos))))
```

Pick the neighbor with the highest score. Among ties, pick the one closest to the projected endpoint (keeps the path moving forward rather than circling). This greedy approach naturally follows the boundary of explored and unexplored territory, sweeping the maximum fog per step.

## Implementation

### New Fields on Fighter Unit

- `:flight-mode` — `:regular`, `:explore`, or `:drone`. Set at launch, cleared on arrival/death.
- `:explore-origin` — Position of the refueling site the exploration launched from. Used as return target after 16 outbound steps.
- `:explore-heading` — `[dr dc]` direction vector chosen at launch.
- `:explore-steps-remaining` — Counts down from 16 during outbound leg. When 0, switch to return.

Existing fields `:flight-target-site` and `:flight-origin-site` are used for regular legs (unchanged) and for the return phase of exploration sorties.

### Modified: `ensure-flight-target`

Roll the dice when a fighter is at a refueling site with no target:

```clojure
(defn- ensure-flight-target [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))]
    (when (and unit (nil? (:flight-target-site unit)) (nil? (:flight-mode unit)))
      (when-let [site-pos (current-refueling-site pos)]
        (if (< (rand) 0.5)
          ;; Regular leg
          (when-let [target (choose-leg site-pos)]
            (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
            (swap! atoms/game-map update-in (conj pos :contents)
                   assoc :flight-target-site target
                         :flight-origin-site site-pos
                         :flight-mode :regular))
          ;; Exploration or drone
          (let [drone? (< (rand) 0.05)
                heading (best-exploration-heading site-pos (if drone? 32 16))
                endpoint (mapv + site-pos (mapv * heading (repeat (if drone? 32 16))))]
            (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
            (swap! atoms/game-map update-in (conj pos :contents)
                   assoc :flight-mode (if drone? :drone :explore)
                         :explore-origin site-pos
                         :explore-heading heading
                         :explore-steps-remaining (if drone? nil 16)
                         :flight-target-site endpoint)))))))
```

### New: `best-exploration-heading`

```clojure
(defn- best-exploration-heading
  "Evaluate 8 compass directions and return the one with the most unexplored cells."
  [pos projection-length]
  (let [directions [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
        scored (map (fn [dir]
                      [dir (count-unexplored-along-direction pos dir projection-length)])
                    directions)
        best-score (apply max (map second scored))
        best (filter #(= best-score (second %)) scored)]
    (first (rand-nth (vec best)))))
```

### Modified: `move-fighter-once`

Add exploration and drone handling. The priority chain becomes:

1. **Attack adjacent enemy** (unchanged — all flight types)
2. **Drone or exploration step** (new — when `:flight-mode` is `:explore` or `:drone`)
3. **Arrived at target** (unchanged — regular legs and exploration return)
4. **Low fuel return** (unchanged — regular legs and exploration return)
5. **Navigate toward target** (unchanged — regular legs and exploration return)
6. **Patrol** (unchanged — fallback)

```clojure
;; After Priority 1 (attack), before existing Priority 2:
(and (= :explore (:flight-mode unit)) (pos? (:explore-steps-remaining unit 0)))
(explore-step pos unit)

(= :drone (:flight-mode unit))
(drone-step pos unit)
```

### New: `explore-step`

```clojure
(defn- explore-step
  "One outbound exploration step. Maximizes unexplored coverage toward heading."
  [pos unit]
  (let [endpoint (:flight-target-site unit)
        passable (get-passable-neighbors pos)
        candidates (filter (complement occupied?) passable)
        scored (map (fn [n] [n (count-unexplored-neighbors n)]) candidates)
        best-score (apply max 0 (map second scored))
        best (map first (filter #(= best-score (second %)) scored))
        target (when (seq best)
                 (apply min-key (partial distance-to endpoint) best))]
    (when (and target (core/move-unit-to pos target))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility target :computer)
      (let [remaining (dec (:explore-steps-remaining unit))]
        (swap! atoms/game-map update-in (conj target :contents)
               assoc :explore-steps-remaining remaining)
        (when (zero? remaining)
          ;; Switch to return: target becomes origin, regular navigation
          (swap! atoms/game-map update-in (conj target :contents)
                 assoc :flight-mode :regular
                       :flight-target-site (:explore-origin unit)
                       :flight-origin-site nil))
        (if (consume-fighter-fuel target) target nil)))))
```

### New: `drone-step`

```clojure
(defn- drone-step
  "One drone step. Maximizes unexplored coverage, no fuel reservation."
  [pos unit]
  (let [endpoint (:flight-target-site unit)
        passable (get-passable-neighbors pos)
        candidates (filter (complement occupied?) passable)
        scored (map (fn [n] [n (count-unexplored-neighbors n)]) candidates)
        best-score (apply max 0 (map second scored))
        best (map first (filter #(= best-score (second %)) scored))
        target (when (seq best)
                 (apply min-key (partial distance-to endpoint) best))]
    (when (and target (core/move-unit-to pos target))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility target :computer)
      (if (consume-fighter-fuel target) target nil))))
```

### Modified: `handle-arrival`

When a returning exploration sortie arrives back at origin, process as normal arrival (refuel, choose next flight). Clear exploration fields:

```clojure
;; In handle-arrival, after existing logic:
(swap! atoms/game-map update-in (conj pos :contents)
       dissoc :explore-origin :explore-heading :explore-steps-remaining :flight-mode)
```

### Modified: `navigate-toward-target` — Regular Leg Enhancement

```clojure
;; Change the unexplored filter from:
(<= (distance-to n target) direct-dist)
;; To:
(<= (distance-to n target) (inc direct-dist))
```

This allows mild sideways jogs to reveal fog on regular legs.

## Edge Cases

### No Unexplored Territory in Any Direction
`best-exploration-heading` returns a direction with score 0. The exploration sortie flies 16 steps in that direction anyway (over explored territory), then returns. Wasted sortie, but harmless — future rolls will produce regular legs instead.

### Exploration Sortie Hits Map Edge
Fighters can fly over any terrain but not off-map. `get-passable-neighbors` already excludes off-map cells. The step selection picks the best available neighbor, which may curve along the map edge. The 16-step count still decrements normally.

### Drone Runs Out of Fuel Over Sea
The fighter dies over sea. Normal behavior — `consume-fighter-fuel` removes it. The fog it revealed during the flight persists on the computer-map.

### Exploration Return Path Blocked
After 16 outbound steps, the fighter switches to `:regular` mode targeting its origin. If the origin site was destroyed (city conquered by player), `handle-low-fuel` kicks in when fuel gets tight, directing the fighter to the nearest surviving refueling site.

### All Neighbors Explored (During Sortie)
`count-unexplored-neighbors` returns 0 for all candidates. Tie-breaking picks the one closest to the projected endpoint, keeping the fighter moving forward rather than circling.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/fighter.cljc` | Add `count-unexplored-neighbors`, `count-unexplored-along-direction`, `best-exploration-heading`, `explore-step`, `drone-step`. Modify `ensure-flight-target` (random roll for flight type), `move-fighter-once` (exploration/drone priorities), `handle-arrival` (clear exploration fields), `navigate-toward-target` (relax distance filter for regular legs). |
| `spec/empire/computer/fighter_spec.clj` | Tests: 50% flight type split, 1/20 drone rate, exploration heading picks most-unexplored direction, exploration sortie flies 16 steps then returns, drone flies until death, step selection maximizes unexplored neighbors, regular leg allows +1 distance for unexplored, exploration return after origin destroyed finds alternate site. |

## Key Design Decisions

### Random roll, not toggle
Each flight independently rolls 50/50. No per-fighter state to track. Simple, stateless, and produces the right distribution over many flights without coupling consecutive flights.

### 16-step outbound = half fuel
With 32 fuel, 16 outbound steps leaves 16 for return. Manhattan distance back is at most 16 (straight line). Diagonal returns may be shorter, giving a small safety margin. This is tight by design — exploration sorties should push deep.

### Greedy step selection
Each step picks the neighbor with the most unexplored neighbors. This greedy approach naturally follows the fog boundary, sweeping maximum new territory. It outperforms a fixed straight line because it adapts to the actual fog shape.

### Drones are expendable
The 1/20 drone rate means roughly 1 fighter in 40 total flights is sacrificed. The intelligence value of deep reconnaissance outweighs the production cost (10 rounds) at a 2.5% rate.
