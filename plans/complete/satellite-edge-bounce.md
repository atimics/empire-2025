# Plan: Satellite Edge Bounce

## Summary

Computer satellites currently stop at map edges and stay in place forever. They should bounce off edges by picking a new random direction that points away from the edge. This keeps satellites scanning throughout their 50-round lifespan instead of wasting it parked at a boundary.

## Current Behavior

Computer satellites have a fixed `:direction` vector (one of 8 compass directions) assigned at spawn. `move-satellite-straight` (satellite.cljc:55) moves one step in that direction each step. When the next position is off-map, the satellite stays in place — permanently stuck.

```clojure
;; Current: stays in place at edge
(if (and (>= nx 0) (< nx map-height) (>= ny 0) (< ny map-width))
  ;; move
  ...
  ;; At map edge - stays in place
  [x y])
```

## Fix

When the next position is off-map, pick a new random direction that points away from the edge, then move in that direction instead.

### New: `bounce-direction`

```clojure
(defn- bounce-direction
  "Returns a random direction vector pointing away from the map edge.
   Filters the 8 compass directions to those that move inward from the edge."
  [[x y] map-height map-width]
  (let [at-top? (zero? x)
        at-bottom? (= x (dec map-height))
        at-left? (zero? y)
        at-right? (= y (dec map-width))
        directions [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
        valid (filter (fn [[dx dy]]
                        (and (if at-top? (>= dx 0) true)
                             (if at-bottom? (<= dx 0) true)
                             (if at-left? (>= dy 0) true)
                             (if at-right? (<= dy 0) true)
                             ;; Must actually move (not [0 0] after filtering)
                             (let [nx (+ x dx) ny (+ y dy)]
                               (and (>= nx 0) (< nx map-height)
                                    (>= ny 0) (< ny map-width)))))
                      directions)]
    (when (seq valid)
      (rand-nth (vec valid)))))
```

### Modified: `move-satellite-straight`

```clojure
(defn- move-satellite-straight
  "Moves a computer satellite one step in its fixed direction.
   Bounces off map edges by picking a new random direction away from the edge."
  [[x y]]
  (let [cell (get-in @atoms/game-map [x y])
        satellite (:contents cell)
        [dx dy] (:direction satellite)
        nx (+ x dx)
        ny (+ y dy)
        map-height (count @atoms/game-map)
        map-width (count (first @atoms/game-map))]
    (if (and (>= nx 0) (< nx map-height) (>= ny 0) (< ny map-width))
      ;; Normal move
      (do (swap! atoms/game-map assoc-in [x y :contents] nil)
          (swap! atoms/game-map assoc-in [nx ny :contents] satellite)
          (visibility/update-cell-visibility [nx ny] (:owner satellite))
          [nx ny])
      ;; At edge — bounce: pick new direction, update satellite, move
      (if-let [new-dir (bounce-direction [x y] map-height map-width)]
        (let [bx (+ x (first new-dir))
              by (+ y (second new-dir))
              updated (assoc satellite :direction new-dir)]
          (swap! atoms/game-map assoc-in [x y :contents] nil)
          (swap! atoms/game-map assoc-in [bx by :contents] updated)
          (visibility/update-cell-visibility [bx by] (:owner satellite))
          [bx by])
        ;; No valid direction (shouldn't happen unless 1x1 map)
        [x y]))))
```

## Bounce Behavior by Position

| Position | Excluded directions | Possible bounce directions |
|----------|-------------------|---------------------------|
| Top edge (not corner) | dx < 0 | [0 -1] [0 1] [1 -1] [1 0] [1 1] |
| Bottom edge (not corner) | dx > 0 | [-1 -1] [-1 0] [-1 1] [0 -1] [0 1] |
| Left edge (not corner) | dy < 0 | [-1 0] [-1 1] [0 1] [1 0] [1 1] |
| Right edge (not corner) | dy > 0 | [-1 -1] [-1 0] [0 -1] [1 -1] [1 0] |
| Top-left corner | dx < 0 or dy < 0 | [0 1] [1 0] [1 1] |
| Top-right corner | dx < 0 or dy > 0 | [0 -1] [1 -1] [1 0] |
| Bottom-left corner | dx > 0 or dy < 0 | [-1 0] [-1 1] [0 1] |
| Bottom-right corner | dx > 0 or dy > 0 | [-1 -1] [-1 0] [0 -1] |

## Edge Cases

### Corner Bounce

At a corner, only 3 directions are valid (the three that move inward diagonally and along the two edges). The random choice among these creates varied bounce angles.

### Satellite Moving Along an Edge

If the satellite's direction is parallel to an edge (e.g., `[0 1]` along the top), it won't hit the edge at all — it moves along it until it reaches a corner. At the corner, it bounces inward.

### Bounce Target Cell Occupied

The satellite moves through all terrain (land, sea, cities) without interaction. Satellites don't collide with other units — they overfly everything. So the bounce target cell is always valid.

### Player Satellites

Player satellites already have bounce behavior via `calculate-new-satellite-target`. This change only affects computer satellites that use `move-satellite-straight`.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/movement/satellite.cljc` | Add `bounce-direction`. Modify `move-satellite-straight` to bounce at edges instead of staying in place. |
| `spec/empire/movement/satellite_spec.clj` | Tests: satellite bounces at top/bottom/left/right edges, satellite bounces at corners, new direction points inward, direction is random among valid options, satellite continues moving after bounce. |

## Key Design Decisions

### Random direction, not reflection

A physics-style reflection (angle of incidence = angle of reflection) would create predictable paths. A random direction away from the edge creates more varied coverage patterns across the map, which is better for reconnaissance.

### Bounce on the same step

The satellite doesn't waste a step at the edge. It detects the edge, picks a new direction, and moves in that direction on the same step. This preserves the satellite's effective lifespan.

### Direction stored on unit

The new direction is stored in `:direction` so subsequent steps continue in the bounced direction until the next edge hit. This matches the existing design where `:direction` is the satellite's persistent heading.
