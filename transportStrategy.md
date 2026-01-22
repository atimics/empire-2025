# Computer Transport Strategy

## Problem

Computer transports loaded with armies need to find a **different continent** to disgorge armies. With fog of war, this is challenging. The current implementation navigates to pre-planned target beaches near visible cities, which doesn't work well when targets aren't visible.

## Implemented Behavior

1. **Depart** - Move away from loading beach into open sea
2. **Choose direction** - Calculate initial explore direction away from origin beach
3. **Explore in direction** - Continue moving in the chosen direction until land is encountered
4. **Same-continent detection** - When land is found, use pathfinding to check if the land has a path back to the origin beach (same continent)
5. **If same continent** - Pick a new direction and continue exploring
6. **If different continent** - Switch to coastline-searching mode
7. **Coastline hug** - Follow the coastline of the new land, searching for a beach
8. **Beach detection** - As soon as a valid unloading beach is found (adjacent to land where armies can disembark), **STOP immediately** and unload

## Implementation Details

### Transport State `:exploring`

After `:departing`, the transport switches to `:exploring`:
- Stores `:explore-direction` as a `[dr dc]` vector (e.g., `[1 0]` for moving south)
- Direction is calculated to move away from the origin beach

### Exploring Movement Logic

In `:exploring` state:
- Move in the stored direction each turn
- When adjacent to land, use `land-path-to-beach?` to check connectivity:
  - Uses army pathfinding to test if any adjacent land cell can reach land near the origin beach
  - If path exists → same continent → pick new direction (avoiding current and opposite), continue exploring
  - If no path exists → different continent → switch to `:coastline-searching`
- If blocked but not by land, pick a new direction

### Transport State `:coastline-searching`

Similar to the player's coastline-follow mode:
- Hug the coastline, preferring unvisited and unexplored areas
- Track visited positions to avoid cycling
- Check every position for valid unloading beach

### Beach Detection and Unloading

A valid unloading beach requires:
- **3+ adjacent land/city cells** (same as loading beaches)
- **No adjacent player cities** (enemy cities for computer)
- **At least one empty land cell** to unload armies to

At each step of `:coastline-searching`:
- Check if current position meets all unloading beach requirements
- If valid beach found → **immediately** switch to `:unloading`

### Multi-Army Unloading

In `:unloading` state:
- Find ALL adjacent empty land cells
- Unload one army to each available cell in a **single round**
- Armies are placed in explore mode to fan out and discover the new continent
- When all armies unloaded, switch to `:returning`

## State Machine

```
:idle → :seeking-beach → :loading → :departing → :exploring → :coastline-searching → :unloading → :returning
                                                      ↑                            |
                                                      |____________________________|
                                                      (if land is same continent,
                                                       pick new direction and keep exploring)
```

## Key Functions

- `calculate-explore-direction` - Computes initial direction away from origin beach
- `land-path-to-beach?` - Uses army pathfinding to check if land connects to origin
- `pick-new-explore-direction` - Selects new direction when blocked or same continent
- `transport-move-exploring` - Main exploring state handler
- `transport-move-coastline-searching` - Coastline hugging state handler
- `can-unload-at?` - Checks if position is valid unloading beach (3+ land, no player city, empty land)
- `find-all-disembark-targets` - Returns all adjacent empty land cells for multi-army unloading
- `transport-move-unloading` - Unloads multiple armies per round

## Files Modified

- `src/empire/computer.cljc` - New states and movement logic
- `spec/empire/computer_spec.clj` - Tests for new behavior
