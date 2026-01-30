# Plan: Carrier Positioning

## Summary

Carriers serve as stationary refueling platforms that extend fighter range across the map. They position themselves in sea cells that are within fighter fuel range of an existing refueling site (computer city or positioned carrier) and at least 80% of fuel range from all other refueling sites. Once in position, carriers hold. They move only to repair damage or if their position becomes redundant.

## Positioning Algorithm

### Refueling Network

The computer maintains a network of refueling sites:
- All computer-owned cities (permanent sites)
- All positioned carriers (mobile sites)

A carrier's goal is to extend this network into uncovered territory.

### Finding a Position

When a carrier needs a position (newly produced or repositioning):

1. Identify all current refueling sites (computer cities + positioned carriers).
2. Search for the first sea cell that satisfies:
   - Within fighter fuel range of at least one existing refueling site
   - At least 80% of fighter fuel range from every other refueling site
   - Extends coverage toward uncovered territory (unexplored sea or land beyond current network reach)
3. Use the first valid position found — no exhaustive search.
4. If no valid position exists, the carrier holds at its current location or patrols until one opens up.

### Spacing Rule

The 80% minimum spacing prevents carriers from clustering. With a fighter fuel range of F, carriers must be at least `0.8 * F` apart from all other refueling sites (cities and carriers).

### Carrier Count

There is no fixed production cap. The number of carriers is naturally bounded by the number of available positions. As carriers fill positions, fewer valid positions remain. When no positions exist, production stops producing carriers.

## Behavior States

### `:positioning`
- Carrier has a target position and is pathfinding toward it.
- Once it reaches the target sea cell, transitions to `:holding`.

### `:holding`
- Carrier is at its assigned position and stationary.
- Fighters can land on it to refuel.
- Stays put regardless of nearby enemies (no wake on enemy approach).
- Periodically checks if position is still valid (not redundant).

### `:repositioning`
- Position became redundant (a new city or carrier now covers the same area).
- Carrier searches for a new valid position.
- If found, pathfinds to it (becomes `:positioning`).
- If none found, enters `:holding` at current location until one opens.

### `:repairing`
- Carrier took damage and is pathfinding to nearest friendly coastal city.
- After repair, searches for a new position (its old position may have been filled).

## Redundancy Detection

A carrier's position is redundant if:
- A newly conquered city is within fuel range, AND
- All territory the carrier covers is now reachable from other refueling sites within fuel range

Check this when cities are conquered or other carriers reposition. The carrier transitions to `:repositioning`.

## Production Integration

At each production decision point for coastal cities, after checking transports and armies:

1. Count current refueling sites (cities + positioned carriers).
2. Search for a valid carrier position (same algorithm as above).
3. If a valid position exists and no carrier is already en route to it, produce a carrier.
4. If no valid position exists, skip carriers and fall to ratio-based production.

### Updated Production Priority

1. Transports (cap 2 per country)
2. Armies (cap 10 per country)
3. Destroyers (global cap: destroyers < transports)
4. Carriers (if all three conditions met: computer owns >10 cities, fewer than 2 cities currently producing carriers, and a valid position exists)
5. Everything else (ratio-based)

### Carrier Production Constraints

- **City threshold:** Carrier production only unlocks after the computer owns more than 10 cities globally.
- **Producer cap:** At most 2 cities may be producing carriers at any given time. If 2 cities are already building carriers, no additional city starts one.
- **Position gating:** A valid carrier position must exist (per the positioning algorithm above).

## New Unit Fields

### Carrier
- `:carrier-mode` — `:positioning`, `:holding`, `:repositioning`, `:repairing`
- `:carrier-target` — target sea position `[row col]` (nil when holding)

## Fighter Integration (Deferred)

Fighters will plan multi-hop routes through the refueling network, landing on carriers to refuel. Details in a separate fighter plan.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/ship.cljc` | Add carrier positioning state machine and position-finding algorithm |
| `src/empire/computer/production.cljc` | Carrier production check: valid position exists, no carrier en route |
| `src/empire/player/production.cljc` | Add carrier fields on spawn |
| `src/empire/player/combat.cljc` | On city conquest, check if nearby carriers are now redundant |
| `src/empire/units/carrier.cljc` | Add carrier-mode and carrier-target to initial state |
| `src/empire/config.cljc` | Add carrier spacing constant (80% of fighter fuel range) |
| `spec/empire/computer/ship_spec.clj` | Tests for position finding, spacing, redundancy, repair |
| `spec/empire/computer/production_spec.clj` | Tests for carrier production gated on valid position |
