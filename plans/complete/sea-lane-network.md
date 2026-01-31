# Plan: Sea Lane Network

## Summary

Computer ships build a persistent navigation graph from previously computed A* paths. Each A* path is decomposed into straight-line segments joined at nodes (direction changes, intersections, endpoints). Future ships route cheaply through this graph — finding a nearby entry node, traversing the graph with Dijkstra, then doing short bounded A* for the last mile — instead of running full-map A* across the entire ocean.

## Concepts

### Sea Lane Network
A persistent graph of nodes and segments derived from previously traveled A* paths. Stored as an atom that persists across rounds (unlike `path-cache` which is cleared each round). Grows naturally as computer ships travel.

### Node
A sea position where segments meet. Nodes arise at direction changes in a path, at intersections where paths cross, and at path endpoints near coastline. Each node has a unique ID and connects to one or more segments.

### Segment
A straight-line sequence of adjacent sea cells all sharing the same compass direction (one of 8 offsets). Connects exactly two nodes. Minimum length of 2 steps — shorter runs are noise.

### Entry/Exit Node
The network node nearest a ship's current position (entry) or goal (exit). The ship does short bounded A* to reach the entry node, follows the graph to the exit node, then does short bounded A* to the goal.

### Bounded A*
A variant of the existing `a-star` that limits search to cells within a configurable radius of the search midpoint. Much cheaper than full-map A*: explores at most ~(2r+1)^2 cells.

## Data Structures

### Node
```clojure
{:id          1
 :pos         [30 42]
 :segment-ids #{3 7 12}}
```

### Segment
```clojure
{:id         3
 :node-a-id  1
 :node-b-id  2
 :direction  [-1 0]     ;; [dr dc] from node-a toward node-b
 :cells      [[30 42] [29 42] [28 42] [27 42]]  ;; ordered, endpoints inclusive
 :length     3}          ;; (dec (count cells))
```

### Network Atom
```clojure
{:nodes      {1 {:id 1 :pos [30 42] :segment-ids #{3 7}} ...}
 :segments   {3 {:id 3 :node-a-id 1 :node-b-id 2 ...} ...}
 :pos->node  {[30 42] 1 ...}         ;; position -> node-id (O(1) lookup)
 :pos->seg   {[29 42] 3 ...}         ;; interior cell -> segment-id (intersection detection)
 :next-node-id    5
 :next-segment-id 8}
```

## Behavior

### 1. Path Recording

When `a-star` computes a successful path in `next-step`, pass it to `record-path!`:

1. Walk the path cell by cell, tracking the direction `[dr dc]` between consecutive cells.
2. When direction changes (or path ends), the preceding same-direction run is a candidate segment.
3. Discard segments shorter than 2 steps.
4. For each candidate segment, check endpoints and interior cells against the existing network.

### 2. Node Creation

A node is created at position `p` when:

1. **Direction change** — the path changes compass direction. Node at the pivot cell.
2. **Path endpoint** — first and last cells of the path (near coastline/city).
3. **Intersection** — a new segment's interior cell coincides with an existing segment's interior cell (found via `pos->seg`). The existing segment is split at the intersection.
4. **Existing node** — if an endpoint already has a node (found via `pos->node`), reuse it.

### 3. Segment Registration

For each valid candidate segment (length >= 2):

1. Look up or create nodes at each endpoint.
2. Skip if an identical segment already exists between those two nodes.
3. Register: add to `:segments`, link both endpoint nodes via `:segment-ids`, index interior cells in `:pos->seg`.

### 4. Intersection Splitting

When a new path cell is in `pos->seg` (crosses an existing segment interior):

1. Look up the existing segment `S`.
2. Create a new node `N` at the intersection position.
3. Split `S` into two sub-segments: `S1` (S's node-a to N) and `S2` (N to S's node-b).
4. Remove `S`, register `S1` and `S2`, update `pos->seg` index.
5. The new path's segment also connects at `N`.

### 5. Size Limits

- **Max nodes**: 500 (configurable)
- **Max segments**: 1000 (configurable)
- When limits are reached, stop recording new paths. Existing network is used as-is.

## Routing Algorithm

When a ship needs a path from `start` to `goal`:

### Step 1: Check Viability
If the network has fewer than 4 nodes, skip to fallback.

