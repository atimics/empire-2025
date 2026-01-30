# Plan: Consolidated Production Priority

## Overview

This plan consolidates all production rules from the individual unit plans into a single reference. It replaces the existing ratio-based production system in `computer/production.cljc` with a priority-based system where each unit type has explicit caps and conditions.

**Core rule:** Never cancel in-progress production. All checks happen at the natural decision point — when a city finishes its current item and needs to decide what to build next.

## Priority Order

When a computer city finishes production, check these in order. Produce the first unit whose conditions are met.

| Priority | Unit | Scope | Conditions | Coastal Only? |
|:--------:|------|-------|------------|:-------------:|
| 1 | Transport | Per-country | Country has < 2 live transports | Yes |
| 2 | Army | Per-country | Country has < 10 armies AND no other city in the country is producing an army | No |
| 3 | Patrol Boat | Per-country | Country has no live patrol boat AND no city in the country is producing one | Yes |
| 4 | Destroyer | Global | Total destroyers < total transports AND country has an unadopted transport | Yes |
| 5 | Carrier | Global | Computer owns > 10 cities AND < 2 cities producing carriers AND a valid carrier position exists | Yes |
| 6 | Battleship | Global | Total battleships < total carriers AND a carrier has no battleship escort | Yes |
| 7 | Submarine | Global | Total submarines < 2 * total carriers AND a carrier has < 2 submarine escorts | Yes |
| 8 | Satellite | Global | Computer owns > 15 cities AND no live satellite exists | No |
| 9 | Fighter | None | Ratio-based fallback — always available | No |

If an inland city reaches a priority that requires a coastal city, it skips that priority and continues down the list.

## Detailed Conditions

### 1. Transport (Per-Country, Cap 2)
- Count live transports belonging to this city's country.
- If < 2, produce a transport.
- Country tracks its transport IDs; liveness checked once per round.

### 2. Army (Per-Country, Cap 10)
- Count live armies with this city's country-id on the map.
- If < 10 AND no other city in the country currently has `{:item :army}` in the production atom, produce an army.
- The "no other city producing" rule prevents all cities from simultaneously switching to armies.
- First 2 armies per country get `:coast-walk` mode (1 clockwise, 1 counter-clockwise). Armies 3+ are normal.

### 3. Patrol Boat (Per-Country, Cap 1)
- Each country maintains exactly 1 patrol boat.
- If the country has no live patrol boat and no city is producing one, produce a patrol boat.
- Country tracks its patrol-boat-id.

### 4. Destroyer (Global, Cap = Transports)
- Count all live computer destroyers and transports globally.
- If destroyers < transports AND this city's country has a transport with no escort, produce a destroyer.
- The destroyer will adopt the unadopted transport after production.

### 5. Carrier (Global, Threshold + Position)
- Three conditions must all be true:
  1. Computer owns > 10 cities globally.
  2. Fewer than 2 cities are currently producing carriers.
  3. A valid carrier position exists (within fighter fuel range of a refueling site, at least 80% fuel range from all other sites).
- No fixed cap — naturally bounded by available positions.

### 6. Battleship (Global, Cap = Carriers)
- Count all live computer battleships and carriers globally.
- If battleships < carriers AND a carrier has an open battleship slot, produce a battleship.
- The battleship will adopt the carrier with the open slot.

### 7. Submarine (Global, Cap = 2 * Carriers)
- Count all live computer submarines and carriers globally.
- If submarines < 2 * carriers AND a carrier has < 2 submarine escorts, produce a submarine.
- The submarine will adopt the carrier with an open slot.

### 8. Satellite (Global, Cap 1, Threshold)
- Two conditions:
  1. Computer owns > 15 cities globally.
  2. No live computer satellite exists.
- On spawn, the satellite picks a random direction (1 of 8) and flies straight until expiry.

### 9. Fighter (Ratio-Based Fallback)
- If no higher-priority unit is needed, produce a fighter.
- Fighters use the leg-based coverage system — no production cap needed, naturally balanced by available legs in the refueling network.
- This is the default production choice when all caps are satisfied.

## Inland Cities

Inland cities (not adjacent to sea) can only produce:
- **Army** (priority 2)
- **Satellite** (priority 8)
- **Fighter** (priority 9)

They skip all coastal-only priorities (1, 3, 4, 5, 6, 7).

## Liveness Checks

Once per round (in `start-new-round`), scan the map for living units and reconcile against country/global records:
- Per-country: transport IDs, patrol-boat-id, army count, coast-walkers-produced
- Global: destroyer count, carrier count, battleship count, submarine count, satellite alive

Dead units are removed from records. The next city to finish production will see the deficit.

## What This Replaces

The existing ratio-based system (`ratio-1-2-cities`, `ratio-3-4-cities`, etc.) is replaced entirely. The ratio tables and deficit calculations in `computer/production.cljc` lines 44-135 are no longer needed. Fighters become the simple fallback at the bottom of the priority list.

The existing continent-needs-army-producer check (lines 99-105) is replaced by the per-country army cap of 10.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/production.cljc` | Replace ratio-based system with priority-based decision logic |
| `src/empire/atoms.cljc` | Country records, global unit tracking atoms |
| `src/empire/config.cljc` | Production constants: caps, thresholds |
| `spec/empire/computer/production_spec.clj` | Tests for each priority level, inland vs coastal, cap enforcement |
