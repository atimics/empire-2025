# Plan: Patrol Boat Coastal Patrol

## Summary

Each country has exactly one patrol boat. The patrol boat circles its country's coastline from the sea side, keeping land on one side. It reverses direction when it hits a map edge. It avoids combat except against transports, which it attacks aggressively. When destroyed, the country produces a replacement.

## Behavior

### Coastal Patrol

1. The patrol boat stays in sea cells adjacent to the country's land.
2. It circles the coastline continuously, keeping land on one side (e.g., land on left).
3. When it hits a map edge (no valid coastal sea cell ahead), it reverses direction (switches handedness — land on left becomes land on right).
4. Prefers sea cells adjacent to the country's own land (uses country-id on cells to stay near its own country).

### Combat

- **Avoids combat:** If an enemy vessel (non-transport) is detected, the patrol boat moves away or takes a different coastal path to avoid engagement.
- **Attacks transports:** If an enemy transport is detected, the patrol boat aggressively moves to intercept and attack it.

### Production and Replacement

- **One per country.** Each country maintains exactly one patrol boat.
- At each production decision point, if the country has no live patrol boat and no city is currently producing one, a coastal city produces a patrol boat.
- This check fits into the production priority order.

### Homing

No matter where the patrol boat is produced, it pathfinds to its country's coastline before beginning patrol. A country's coastline is defined as sea cells adjacent to land cells with that country's country-id.

## New Unit Fields

### Patrol Boat
- `:patrol-country-id` — the country this patrol boat belongs to
- `:patrol-direction` — `:clockwise` or `:counter-clockwise` (reverses on map edge)
- `:patrol-mode` — `:homing`, `:patrolling`, `:attacking`, `:fleeing`

## Country Field

- `:patrol-boat-id` — ID of the country's patrol boat (nil if none). Used for liveness checks.

## Production Priority (Updated)

1. Transports (cap 2 per country)
2. Armies (cap 10 per country)
3. **Patrol boat (1 per country, coastal city only)**
4. Destroyers (global: destroyers <= transports)
5. Carriers (requires >10 cities, max 2 producing)
6. Battleships (global: battleships <= carriers)
7. Submarines (global: submarines <= 2 * carriers)
8. Everything else (ratio-based)

Patrol boats are high priority — they're cheap, defend the homeland, and each country needs exactly one.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/ship.cljc` | Add patrol boat coastal patrol logic, direction reversal, transport attack, flee from non-transports |
| `src/empire/computer/production.cljc` | Patrol boat production: 1 per country, replace on death |
| `src/empire/player/production.cljc` | Stamp patrol fields on spawn |
| `src/empire/player/combat.cljc` | On patrol boat death, clear country's patrol-boat-id |
| `src/empire/units/patrol_boat.cljc` | Add patrol fields to initial state |
| `src/empire/config.cljc` | Patrol boat constants if needed |
| `spec/empire/computer/ship_spec.clj` | Tests for coastal patrol, direction reversal, transport attack, fleeing |
| `spec/empire/computer/production_spec.clj` | Tests for 1-per-country cap and replacement |
