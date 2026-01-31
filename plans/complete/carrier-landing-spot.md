# Plan: Carrier Landing Spot Navigation

## Summary

Carriers bumble instead of reaching their landing spots because: (1) `carrier-spacing` is 80% of fighter fuel but should be 70%, (2) `find-carrier-position` returns the first valid cell scanning top-left to bottom-right rather than choosing a position between two existing refueling sites and near the carrier, and (3) carrier movement uses greedy neighbor selection (`core/move-toward`) instead of pathfinding, so carriers get stuck against coastlines.

## Fixes

### 1. Change Carrier Spacing to 70%

In `config.cljc`, change:

```clojure
(def carrier-spacing 26)  ;; 80% of fighter-fuel
```

To:

```clojure
(def carrier-spacing 22)  ;; 70% of fighter-fuel (0.7 * 32 = 22.4, rounded down)
```

### 2. Find Position Between Two Refueling Sites

Replace the naive top-left scan in `find-carrier-position` with an algorithm that finds sea cells positioned between pairs of refueling sites. For each pair of refueling sites that are farther than `carrier-spacing` apart, search for a sea cell near the midpoint that satisfies the spacing and range constraints.

```clojure
(defn find-carrier-position
  "Finds a valid carrier position between two refueling sites, preferring
   positions closest to the midpoint of the two farthest-apart connected sites."
  []
  (let [refuel-sites (vec (find-refueling-sites))
        positioning-targets (vec (find-positioning-carrier-targets))
        spacing-sites (into refuel-sites positioning-targets)]
    (when (seq refuel-sites)
      (let [;; Find pairs of sites that are too far apart for fighters to bridge
            pairs (for [a refuel-sites
                        b refuel-sites
                        :when (and (not= a b)
                                   (> (core/distance a b) config/carrier-spacing)
                                   (<= (core/distance a b) (* 2 config/fighter-fuel)))]
                    [a b])
            ;; Sort pairs by distance descending (fill biggest gaps first)
            sorted-pairs (sort-by (fn [[a b]] (- (core/distance a b))) pairs)]
        (or
          ;; Search near midpoints of site pairs
          (first
            (for [[a b] sorted-pairs
                  :let [mid [(quot (+ (first a) (first b)) 2)
                             (quot (+ (second a) (second b)) 2)]
                        radius 8
                        candidates (for [dr (range (- radius) (inc radius))
                                         dc (range (- radius) (inc radius))
                                         :let [pos [(+ (first mid) dr)
                                                    (+ (second mid) dc)]]
                                         :when (valid-carrier-position? pos refuel-sites spacing-sites)]
                                     pos)]
                  :when (seq candidates)
                  :let [best (apply min-key #(core/distance % mid) candidates)]]
              best))
          ;; Fallback: any valid position (current behavior)
          (first (for [i (range (count @atoms/game-map))
                       j (range (count (first @atoms/game-map)))
                       :when (valid-carrier-position? [i j] refuel-sites spacing-sites)]
                   [i j])))))))
```

### 3. Use Pathfinding for Carrier Movement

Replace the greedy `move-toward` call in carrier positioning with A* pathfinding via `pathfinding/next-step`. This prevents carriers from getting stuck against coastlines.

```clojure
(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (if (= pos target)
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))
    ;; Use pathfinding instead of greedy movement
    (when-let [next-pos (pathfinding/next-step pos target :carrier)]
      (core/move-unit-to pos next-pos)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-pos :computer)
      next-pos)))
```

Similarly update `position-carrier-without-target` and `reposition-carrier` to use the same pathfinding approach when moving toward a target.

### 4. Invalidate Target When Occupied

When a carrier arrives at its target and the cell is occupied (another unit moved there), search for a new position instead of stalling:

```clojure
(defn- position-carrier-with-target
  "Handles carrier in positioning mode that has a target."
  [pos target]
  (cond
    ;; At target — transition to holding
    (= pos target)
    (swap! atoms/game-map update-in (conj pos :contents)
           #(-> % (assoc :carrier-mode :holding) (dissoc :carrier-target)))

    ;; Target became invalid (occupied or no longer valid)
    (not (valid-carrier-position? target
           (vec (find-refueling-sites))
           (into (vec (find-refueling-sites)) (vec (find-positioning-carrier-targets)))))
    (do (swap! atoms/game-map update-in (conj pos :contents) dissoc :carrier-target)
        (position-carrier-without-target pos))

    ;; Move toward target using pathfinding
    :else
    (when-let [next-pos (pathfinding/next-step pos target :carrier)]
      (core/move-unit-to pos next-pos)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-pos :computer)
      next-pos)))
```

## Edge Cases

### No Pair of Sites Far Enough Apart

If all refueling sites are within `carrier-spacing` of each other, no pairs qualify. The fallback scan takes over — this handles the case where a single city needs a carrier offshore.

### Midpoint is on Land

The midpoint of two sites might be land. The radius-8 search around the midpoint will find nearby sea cells. If none are valid, the next pair is tried.

### Pathfinding Fails

If `pathfinding/next-step` returns nil (no path to target), the carrier stays put. On the next round it tries again. If the target is truly unreachable, the target validity check will eventually trigger a new target search.

### Multiple Carriers Targeting Same Position

`find-carrier-position` already accounts for positioning carrier targets in its spacing calculation via `find-positioning-carrier-targets`. Two carriers won't target the same area.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/config.cljc` | Change `carrier-spacing` from 26 to 22. |
| `src/empire/computer/ship.cljc` | Rewrite `find-carrier-position` to search between pairs of refueling sites. Change `position-carrier-with-target` to use pathfinding and validate target. Update `position-carrier-without-target` and `reposition-carrier` similarly. |
| `spec/empire/computer/ship_spec.clj` | Tests: carrier position found between two distant sites, carrier uses pathfinding around coastline, stale target triggers re-search, fallback when no pairs exist, spacing at 70%. |

## Key Design Decisions

### 70% spacing not 80%

At 80% spacing (26 cells), two refueling sites need to be 52+ cells apart before a carrier can fit between them. At 70% (22 cells), the threshold drops to 44 cells. This allows carriers to fill gaps more aggressively, matching the intended design.

### Search near midpoint with radius

Rather than scanning the entire map, searching in a radius-8 box around the midpoint of two sites is fast and produces positions that are genuinely between the sites. The radius accommodates coastlines and obstacles near the midpoint.

### Pathfinding over greedy movement

Greedy neighbor selection (`core/move-toward`) picks the neighbor closest to the target by Manhattan distance. This fails when land masses intervene — the carrier slides along the coast indefinitely. Using `pathfinding/next-step` with A* finds the actual shortest path around obstacles.
