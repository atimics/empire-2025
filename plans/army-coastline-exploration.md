# Plan: Army Coastline Exploration

## Summary

The first two computer armies produced on a continent enter coast-walk mode. One walks clockwise (sea on right), the other counter-clockwise (sea on left). They hug the land/sea boundary, preferring moves that expose unexplored territory, avoiding backtracking (memory of 10 positions). They stop at a map edge or their starting point, then switch to regular explore mode.

## Behavior Rules

1. **Activation:** The first army produced on a continent enters coast-walk mode clockwise (sea on right). The second enters coast-walk counter-clockwise (sea on left). Armies 3+ use regular explore.

2. **Movement priority:** Each step, prefer the land cell that:
   - Is adjacent to sea (maintains coast-hugging)
   - Exposes the most unexplored territory
   - Is NOT in the 10-position backtrack memory

3. **Handedness:** Clockwise walkers keep sea on their right. Counter-clockwise keep sea on their left. This determines turning preference when multiple coast-adjacent cells are available.

4. **Termination:** The army stops coast-walking when:
   - It reaches a map edge (no valid coast-adjacent move), OR
   - It returns to its starting position (full circuit)

   On termination, the army switches to regular `:explore` mode.

5. **Backtrack memory:** The army remembers its last 10 positions. It prefers moves not in this set. If all coast-adjacent moves are in the backtrack set, it picks the oldest one (least recently visited).

## New Unit Fields

- `:mode :coast-walk` — new army mode
- `:coast-direction :clockwise` or `:counter-clockwise` — handedness
- `:coast-start` — starting position `[row col]` for circuit detection
- `:coast-visited` — vector of last 10 positions (ring buffer)

## New Country Field

- `:coast-walkers-produced` — count (0, 1, or 2) of coast-walk armies produced on this country. Resets never — once 2 have been produced, all subsequent armies explore normally.

## Integration Points

### Production (`player/production.cljc`)

When a computer army is spawned, check the producing city's country's `:coast-walkers-produced` count:
- 0 → set army mode to `:coast-walk`, direction `:clockwise`, increment count
- 1 → set army mode to `:coast-walk`, direction `:counter-clockwise`, increment count
- 2+ → normal army (`:awake` or `:explore` per existing logic)

### Movement (`computer/army.cljc`)

Add a `:coast-walk` branch to army processing. Each step:
1. Find all land neighbors adjacent to sea
2. Filter out non-land cells, cells with units
3. Score by: unexplored cells revealed (higher = better), not in backtrack memory (bonus)
4. Apply handedness tie-breaking (prefer the turn direction matching clockwise/counter-clockwise)
5. Move to best candidate
6. Update backtrack memory (keep last 10)
7. Check termination: at map edge (no valid next move) or back at `:coast-start`
8. On termination: switch to `:explore` mode with standard explore-steps

### Shared Logic with `movement/coastline.cljc`

The existing coastline module handles ship coast-following from the sea side. The army version is the mirror. Potentially share:
- Direction rotation logic (clockwise/counter-clockwise turn order)
- Neighbor scoring by unexplored visibility
- Handedness determination

Or keep separate — ships and armies have different terrain constraints (sea vs land), so sharing may add complexity without much savings.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/army.cljc` | Add coast-walk movement logic |
| `src/empire/player/production.cljc` | Stamp coast-walk mode on first 2 armies per country |
| `src/empire/movement/wake_conditions.cljc` | Handle `:coast-walk` mode in needs-attention checks |
| `src/empire/units/army.cljc` | Add `:coast-walk` to valid modes if needed |
| `src/empire/units/dispatcher.cljc` | Update `needs-attention?` for coast-walk armies |
| `spec/empire/computer/army_spec.clj` | Tests for coast-walk movement, termination, handedness |
| `spec/empire/player/production_spec.clj` | Tests for coast-walker assignment on first 2 armies |
