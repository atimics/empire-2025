# Plan: Computer Satellite

## Summary

The computer can have at most one satellite at a time. Satellite production unlocks after the computer conquers 15 cities. When produced, the satellite picks a random direction (one of 8: N, NE, E, SE, S, SW, W, NW) and flies in a straight line until it expires.

## Behavior

1. On production, pick a random direction from the 8 cardinal/diagonal directions.
2. Each turn, move one cell in that direction.
3. Continue until the satellite's turns expire (per existing `turns-remaining` field).
4. The satellite reveals fog-of-war as it moves (existing visibility logic).
5. If the satellite reaches a map edge before expiring, it continues along the edge or stops — it does not wrap or bounce.

## Production

- **Global cap:** At most 1 computer satellite alive at any time.
- **City threshold:** Computer must own more than 15 cities.
- When the satellite expires, the next city to finish production can produce a replacement (if both conditions met).

### Production Priority (Updated)

1. Transports (cap 2 per country)
2. Armies (cap 10 per country)
3. Patrol boat (1 per country, coastal)
4. Destroyers (global: destroyers <= transports)
5. Carriers (requires >10 cities, max 2 producing)
6. Battleships (global: battleships <= carriers)
7. Submarines (global: submarines <= 2 * carriers)
8. **Satellite (global: max 1, requires >15 cities)**
9. Everything else (ratio-based)

## New Unit Fields

### Satellite
- `:direction` — `[dr dc]` vector chosen randomly at production (e.g., `[-1 1]` for NE)

No new fields beyond this — `turns-remaining` already exists.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/production.cljc` | Satellite production: max 1 global, requires >15 cities |
| `src/empire/player/production.cljc` | Stamp random direction on computer satellite spawn |
| `src/empire/movement/satellite.cljc` | Use stamped direction for computer satellites (straight line) |
| `spec/empire/computer/production_spec.clj` | Tests for satellite cap and city threshold |
| `spec/empire/movement/satellite_spec.clj` | Tests for straight-line movement in stamped direction |
