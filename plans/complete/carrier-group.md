# Plan: Carrier Group (Battleship & Submarine Escorts)

## Summary

Each carrier forms a carrier group with one battleship and two submarines as escorts. Escorts endlessly circle the carrier at a preferred radius of 2. When any escort detects an enemy vessel, all escorts in the group break formation and attack (the carrier does not). After combat, escorts return to orbit. If the carrier is destroyed, escorts repair if needed and adopt another carrier. If an escort is destroyed, the carrier loses that escort and becomes eligible for a replacement.

## Formation

### Carrier Group Composition
- 1 carrier (the anchor)
- 1 battleship
- 2 submarines

### Orbit Behavior

Escorts endlessly circle the carrier at radius 2:
- Each turn, an escort moves one step along a circular path around the carrier.
- Preferred distance from carrier: 2 cells (Manhattan or Chebyshev — whichever produces better-looking orbits).
- If displaced (after combat or carrier movement), escorts pathfind back to radius 2 and resume circling.
- Escorts should space themselves around the carrier, not cluster on the same side.

### Carrier Movement

When the carrier moves (positioning, repositioning, or repairing), escorts follow:
- Each escort adjusts its position to maintain radius 2 from the carrier's new position.
- The orbit continues relative to the carrier's new location.

## Combat

### Detection
When any escort detects an enemy vessel (within its visibility radius):
1. All escorts in the group are alerted.
2. All escorts break formation and move to attack the enemy.
3. The carrier does NOT engage — it holds position.

### After Combat
- Surviving escorts return to orbit around the carrier.
- If an escort was destroyed, the carrier's group record is updated (slot opened for replacement).
- If the escort took damage, it continues escorting — it does NOT break for repairs.

### Carrier Destroyed
- All escorts in the group lose their adoption.
- Damaged escorts pathfind to nearest friendly coastal city for repair.
- Undamaged escorts immediately seek another carrier to adopt.
- If no unadopted carrier exists, escorts patrol or hold until one becomes available.

## Adoption

### Mutual Pairing
- Battleship stores its carrier's ID. Carrier stores its battleship's ID.
- Each submarine stores its carrier's ID. Carrier stores both submarine IDs.
- A carrier can have at most 1 battleship and 2 submarines.

### Adoption Process
When a battleship or submarine is produced (or finishes repairs after losing its carrier):
1. Scan for carriers that have an open slot (missing battleship or submarine).
2. Adopt the nearest one.
3. Pathfind to the carrier (intercepting).
4. On arrival at radius 2, begin orbiting.

## Production

### Global Caps
- **Battleships:** total computer battleships <= total computer carriers
- **Submarines:** total computer submarines <= 2 * total computer carriers

### Production Priority (Updated)

1. Transports (cap 2 per country)
2. Armies (cap 10 per country)
3. Destroyers (global: destroyers <= transports)
4. Carriers (requires >10 cities, max 2 cities producing)
5. Battleships (global: battleships <= carriers)
6. Submarines (global: submarines <= 2 * carriers)
7. Everything else (ratio-based)

Coastal cities check these in order at each production decision point.

## New Unit Fields

### Carrier (additions to carrier-positioning.md fields)
- `:group-battleship-id` — ID of adopted battleship (nil if none)
- `:group-submarine-ids` — vector of up to 2 submarine IDs (e.g. `[3 7]` or `[3]` or `[]`)

### Battleship
- `:escort-carrier-id` — carrier ID this battleship is escorting (nil if seeking)
- `:escort-mode` — `:intercepting`, `:orbiting`, `:attacking`, `:seeking`, `:repairing`
- `:orbit-angle` — current position in orbit (to maintain spacing with other escorts)

### Submarine
- `:escort-carrier-id` — carrier ID this submarine is escorting (nil if seeking)
- `:escort-mode` — `:intercepting`, `:orbiting`, `:attacking`, `:seeking`, `:repairing`
- `:orbit-angle` — current position in orbit

## Orbit Implementation

### Circling at Radius 2

The orbit can be implemented as stepping through positions on a ring of radius 2 around the carrier:

At radius 2 (Chebyshev), there are 16 cells forming a square ring:
```
. . . . .
. . . . .
. . C . .
. . . . .
. . . . .
```

Each turn, an escort advances one step clockwise (or counter-clockwise) along this ring. With 3 escorts (1 battleship, 2 submarines), they should be spaced roughly 120 degrees apart (every ~5 cells along the 16-cell ring).

### Spacing

- Battleship starts at position 0 on the ring.
- Submarine 1 at position 5 (roughly 120 degrees).
- Submarine 2 at position 11 (roughly 240 degrees).
- Each turn, all advance one step in the same rotational direction.

### Sea-Only Constraint

Escorts can only occupy sea cells. If the next orbit position is land, the escort skips to the next valid sea position on the ring. If the carrier is near a coastline, some orbit positions may be blocked — escorts use the nearest available sea cell at approximately radius 2.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/ship.cljc` | Add battleship and submarine escort logic: orbit, combat break, carrier follow |
| `src/empire/computer/production.cljc` | Battleship and submarine production with global caps |
| `src/empire/player/production.cljc` | Add escort fields on battleship/submarine spawn |
| `src/empire/player/combat.cljc` | On escort/carrier death, update group records |
| `src/empire/units/battleship.cljc` | Add escort fields to initial state |
| `src/empire/units/submarine.cljc` | Add escort fields to initial state |
| `src/empire/units/carrier.cljc` | Add group-battleship-id and group-submarine-ids |
| `src/empire/config.cljc` | Orbit radius constant, escort spacing |
| `spec/empire/computer/ship_spec.clj` | Tests for orbit, combat break/return, adoption, carrier death |
| `spec/empire/computer/production_spec.clj` | Tests for battleship/submarine global caps |
