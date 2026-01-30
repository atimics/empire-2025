# Plan: Destroyer Transport Escort

## Summary

Destroyers serve as dedicated transport escorts. Each destroyer adopts an unadopted transport, intercepts it, then travels alongside it wherever it goes. Destroyers avoid blocking army landings. If the transport dies, the destroyer repairs (if needed) then adopts another. If the destroyer dies, the transport loses its escort. Total destroyers globally cannot exceed total transports.

## Behavior Rules

### Adoption

1. When a destroyer is produced (or finishes repairs after losing its transport), it scans for unadopted transports and adopts the nearest one.
2. Adoption is mutual: the destroyer stores the transport's ID, the transport stores the destroyer's ID.
3. A transport can have at most one escort. A destroyer escorts at most one transport.

### Interception

4. After adoption, the destroyer pathfinds toward the transport's current position.
5. While intercepting, the destroyer moves independently each turn toward the transport.
6. Once adjacent to the transport, the destroyer enters escort mode.

### Escort Mode

7. The destroyer mirrors the transport's movement, staying 1 cell away in sea.
8. When the transport is at a coast unloading, the destroyer avoids cells between the transport and land — it stays on the seaward side so armies can disembark onto land cells.
9. The destroyer moves after or in coordination with the transport each turn.

### Transport Destroyed

10. If the escorted transport is destroyed, the destroyer checks if it needs repairs.
11. If damaged, the destroyer pathfinds to the nearest friendly coastal city for repair. After repair, it adopts a new unadopted transport.
12. If undamaged, it immediately adopts a new unadopted transport (if one exists). If none available, it patrols or sentries until one becomes available.

### Destroyer Destroyed

13. When a destroyer is destroyed, its transport's escort ID is cleared. The transport becomes unadopted and eligible for adoption by a new destroyer.

### Repair

14. Destroyers only repair when their transport is destroyed — they do NOT break escort for repairs.
15. If the transport happens to dock at a city that can repair the destroyer, that's a bonus but not actively sought.

## Production Cap

- **Global cap:** Total computer destroyers on the map cannot exceed total computer transports on the map.
- At each production decision point, count all live computer destroyers and transports globally. If destroyers >= transports, do not produce a destroyer.
- This cap is checked independently of the per-country transport cap (2 per country) and army cap (10 per country).

### Production Priority Update

The country production priority order becomes:
1. Transports (cap 2 per country)
2. Armies (cap 10 per country)
3. Destroyers (global cap: destroyers < transports, and the city's country has an unadopted transport)
4. Everything else (ratio-based)

A coastal city in a country that has an unadopted transport and the global cap allows it should produce a destroyer. If the country has no unadopted transport, skip to ratio-based.

## New Unit Fields

### Destroyer
- `:escort-transport-id` — the transport ID this destroyer is escorting (nil if seeking)
- `:escort-mode` — `:intercepting`, `:escorting`, `:seeking`, or `:repairing`

### Transport
- `:escort-destroyer-id` — the destroyer ID escorting this transport (nil if unadopted)

## Integration Points

### Production (`computer/production.cljc`)

- Add destroyer to priority check after armies
- Count global destroyers and transports for cap
- Check if country has an unadopted transport before producing

### Computer Ship AI (`computer/ship.cljc`)

- Add destroyer escort logic: seek → intercept → escort state machine
- Mirror transport movement during escort
- Handle transport death: transition to repair or seek
- Avoid blocking landing cells (cells between transport and adjacent land)

### Transport (`computer/transport.cljc`)

- On unload, communicate position/intent to escort so destroyer can position on seaward side
- On destruction, clear escort pairing

### Combat (`player/combat.cljc`)

- When a destroyer is destroyed, clear the transport's `:escort-destroyer-id`
- When a transport is destroyed, set the destroyer's `:escort-mode` to `:seeking` or `:repairing`

### Movement Coordination

- Destroyer needs to know where the transport moved this turn to mirror it
- Option A: Destroyer moves immediately after its transport in the processing order
- Option B: Transport stores its last move direction; destroyer reads it next step
- Option A is cleaner if processing order can be controlled

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/ship.cljc` | Add destroyer escort state machine (seek, intercept, escort, repair) |
| `src/empire/computer/transport.cljc` | Store escort-destroyer-id; communicate position to escort |
| `src/empire/computer/production.cljc` | Destroyer production priority with global cap |
| `src/empire/player/combat.cljc` | Clear escort pairing on either unit's death |
| `src/empire/player/production.cljc` | Add escort fields to destroyer and transport on spawn |
| `src/empire/units/destroyer.cljc` | Add escort fields to initial state |
| `src/empire/units/transport.cljc` | Add escort-destroyer-id to initial state |
| `spec/empire/computer/ship_spec.clj` | Tests for escort adoption, interception, mirroring, landing avoidance |
| `spec/empire/computer/production_spec.clj` | Tests for global destroyer cap |
| `spec/empire/player/combat_spec.clj` | Tests for escort pairing cleared on death |
