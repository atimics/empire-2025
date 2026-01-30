# Plan: Rename Origin Continent to Pickup Continent & Update on Unload

## Current Behavior

The transport stores `:origin-continent-pos` — a single land position recorded when it first fills up (6 armies). This position is flood-filled to derive the full origin continent. The transport then:
- Loads armies only from the origin continent
- Unloads armies only onto land that is NOT the origin continent
- After fully unloading, reverts to `:loading` mission but **retains the same** `:origin-continent-pos`, so it goes back to the same continent

## Proposed Changes

### 1. Rename `:origin-continent-pos` to `:pickup-continent-pos`

Files affected:
- `src/empire/computer/transport.cljc` — lines 185, 186, 188, 227, 236, 244 (6 occurrences)
- `src/empire/player/production.cljc` — line 46 (`:origin-beach nil` can also be cleaned up or renamed)
- `spec/empire/computer/transport_spec.clj` — ~20 occurrences

Also rename all local variables from `origin-continent` to `pickup-continent` throughout `computer/transport.cljc` for consistency (function params, lets, docstrings).

### 2. Update pickup continent after unload

When a transport fully unloads (`army-count` reaches 0), instead of keeping the old `:origin-continent-pos`, set `:pickup-continent-pos` to the nearest continent (by Manhattan distance from the transport's current position) that:
- Is NOT the continent the transport just unloaded onto (the current continent)
- Has more than 3 computer armies

This requires a new function, roughly:

```clojure
(defn- find-next-pickup-continent-pos [transport-pos current-continent]
  ;; 1. Find all computer armies on the map
  ;; 2. Group by continent (flood-fill from each army position)
  ;; 3. Exclude armies on current-continent
  ;; 4. Keep only continents with > 3 armies
  ;; 5. Return nearest army position (by Manhattan distance) from qualifying continent
  ;; 6. Return nil if no qualifying continent exists
)
```

The update happens in `unload-armies` at line 172-173, where mission changes to `:loading`. At that point, also set `:pickup-continent-pos` to the result of this function.

### 3. Handle edge cases

- If no continent has >3 armies, set `:pickup-continent-pos` to `nil` so the transport falls back to loading from any available army (existing behavior at line 43-45 in `find-nearest-army`)
- The "current continent" (the one just unloaded onto) is determined by flood-filling from adjacent land at the unload position

### 4. Test changes

- Update all existing spec references from `:origin-continent-pos` to `:pickup-continent-pos`
- Add new test: after full unload, `:pickup-continent-pos` changes to nearest continent with >3 armies (not the unload continent)
- Add new test: if no continent qualifies (none has >3 armies), `:pickup-continent-pos` is set to `nil`
- Add new test: scan-continent or equivalent correctly counts computer armies on a continent

## Performance Note

The `find-next-pickup-continent-pos` function flood-fills from each army's position, which could be expensive. To avoid redundancy, we should track which positions have already been assigned to a continent during the scan. This is a one-time cost per unload event (not per turn), so it should be acceptable.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/transport.cljc` | Rename all `origin` to `pickup`; add `find-next-pickup-continent-pos`; update `unload-armies` to reset pickup on full unload |
| `src/empire/player/production.cljc` | Rename `:origin-beach` — remove or rename; no functional change needed (it's `nil` for player transports) |
| `src/empire/computer/continent.cljc` | Possibly add `count-computer-armies` helper or enhance `scan-continent` to count by unit type |
| `spec/empire/computer/transport_spec.clj` | Rename all refs; add new tests for pickup-continent update logic |
