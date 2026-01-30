# Plan: Fighter Map Coverage

## Summary

Computer fighters systematically cover the map by flying legs between refueling sites (computer cities and positioned carriers). Legs are defined by their two endpoints and tracked globally with aging. Fighters prefer unflown legs, then the oldest available leg. Within a leg, fighters prefer flying over unexplored territory.

## Concepts

### Refueling Site
A computer-owned city or a carrier in `:holding` mode. Fighters land on these to refuel.

### Leg
A flight between two refueling sites, defined by its endpoints (unordered pair). Example: `#{city-A carrier-B}`. The route taken between endpoints can vary — the leg identity is just the pair.

### Leg Record
Global tracking of all legs with aging:
- `:endpoints` — unordered pair of refueling site positions
- `:last-flown` — round number when this leg was last completed (nil if never flown)

## Behavior

### Choosing a Leg

When a fighter is at a refueling site and ready to launch:

1. Enumerate all reachable refueling sites within fighter fuel range from current position.
2. For each reachable site, look up the leg record for the pair `#{current-site, target-site}`.
3. Pick the leg that is:
   - **Unflown** (`:last-flown` is nil) — highest priority
   - **Oldest** (lowest `:last-flown` round number) — if all have been flown
4. If no reachable sites exist (isolated), the fighter holds.

### Flying a Leg

Once a target refueling site is chosen:

1. The fighter launches and moves toward the target site.
2. Each step, prefer cells that:
   - Are unexplored (highest priority)
   - Move generally toward the target (don't stray so far that fuel runs out)
3. The fighter must reserve enough fuel to reach the target. Route deviations over unexplored territory are allowed only if fuel permits.
4. On landing at the target site, the leg record is updated with the current round number as `:last-flown`.

### Fuel Management

- Fighter knows its current fuel and the Manhattan distance to the target site.
- At each step, ensure remaining fuel > distance to target (with a small safety margin).
- If fuel gets critical, fly direct to target — no more exploration deviations.

## Global Leg Memory

### Data Structure

A new atom `fighter-leg-records`:
```clojure
{#{[r1 c1] [r2 c2]} {:last-flown 42}
 #{[r1 c1] [r3 c3]} {:last-flown nil}
 ...}
```

Key is a set of two positions (unordered pair). Value tracks when the leg was last completed.

### Leg Discovery

Legs are discovered dynamically. When a fighter at site A finds reachable site B, the leg `#{A B}` is added to the records if not already present (with `:last-flown nil`).

### Network Changes

When the refueling network changes (new city conquered, carrier repositioned or destroyed):
- New legs appear naturally as fighters discover new reachable sites.
- Legs to destroyed carriers become invalid. Clean up: remove legs where an endpoint is no longer a refueling site.

## Production

Fighter production follows the existing ratio-based system (after transports, armies, destroyers, and carriers). No new cap needed — the ratio system already balances fighters against other units.

## Integration Points

### Launch Decision (`computer/fighter.cljc`)

When a fighter is at a refueling site (city or carrier) and is awake:
1. Enumerate reachable refueling sites.
2. Consult `fighter-leg-records` for leg ages.
3. Pick unflown or oldest leg.
4. Set target and launch.

### In-Flight Movement (`computer/fighter.cljc`)

Each step:
1. Check fuel budget: remaining fuel vs distance to target.
2. If budget allows deviation: prefer unexplored neighbor cells that still trend toward target.
3. If budget tight: fly direct.

### Landing (`computer/fighter.cljc`)

On reaching target refueling site:
1. Update `fighter-leg-records` for this leg with current round number.
2. Refuel.
3. Next turn, choose a new leg from this site.

### Carrier Integration

Carriers in `:holding` mode are refueling sites. Fighters can plan multi-hop routes across the map: city → carrier → carrier → target area. Each hop is a separate leg.

## New Atoms

- `fighter-leg-records` — global map of leg endpoint pairs to last-flown data.

## New Unit Fields

### Fighter
- `:flight-target-site` — position of the target refueling site for current leg (nil when grounded).
- `:flight-origin-site` — position of the origin refueling site (for recording the leg on landing).

## Files Modified

| File | Change |
|------|--------|
| `src/empire/atoms.cljc` | Add `fighter-leg-records` atom |
| `src/empire/computer/fighter.cljc` | Leg selection, in-flight unexplored preference, landing/recording |
| `src/empire/computer/production.cljc` | No change — fighters use existing ratio-based production |
| `src/empire/config.cljc` | Fuel safety margin constant if needed |
| `src/empire/test_utils.cljc` | Add reset for `fighter-leg-records` |
| `spec/empire/computer/fighter_spec.clj` | Tests for leg selection (unflown, oldest), fuel management, unexplored preference |
