# Plan: Stuck Transport Scuttle

## Summary

When a computer transport hasn't moved in 10 rounds, it unloads its armies at the nearest beach, scuttles itself, and the city that produced it is marked as landlocked so it never produces another ship.

## Detection

### New Fields on Transport

- `:stuck-since-round` — The round number when the transport was last observed at its current position. Updated each time the transport moves. If `(- current-round stuck-since-round) >= 10`, the transport is stuck.
- `:produced-at` — The coordinates of the city that produced this transport. Set at spawn time. Used to mark the city landlocked on scuttle.

### Tracking Position Changes

In `process-transport`, after any successful move, reset `:stuck-since-round` to the current round:

```clojure
;; After a successful move (transport changed position):
(swap! atoms/game-map assoc-in (conj new-pos :contents :stuck-since-round) @atoms/round-number)
```

At the start of `process-transport`, check for stuck condition:

```clojure
(defn- stuck? [transport]
  (let [since (:stuck-since-round transport)]
    (and since (>= (- @atoms/round-number since) 10))))
```

### Initialization

When a transport is spawned, set `:stuck-since-round` to the current round:

```clojure
;; In player/production.cljc, apply-unit-type-attributes:
(= item :transport)
(assoc :transport-mission :idle
       :stuck-since-round @atoms/round-number)
```

## Scuttle Procedure

When a transport is detected as stuck in `process-transport`:

### Step 1: Unload Armies at Nearest Beach

Find adjacent land cells (`:land` or `:city` type) that are empty. Unload as many armies as possible:

```clojure
(defn- scuttle-unload [pos transport]
  (let [neighbors (core/get-neighbors pos)
        game-map @atoms/game-map
        land-cells (filter (fn [n]
                             (let [cell (get-in game-map n)]
                               (and cell
                                    (#{:land :city} (:type cell))
                                    (nil? (:contents cell)))))
                           neighbors)
        army-count (:army-count transport 0)
        to-unload (min army-count (count land-cells))]
    (doseq [land-pos (take to-unload land-cells)]
      (let [army {:type :army :owner :computer :mode :awake :hits 1
                  :steps-remaining (config/unit-speed :army)}]
        (swap! atoms/game-map assoc-in (conj land-pos :contents) army)
        (visibility/update-cell-visibility land-pos :computer)))))
```

If there are no adjacent land cells, the armies go down with the ship.

### Step 2: Remove the Transport

```clojure
(swap! atoms/game-map update-in pos dissoc :contents)
```

Also clean up any associated state:
- Release any escort destroyer (clear its `:escort-transport-id`)
- Remove from any pending attention queues

### Step 3: Mark City as Landlocked

```clojure
(defn- mark-city-landlocked [city-pos]
  (when city-pos
    (swap! atoms/game-map assoc-in (conj city-pos :landlocked) true)))
```

Use the transport's `:produced-at` field to find the city.

### Landlocked Effect on Production

In `computer/production.cljc`, all ship production checks already gate on `coastal?`. Add a landlocked check:

```clojure
(defn city-is-coastal?
  "Returns true if city has adjacent sea cells and is not landlocked."
  [city-pos]
  (and (not (:landlocked (get-in @atoms/game-map city-pos)))
       (some (fn [neighbor]
               (= :sea (:type (get-in @atoms/game-map neighbor))))
             (get-neighbors city-pos))))
```

This single change prevents all ship types (transport, destroyer, patrol-boat, carrier, battleship, submarine) from being produced at a landlocked city, since they all check `coastal?`.

## Recording Producing City

### Modify `stamp-unit-fields` in `player/production.cljc`

Add a new step that records the spawning city coordinates on the unit:

```clojure
(defn- apply-produced-at
  "Records the producing city coordinates on transports."
  [unit item coords]
  (if (= item :transport)
    (assoc unit :produced-at coords)
    unit))
```

Thread this through `stamp-unit-fields` (which already receives the cell but not coordinates — the spawning function `spawn-unit` will need to pass coordinates through).

## Integration Into process-transport

At the top of `process-transport`, before mission logic:

```clojure
(defn process-transport [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))]
    (if (stuck? transport)
      (do (scuttle-unload pos transport)
          (mark-city-landlocked (:produced-at transport))
          (release-escort pos transport)
          (swap! atoms/game-map update-in pos dissoc :contents)
          nil)
      ;; ... existing mission logic ...
      )))
```

## Edge Cases

### Transport Stuck in Open Sea (No Adjacent Land)

If there are no adjacent land cells, `scuttle-unload` unloads zero armies. The transport and its cargo are lost. The city is still marked landlocked — the body of water is genuinely inaccessible.

### Transport Stuck While Loading (Empty)

A transport with 0 armies that hasn't moved in 10 rounds still scuttles and marks the city landlocked. If it can't move, the water is useless for shipping.

### Producing City Already Conquered by Player

If the producing city was conquered, `mark-city-landlocked` still sets `:landlocked` on the cell. If the computer reconquers the city, the landlocked flag persists — this is correct since the geography hasn't changed.

### Producing City Nil

If `:produced-at` is nil (transport from before this feature), skip the landlocked marking. The transport still scuttles.

### Escort Destroyer Orphaned

When the transport scuttles, any escort destroyer with a matching `:escort-transport-id` needs its escort fields cleared so it returns to normal ship behavior. Look up the destroyer by the transport's `:transport-id` and clear its escort state.

### Transport Moves on Round 10

If the transport finally moves on exactly round 10, `:stuck-since-round` resets and the 10-round counter restarts. The check is `>=`, so it must be stuck for a full 10 rounds without any movement.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/transport.cljc` | Add `stuck?`, `scuttle-unload`, `release-escort`. Modify `process-transport` to check stuck condition first. Reset `:stuck-since-round` after successful moves. |
| `src/empire/computer/production.cljc` | Modify `city-is-coastal?` to check `:landlocked` flag. |
| `src/empire/player/production.cljc` | Add `apply-produced-at` to record producing city on transport. Set `:stuck-since-round` on spawn. Pass coordinates through `stamp-unit-fields`. |
| `spec/empire/computer/transport_spec.clj` | Tests: transport scuttles after 10 rounds stuck, armies unloaded to adjacent land, city marked landlocked, landlocked city produces no ships, escort released on scuttle, stuck counter resets on move. |

## Key Design Decisions

### Landlocked on city-is-coastal? not per-type checks

Adding the landlocked check to `city-is-coastal?` blocks ALL ship production from that city in one place, rather than adding checks to each ship production priority. Any future ship types also automatically respect it.

### 10-round window, not cumulative

The counter resets on any successful move. A transport that moves once in 10 rounds is not stuck. Only truly immobile transports trigger scuttling.

### Unloaded armies have no country-id

Armies scuttle-unloaded lack a country-id (same as regular transport unloads). They behave as invasion forces and will establish a new country on conquering a city.
