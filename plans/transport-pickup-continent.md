# Plan: Transport & Country Production Management

## Part 1: Country System

Replace the ephemeral flood-fill continent model with persistent **countries** — production management boundaries, not geographic ones. Two countries can exist on the same physical landmass and never merge.

### New Atoms

- `next-country-id` — auto-incrementing unique ID for countries.
- `next-unload-event-id` — auto-incrementing unique ID for transport unload cycles.

### Country ID Assignment Rules

1. The computer's first city gets country-id 1. Cells explored outward from it get stamped with that country-id.
2. Cities produce armies with the city's country-id.
3. When an army boards a transport, it ceases to exist (becomes `army-count`). Country-id is implicitly erased.
4. When a transport transitions to `:unloading`, a new unload-event-id is minted. Each army created during that unload cycle gets that unload-event-id (no country-id).
5. When an army with an unload-event-id conquers a city:
   - A new country-id is minted.
   - The conquered city gets the new country-id.
   - **All** armies on the map with that same unload-event-id get the new country-id (and lose the unload-event-id).
6. When an army with a country-id conquers a city, the city gets that country-id.

### New Unit/Cell Fields

- Army: `:country-id` (from producing city) OR `:unload-event-id` (from transport unload). Never both.
- City: `:country-id` (assigned on conquest or at game start).
- Cell: `:country-id` (stamped during exploration from a city's country).
- Transport: `:unload-event-id` (minted when transitioning to `:unloading`, stamped onto each unloaded army).

---

## Part 2: Country Production Management

### Production Decision Priority

When a computer city finishes production and needs to decide what to build next, check in order:

1. **Transports:** Does this city's country have fewer than 2 live transports? If yes, produce a transport (coastal cities only).
2. **Armies:** Does this city's country have fewer than 10 armies? And is no other city in the country already producing armies? If yes, produce an army.
3. **Everything else:** Use existing ratio-based system for fighters, destroyers, subs, battleships, patrol-boats.

### Key Rules

- **Never cancel in-progress production.** All checks happen at the natural decision point (when a city finishes its current item).
- **2 transports per country** regardless of country size.
- **10 armies per country** cap. If below 10 and no city in the country is producing armies, the next city to finish redirects to armies.
- Countries track their transport IDs. Once per round, check whether transports are alive. Dead transports are removed from the country's records.

### Transport Liveness Check

Once per round (in `start-new-round` or at computer city processing time), scan the map for living computer transports. Compare against each country's recorded transport IDs. Remove dead ones. The next city in that country to finish production will see the deficit and produce a replacement.

---

## Part 3: Remove Dead Code

The following are remnants of a previous transport AI design. All are dead code — defined but never populated during gameplay.

### Dead Atoms (in `atoms.cljc`)

- `reserved-beaches` (line 118-121) — written to on transport death, but never read.
- `beach-army-orders` (line 123-127) — defined, read functions exist, but never populated.

### Dead Functions (in `player/production.cljc`)

- `find-beach-order-for-city` (lines 5-9) — reads `beach-army-orders`, always returns nil.
- `apply-beach-order-to-army` (lines 11-23) — calls `find-beach-order-for-city`, always returns unit unchanged.
- `apply-computer-army-beach-order` (lines 68-73) — wrapper, always returns unit unchanged.

### Dead Field

- `:origin-beach` set to `nil` on transport spawn (line 46 of `player/production.cljc`), never read or updated.

### Dead Function (in `player/combat.cljc`)

- `release-transport-beach-reservation` (lines 135-143) — writes to `reserved-beaches` which is never read. Called from `attempt-attack` (line 178).

### Dead Resets (in `test_utils.cljc`)

- `(reset! atoms/reserved-beaches {})` (line 160)
- `(reset! atoms/beach-army-orders {})` (line 161)

### Removal Plan

1. Delete `reserved-beaches` and `beach-army-orders` atoms from `atoms.cljc`.
2. Delete `find-beach-order-for-city`, `apply-beach-order-to-army`, and `apply-computer-army-beach-order` from `player/production.cljc`.
3. Remove the `apply-computer-army-beach-order` call from `spawn-unit` (line 84) — just remove that step from the threading macro.
4. Remove `:origin-beach nil` from `apply-unit-type-attributes` (line 46).
5. Delete `release-transport-beach-reservation` from `player/combat.cljc`.
6. Remove the call to `release-transport-beach-reservation` from `attempt-attack` (line 178).
7. Remove both resets from `test_utils.cljc`.

---

## Summary of All Files Modified

| File | Change |
|------|--------|
| `src/empire/atoms.cljc` | Add `next-country-id`, `next-unload-event-id`. Remove `reserved-beaches`, `beach-army-orders`. |
| `src/empire/computer/transport.cljc` | Add unload-event-id minting. Country-aware pickup-continent-pos update on full unload. |
| `src/empire/computer/production.cljc` | New production decision logic: transports (cap 2), armies (cap 10), then ratio-based. |
| `src/empire/computer/continent.cljc` | May need army-counting helpers per country. Possibly rename or supplement with country-aware functions. |
| `src/empire/player/production.cljc` | Remove dead beach-order functions. Remove `:origin-beach nil`. |
| `src/empire/player/combat.cljc` | Remove `release-transport-beach-reservation` and its call from `attempt-attack`. |
| `src/empire/test_utils.cljc` | Add resets for new atoms. Remove resets for dead atoms. |
| `spec/empire/computer/transport_spec.clj` | Add tests for unload-event-id and pickup-continent update. |
| `spec/empire/computer/production_spec.clj` | Add tests for country-aware production decisions (transport cap, army cap, priority order). |

## Resolved Questions

| # | Question | Decision |
|---|----------|----------|
| Q1 | How to anchor continent identity? | Replace with persistent **country** system. Countries never merge. |
| Q2 | Cancel production to replace transport? | No. Never cancel in-progress production. |
| Q3 | 2-transport cap regardless of size? | Yes. Exactly 2 per country. |
| Q4 | Does "redirect" mean cancel? | No. Redirect at next natural decision point only. |
| Q5 | What about other unit types? | Priority: transports > armies > ratio-based for the rest. |
| Q6 | Beach-army-orders conflict? | N/A. Dead code. Remove it. |