### Step 2: Find Entry Node
Search for the nearest node within `local-radius` (15) of `start`. If none, try `extended-radius` (25). If still none, fallback.

### Step 3: Find Exit Node
Same search near `goal`.

### Step 4: Graph Traversal
Dijkstra from entry node to exit node. Edge weights are segment lengths. If no path found (disconnected components), fallback.

### Step 5: Assemble Path
1. Bounded A* from `start` to entry node.
2. Concatenate segment cells along the Dijkstra route (reversing cells when traversing a segment from node-b to node-a).
3. Bounded A* from exit node to `goal`.
4. Remove duplicate positions at joins.

### Step 6: Fallback
If any step fails, use full unbounded A* (current behavior). The network is a performance optimization — never a correctness requirement.

## Network Lifecycle

- **Early game**: Network empty. All pathfinding uses full A*. Each successful A* seeds the network.
- **Mid game**: Major shipping lanes exist. New transports on similar routes benefit from graph routing.
- **Late game**: Network well-developed. Full A* rarely needed.
- **Persistence**: Sea cells never change type, so recorded segments remain valid. Transient obstructions (ships in cells) are handled by per-step movement logic, not the network.

## Integration Points

### pathfinding.cljc
- Add `bounded-a-star`: radius-limited variant of `a-star`.
- Add `network-route` function: implements Steps 1-5 of the routing algorithm.
- Modify `next-step`: try `network-route` first; fall back to current A* if it returns nil.
- Call `record-path!` after successful A* computation.

### sea_lanes.cljc (new module)
- `decompose-path` — break A* path into straight-line candidate segments.
- `record-path!` — decompose and record an A* path into the network.
- `route-through-network` — full routing: find entry/exit, Dijkstra, assemble path.
- `split-segment-at` — split an existing segment at intersection.
- `dijkstra` — graph traversal over the node/segment network.
- `find-nearest-node` — find closest network node within radius.
- `assemble-path` — build cell-by-cell path from Dijkstra segment sequence.
- Internal helpers for node/segment creation and index maintenance.

### atoms.cljc
- Add `sea-lane-network` atom with initial empty network structure.

### config.cljc
- `max-sea-lane-nodes` (500)
- `max-sea-lane-segments` (1000)
- `sea-lane-local-radius` (15)
- `sea-lane-extended-radius` (25)
- `sea-lane-min-segment-length` (2)
- `sea-lane-min-network-nodes` (4)

### game-loop.cljc
- No change to `clear-path-cache`. The sea lane network persists across rounds.

### transport.cljc
- No changes. Calls `pathfinding/next-step` which transparently uses the network.

### test_utils.cljc
- Add `sea-lane-network` to `reset-all-atoms!`.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/movement/sea_lanes.cljc` | **New.** Path decomposition, node/segment management, intersection splitting, Dijkstra, bounded A*, path assembly. |
| `src/empire/movement/pathfinding.cljc` | Modify `next-step` to try network route first. Call `record-path!` after successful A*. Add `bounded-a-star`. |
| `src/empire/atoms.cljc` | Add `sea-lane-network` atom. |
| `src/empire/config.cljc` | Add sea lane constants. |
| `src/empire/test_utils.cljc` | Add `sea-lane-network` reset to `reset-all-atoms!`. |
| `spec/empire/movement/sea_lanes_spec.clj` | **New.** Tests for decomposition, node creation, intersection splitting, Dijkstra, bounded A*, full routing, size limits. |
| `spec/empire/movement/pathfinding_spec.clj` | Tests for network-integrated `next-step`: uses network when available, falls back correctly, records paths. |

## Key Design Decisions

### Duplicate segment detection
When recording a segment between two nodes that already have a segment connecting them with the same cell count, skip registration. This prevents the same path recorded twice from bloating the network.

### Intersection splitting preserves minimum segment length
When splitting a segment at an intersection, if either sub-segment would be shorter than `min-segment-length`, only register the other sub-segment (or neither if both are too short).

### Bounded A* radius centered on midpoint
The radius is measured from the midpoint of start and goal, not from either endpoint. This ensures both endpoints are within the search area when they're close together, while still bounding the search space.

### Network is never a correctness requirement
Every code path through `route-through-network` can return nil, causing fallback to full A*. The network purely optimizes performance — it never changes game behavior.
