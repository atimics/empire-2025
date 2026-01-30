# Implementation Order

## Status

All 8 plans are unimplemented. The foundation is in place: country-id system, unload-event-id, basic production infrastructure, transport missions. No unit behavior plans have been started.

## Dependencies

```
                     Country System (exists)
                     /    |    \      \
                    /     |     \      \
       Army Coast  Patrol  Satellite  Destroyer Escort
       Walk        Boat               (transports exist)
                                           |
                                      Carrier Positioning
                                      /              \
                              Carrier Group    Fighter Coverage
                              (BB + Sub)       (needs carriers as
                                                refueling sites)
                                      \              /
                                  Consolidated Production
                                  (final integration)
```

## Phases

### Phase 1: Independent Units (no new dependencies)

These four can be implemented in any order or in parallel. Each only requires the existing country system and transport infrastructure.

**1a. Satellite** (simplest — partial infrastructure exists)
- Add `:direction` field to satellite initial state
- Stamp random direction on computer satellite spawn
- Add straight-line movement for computer satellites
- Add production gate: >15 cities, max 1 alive
- Update production priority in `computer/production.cljc`
- Tests: direction stamping, straight-line flight, production gate

**1b. Patrol Boat**
- Add patrol fields to `units/ships.cljc`: `:patrol-country-id`, `:patrol-direction`, `:patrol-mode`
- Add coastal patrol logic to `computer/ship.cljc`: homing, coastline following from sea side, direction reversal at map edge
- Add flee-from-non-transports, attack-transports behavior
- Add production: 1 per country, replace on death
- Tests: coastal patrol, reversal, transport attack, flee, production cap

**1c. Army Coastline Exploration**
- Add `:coast-walk` mode with `:coast-direction`, `:coast-start`, `:coast-visited` fields
- Add coast-walk movement to `computer/army.cljc`: land/sea boundary following, handedness, backtrack memory (10 positions)
- Track `:coast-walkers-produced` per country
- Stamp coast-walk on first 2 armies per country at spawn
- Termination: map edge or return to start → switch to `:explore`
- Tests: clockwise/counter-clockwise, backtrack avoidance, termination conditions

**1d. Destroyer Escort**
- Add escort fields to destroyer in `units/ships.cljc` and transport in `units/transport.cljc`
- Add escort state machine to `computer/ship.cljc`: `:seeking` → `:intercepting` → `:escorting` (+ `:repairing`)
- Add movement mirroring, landing avoidance (seaward side during unload)
- Add adoption/pairing logic, clear on death in `combat.cljc`
- Add production: global cap destroyers <= transports
- Tests: adoption, interception, escort mirroring, landing avoidance, death handling

### Phase 2: Carrier Positioning

Depends on nothing new, but must be complete before Phase 3.

**2. Carrier Positioning**
- Add `:carrier-mode` and `:carrier-target` to `units/carrier.cljc`
- Implement position-finding algorithm: within fighter fuel range of refueling site, 80% spacing from all other sites, first valid position found
- Add state machine in `computer/ship.cljc`: `:positioning` → `:holding`, `:repositioning`, `:repairing`
- Add redundancy detection (new city makes position unnecessary)
- Add production: >10 cities, max 2 cities producing, valid position exists
- Add carrier spacing constant to `config.cljc`
- Tests: position finding, spacing enforcement, redundancy, repositioning

### Phase 3: Carrier-Dependent Units

Both depend on carriers being positioned as refueling sites.

**3a. Carrier Group (Battleship + Submarine Escorts)**
- Add group fields to carrier: `:group-battleship-id`, `:group-submarine-ids`
- Add escort fields to battleship and submarine in `units/ships.cljc`
- Implement orbit at radius 2 (Chebyshev ring, 120-degree spacing)
- Add escort state machine: `:seeking` → `:intercepting` → `:orbiting` (+ `:attacking`, `:repairing`)
- All escorts break formation on enemy detection; carrier holds
- Carrier movement → escorts follow, maintain orbit
- Add production: battleships <= carriers, submarines <= 2 * carriers
- Clear pairings on death in `combat.cljc`
- Tests: orbit, spacing, combat break/return, carrier follow, death handling

**3b. Fighter Coverage**
- Add `fighter-leg-records` atom to `atoms.cljc`
- Add `:flight-target-site` and `:flight-origin-site` to fighter
- Implement leg selection: enumerate reachable refueling sites, pick unflown or oldest leg
- Implement in-flight behavior: prefer unexplored cells, maintain fuel budget to reach target
- Record leg completion on landing (update `:last-flown` round)
- Clean up invalid legs when refueling sites are destroyed
- Tests: leg selection (unflown first, oldest), fuel management, leg recording, cleanup

### Phase 4: Production Consolidation

After all unit behaviors are implemented, replace the ratio-based system entirely.

**4. Consolidated Production Priority**
- Replace ratio tables and deficit calculations in `computer/production.cljc`
- Implement the full priority chain: transport → army → patrol boat → destroyer → carrier → battleship → submarine → satellite → fighter
- Handle inland vs coastal city constraints
- Add once-per-round liveness scan in `round_setup.cljc`
- Remove dead ratio code
- Tests: full priority order, inland city skipping, all caps enforced, liveness reconciliation

## Estimated Scope Per Phase

| Phase | Files Created | Files Modified | New Tests |
|-------|:------------:|:--------------:|:---------:|
| 1a. Satellite | 0 | 4 | ~5 |
| 1b. Patrol Boat | 0 | 5 | ~8 |
| 1c. Army Coast-Walk | 0 | 4 | ~8 |
| 1d. Destroyer Escort | 0 | 5 | ~10 |
| 2. Carrier Positioning | 0 | 5 | ~8 |
| 3a. Carrier Group | 0 | 5 | ~10 |
| 3b. Fighter Coverage | 0 | 5 | ~8 |
| 4. Production Consolidation | 0 | 3 | ~10 |

## Build-Test Cadence

Each phase item should follow:
1. Write tests for the new behavior
2. Implement the behavior
3. Run `clj -M:spec` to verify all tests pass
4. Commit

Phase 1 items can be done in parallel branches if desired, but serial implementation is simpler. Each builds on the same production infrastructure without conflicts.
