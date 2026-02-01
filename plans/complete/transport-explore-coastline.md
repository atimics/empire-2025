# Plan: Transport Explores Unexplored Coastline

## Summary

When a transport has no destination (no armies to pick up, no unload target), it should explore unexplored coastline first — sea cells adjacent to unexplored land. If no unexplored coastline exists, it falls back to exploring any unexplored sea. This makes idle transports useful scouts that map out new continents for future invasions.

## Current Behavior

Transport `explore-sea` (transport.cljc:137) calls `pathfinding/find-nearest-unexplored` which does BFS over passable sea cells to find the nearest cell adjacent to any unexplored cell on the computer-map. This treats all unexplored cells equally — open ocean fog and coastline fog have the same priority.

## New: `find-nearest-unexplored-coastline`

Add a BFS variant in `pathfinding.cljc` that specifically targets sea cells adjacent to unexplored land or city cells (coastline), rather than any unexplored cell:

```clojure
(defn- adjacent-to-unexplored-land?
  "Returns true if any neighbor is unexplored land or city on computer-map,
   or is nil (unexplored) and the corresponding game-map cell is land/city."
  [pos computer-map game-map]
  (let [[x y] pos]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)
                  comp-cell (get-in computer-map [nx ny])
                  real-cell (get-in game-map [nx ny])]
              (and (nil? comp-cell)
                   real-cell
                   (#{:land :city} (:type real-cell)))))
          neighbor-offsets)))
```

Wait — the computer-map stores cells as the computer has seen them. Unexplored cells are nil. But the computer can't see the real game-map to know if an unexplored cell is land. The BFS needs to work from the computer's perspective.

Revised approach: a sea cell is "adjacent to unexplored coastline" if it is adjacent to at least one nil cell on the computer-map AND at least one land/city cell on the computer-map. This means the transport is at a known coast that has unexplored territory nearby — a frontier worth exploring.

```clojure
(defn- at-exploration-frontier?
  "Returns true if pos is a sea cell adjacent to both:
   - at least one unexplored (nil) cell on computer-map
   - at least one known land/city cell on computer-map
   This identifies coastal frontier positions worth exploring."
  [pos computer-map]
  (let [[x y] pos
        neighbors (for [[dx dy] neighbor-offsets
                        :let [nx (+ x dx) ny (+ y dy)
                              cell (get-in computer-map [nx ny])]
                        :when cell]
                    cell)
        unknowns (for [[dx dy] neighbor-offsets
                       :let [nx (+ x dx) ny (+ y dy)]
                       :when (and (>= nx 0) (< nx (count computer-map))
                                  (>= ny 0) (< ny (count (first computer-map)))
                                  (nil? (get-in computer-map [nx ny])))]
                   true)]
    (and (seq unknowns)
         (some #(#{:land :city} (:type %)) neighbors))))
```

### BFS for Coastline Exploration

```clojure
(defn- find-nearest-unexplored-coastline-uncached
  "BFS from start over passable sea cells to find nearest cell at the
   exploration frontier (adjacent to both unexplored and known land)."
  [start unit-type]
  (let [game-map @atoms/game-map
        computer-map @atoms/computer-map]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
           visited #{start}]
      (when (seq queue)
        (let [current (peek queue)
              rest-queue (pop queue)]
          (if (and (not= current start)
                   (at-exploration-frontier? current computer-map))
            current
            (let [neighbors (remove visited
                                    (get-passable-neighbors current unit-type game-map))
                  new-visited (into visited neighbors)
                  new-queue (into rest-queue neighbors)]
              (recur new-queue new-visited))))))))

(defn find-nearest-unexplored-coastline
  "BFS to find nearest sea cell at a coastal exploration frontier.
   Cached per round like find-nearest-unexplored."
  [start unit-type]
  (let [cache @bfs-unexplored-cache
        cache-key [:coastline unit-type]]
    (if (contains? cache cache-key)
      (get cache cache-key)
      (let [result (find-nearest-unexplored-coastline-uncached start unit-type)]
        (swap! bfs-unexplored-cache assoc cache-key result)
        result))))
```

## Modified: `explore-sea` in transport.cljc

```clojure
(defn- explore-sea
  "Move transport toward unexplored coastline first, then any unexplored sea.
   Stays put if all sea is explored."
  [pos]
  (if-let [target (pathfinding/find-nearest-unexplored-coastline pos :transport)]
    (move-toward-position pos target)
    (when-let [target (pathfinding/find-nearest-unexplored pos :transport)]
      (move-toward-position pos target))))
```

## Edge Cases

### No Unexplored Coastline But Unexplored Open Ocean

The transport falls through to `find-nearest-unexplored` which finds any sea adjacent to unexplored cells. The transport scouts open ocean — less useful for finding continents but still reveals fog.

### All Sea Explored

Both BFS calls return nil. The transport stays put. This is existing behavior — a fully-explored map has nothing for idle transports to do.

### Transport Already at Frontier

If the transport is already at a coastal frontier, BFS skips the start position (existing behavior) and finds the next frontier cell. The transport moves along the coastline, progressively revealing land.

### BFS Cache Interaction

The cache key `[:coastline :transport]` is distinct from the existing `:transport` key used by `find-nearest-unexplored`. Both caches clear each round (existing cache-clearing behavior).

## Files Modified

| File | Change |
|------|--------|
| `src/empire/movement/pathfinding.cljc` | Add `at-exploration-frontier?`, `find-nearest-unexplored-coastline-uncached`, `find-nearest-unexplored-coastline`. |
| `src/empire/computer/transport.cljc` | Modify `explore-sea` to try coastline exploration first. |
| `spec/empire/movement/pathfinding_spec.clj` | Tests: frontier detection at known coast with adjacent unexplored, BFS finds nearest coastline frontier, cache works with coastline key, falls back when no coastline exists. |
| `spec/empire/computer/transport_spec.clj` | Tests: idle transport moves toward unexplored coastline, falls back to open sea when no coastline, stays put when fully explored. |

## Key Design Decisions

### Frontier = unexplored + known land neighbor

A cell adjacent to only unexplored cells could be open ocean. Requiring at least one known land neighbor ensures the transport heads toward actual coastlines where it might find cities or landing spots for armies.

### Same BFS infrastructure

Reuses the existing BFS pattern and cache from `find-nearest-unexplored`. No new traversal algorithms needed — just a different termination condition.

### Transport-only change

This only modifies transport `explore-sea`. Other ship types that call `find-nearest-unexplored` directly are unaffected and continue exploring any unexplored territory.
