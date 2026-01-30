# VMS Mechanics Implementation Plan

Implement three VMS Empire mechanics missing from the Clojure port:
1. City conquest flips ownership of ships/fighters
2. Damage-based speed scaling
3. Damage-based capacity scaling with cargo drowning

---

## 1. City Conquest: Flip Ownership of Ships/Fighters

### VMS Behavior (object.c `kill_city()`)
When a city is conquered:
- **Armies at the city are killed** (including armies inside transports)
- **Ships and fighters flip ownership** to the conqueror (UNLINK from old owner's list, LINK to new owner's list)
- **Satellites are unaffected**
- **Production state is reset** (work=0, prod=NOPIECE, all standing orders cleared)
- The attacking army is always consumed ("dispersed to enforce control")

### Current Clojure Behavior (`combat.cljc` `attempt-conquest`)
- Attacking army is removed
- City status flips to `:player`
- No handling of other units at the city at all
- Production is NOT cleared

### Implementation Plan

**Files to modify:**
- `src/empire/player/combat.cljc` - add unit flipping logic to `attempt-conquest`
- `src/empire/computer/core.cljc` - add same logic to `attempt-conquest-computer`

**Logic to add after city-status is set to new owner:**
1. Find all units at the conquered city (contents, plus any carried units)
2. For each unit at the city:
   - If army: remove it (kill)
   - If satellite: leave unchanged
   - If ship or fighter: flip `:owner` to the conqueror, set `:mode` to `:awake`, clear any movement orders
   - If the flipped unit is a transport: kill all armies it carries (set `:army-count` to 0, `:awake-armies` to 0)
   - If the flipped unit is a carrier: kill all fighters it carries (set `:fighter-count` to 0, `:awake-fighters` to 0)
3. Handle city airport: if the city has `:fighter-count` > 0, those fighters flip to the new owner
4. Handle city shipyard: if the city has `:shipyard` entries, those ships flip to the new owner
5. Reset city production: `(swap! atoms/production dissoc city-coords)`
6. Clear city orders: set `:marching-orders` and `:flight-path` to nil

### Detailed Implementation Notes

#### City cell structure at conquest time
A city cell can have multiple layers of units:
- `:contents` — a single unit standing on the city (army, fighter, ship, etc.)
- `:fighter-count` / `:awake-fighters` — fighters stored in the city's airport
- `:shipyard` — vector of `{:type :destroyer :hits 3}` maps for ships being repaired

#### Player conquest path
**File:** `src/empire/player/combat.cljc` lines 13-27, `attempt-conquest`

Current success branch (line 18-22):
```clojure
(swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
(swap! atoms/game-map assoc-in city-coords (assoc city-cell :city-status :player))
```

Add a helper function `conquer-city-contents` that takes city-coords and new-owner, and:
1. Reads the cell fresh from `@atoms/game-map` (after city-status already flipped)
2. Processes `:contents` — kill armies, flip ships/fighters, ignore satellites
3. For flipped transports: zero out `:army-count` and `:awake-armies` (armies inside drown)
4. For flipped carriers: zero out `:fighter-count` and `:awake-fighters` (fighters inside lost)
5. Airport fighters: these already belong to the old owner implicitly (city owned them). Just leave `:fighter-count`/`:awake-fighters` as-is — they now belong to the new city owner.
6. Shipyard ships: these are stored as minimal maps `{:type _ :hits _}` with no `:owner` field — they belong to whoever owns the city, so no change needed.
7. Clear production: `(swap! atoms/production dissoc city-coords)`
8. Clear orders: dissoc `:marching-orders` and `:flight-path` from the city cell

#### Computer conquest path
**File:** `src/empire/computer/core.cljc` lines 63-80, `attempt-conquest-computer`

Same logic as player path but with `:computer` as the new owner. Both paths should call the same shared `conquer-city-contents` function.

#### Where to put the shared function
Put `conquer-city-contents` in `combat.cljc` (or a new shared module if circular deps arise). Both `combat.cljc` and `computer/core.cljc` call it.

**Tests:**
- Conquest flips a fighter at the city to new owner
- Conquest flips a ship (destroyer, transport, carrier) at the city to new owner
- Conquest kills armies standing on the city (`:contents` with `:type :army`)
- Conquest kills armies inside a transport at the city, then flips the transport
- Conquest kills fighters inside a carrier at the city, then flips the carrier
- Satellites at the city are unchanged
- City production is cleared on conquest (`atoms/production` entry removed)
- City marching-orders and flight-path are cleared on conquest
- Airport fighters remain (count preserved) — they belong to new city owner
- Shipyard ships remain (no owner field) — they belong to new city owner
- Computer conquest applies the same logic in reverse

---

## 2. Damage-Based Speed Scaling

### VMS Behavior (object.c `obj_moves()`)
```c
speed = (base_speed * current_hits + max_hits - 1) / max_hits;  // ceiling division
```
A unit at half hits moves at roughly half speed (rounded up, minimum 1).

### Current Clojure Behavior
`game_loop.cljc` `reset-steps-remaining` always sets `steps-remaining` to the static `config/unit-speed` value regardless of hits.

### Implementation Plan

**Files to modify:**
- `src/empire/units/dispatcher.cljc` — add `effective-speed` function
- `src/empire/game_loop.cljc` — use `effective-speed` in `reset-steps-remaining`
- `src/empire/containers/ops.cljc` — use `effective-speed` when launching units
- `src/empire/computer/fighter.cljc` — replace hardcoded `fighter-speed` constant

**New function in dispatcher.cljc:**
```clojure
(defn effective-speed
  "Calculates movement speed scaled by remaining hits (VMS ceiling division)."
  [unit-type current-hits]
  (let [base-speed (speed unit-type)
        max-hits (hits unit-type)]
    (quot (+ (* base-speed current-hits) (dec max-hits)) max-hits)))
```

### Detailed Implementation Notes

#### All locations that set `:steps-remaining`

1. **`game_loop.cljc` line 116 — `reset-steps-remaining`** (round start for all player units)
   - Current: `(let [steps (or (config/unit-speed (:type unit)) 1)]`
   - Change to: `(let [steps (dispatcher/effective-speed (:type unit) (:hits unit))]`

2. **`containers/ops.cljc` line 82 — `disembark-army-from-transport`**
   - Current: `:steps-remaining (config/unit-speed :army)`
   - No change needed: armies always have 1 hit, so effective-speed = base speed

3. **`containers/ops.cljc` line 121 — `disembark-army-to-explore`**
   - Current: `:steps-remaining (config/unit-speed :army)`
   - No change needed: same reasoning as above

4. **`containers/ops.cljc` line 176 — `launch-fighter-from-carrier`**
   - Current: `:steps-remaining (dec (config/unit-speed :fighter))`
   - No change needed: fighters always have 1 hit

5. **`containers/ops.cljc` line 195 — `launch-fighter-from-airport`**
   - Current: `:steps-remaining (config/unit-speed :fighter)`
   - No change needed: fighters always have 1 hit

6. **`containers/ops.cljc` line 216 — `launch-ship-from-shipyard`**
   - Current: `:steps-remaining (dispatcher/speed (:type ship-data))`
   - **MUST CHANGE**: Ship may be partially repaired. Use `(dispatcher/effective-speed (:type ship-data) (:hits ship-data))`

7. **`game_loop.cljc` line 219 — `move-satellite-steps`**
   - Current: `steps-left (config/unit-speed :satellite)`
   - No change needed: satellites always have 1 hit

8. **`computer/fighter.cljc` line 117 — hardcoded `fighter-speed`**
   - `(def ^:private fighter-speed 8)`
   - No change needed: fighters always have 1 hit. But consider replacing the hardcoded 8 with `(dispatcher/speed :fighter)` for consistency.

#### Effective speed table for multi-hit units

| Unit | Max Hits | Base Speed | Hits | Effective Speed |
|------|----------|------------|------|-----------------|
| Destroyer | 3 | 2 | 3 | 2 |
| Destroyer | 3 | 2 | 2 | 2 (ceil 4/3) |
| Destroyer | 3 | 2 | 1 | 1 (ceil 2/3) |
| Submarine | 2 | 2 | 2 | 2 |
| Submarine | 2 | 2 | 1 | 1 |
| Carrier | 8 | 2 | 8 | 2 |
| Carrier | 8 | 2 | 4 | 1 |
| Carrier | 8 | 2 | 1 | 1 (ceil 2/8) |
| Battleship | 10 | 2 | 10 | 2 |
| Battleship | 10 | 2 | 5 | 1 |
| Battleship | 10 | 2 | 1 | 1 (ceil 2/10) |

Since all multi-hit units have base speed 2, effective speed is either 2 (healthy enough) or 1 (badly damaged). The threshold is at 50% hits (ceiling).

**Tests:**
- `effective-speed` returns base speed at full health for all unit types
- `effective-speed` returns 1 for destroyer at 1/3 hits
- `effective-speed` returns 2 for destroyer at 2/3 hits
- `effective-speed` returns 1 for submarine at 1/2 hits
- `effective-speed` returns 1 for battleship at 5/10 hits
- `effective-speed` returns 1 for carrier at 4/8 hits
- Units with max-hits=1 always get base speed (army=1, fighter=8, etc.)
- `reset-steps-remaining` uses effective speed (integration test with damaged ship on map)
- `launch-ship-from-shipyard` uses effective speed for partially-repaired ships

---

## 3. Damage-Based Capacity Scaling and Cargo Drowning

### VMS Behavior (object.c `obj_capacity()`, attack.c `survive()`)
```c
capacity = (base_capacity * current_hits + max_hits - 1) / max_hits;  // ceiling division
```
After combat, if cargo count exceeds new capacity, excess cargo is killed one at a time ("fell overboard and drowned").

### Current Clojure Behavior
Capacity is a hardcoded constant. No scaling. No drowning mechanic.

### Implementation Plan

**Files to modify:**
- `src/empire/units/dispatcher.cljc` — add `effective-capacity` function
- `src/empire/player/combat.cljc` — add cargo drowning after combat in `attempt-attack`
- `src/empire/containers/ops.cljc` — use effective-capacity in `load-adjacent-sentry-armies`
- `src/empire/movement/movement.cljc` — use effective-capacity in `fighter-landing-carrier?`
- `src/empire/movement/wake_conditions.cljc` — use effective-capacity in `fighter-landing-on-carrier?`
- `src/empire/units/transport.cljc` — update `full?` to accept dynamic capacity
- `src/empire/units/carrier.cljc` — update `full?` to accept dynamic capacity

**New function in dispatcher.cljc:**
```clojure
(defn effective-capacity
  "Calculates cargo capacity scaled by remaining hits (VMS ceiling division)."
  [unit-type current-hits]
  (let [base-cap (capacity unit-type)
        max-h (hits unit-type)]
    (quot (+ (* base-cap current-hits) (dec max-h)) max-h)))
```

### Detailed Implementation Notes

#### Practical impact analysis
Only carriers are meaningfully affected by capacity scaling:
- **Transport**: max-hits=1, capacity=6. At 1 hit (alive) capacity is 6. At 0 hits it's dead. No scaling ever applies.
- **Carrier**: max-hits=8, capacity=8. Capacity scales with damage.

| Carrier Hits | Effective Capacity |
|---|---|
| 8 | 8 |
| 7 | 7 |
| 6 | 6 |
| 5 | 5 |
| 4 | 4 |
| 3 | 3 |
| 2 | 2 |
| 1 | 1 |

(Carrier capacity scales 1:1 with hits since base capacity = max hits = 8.)

#### All locations referencing capacity constants

1. **`config.cljc` lines 45-46** — `transport-capacity` (6) and `carrier-capacity` (8)
   - These constants can remain as "base capacity" values but callers must switch to `effective-capacity` where a specific unit's hits are known.

2. **`containers/ops.cljc` line 18** — `load-adjacent-sentry-armies`
   - Current: `(not (uc/full? unit :army-count config/transport-capacity))`
   - Change to: `(not (uc/full? unit :army-count (dispatcher/effective-capacity :transport (:hits unit))))`
   - (In practice, transport always has 1 hit so this won't change behavior, but is correct.)

3. **`containers/ops.cljc` line 33** — same function, inner loop
   - Same change as above, using the refreshed transport from game-map.

4. **`movement/movement.cljc` line 130** — `fighter-landing-carrier?`
   - Current: `(not (uc/full? to-contents :fighter-count config/carrier-capacity))`
   - Change to: `(not (uc/full? to-contents :fighter-count (dispatcher/effective-capacity :carrier (:hits to-contents))))`
   - This is meaningful: a damaged carrier might refuse a landing fighter.

5. **`movement/wake_conditions.cljc` line 60** — `fighter-landing-on-carrier?`
   - Same change as above.

6. **`units/transport.cljc` lines 31-34** — `transport/full?`
   - Uses hardcoded `capacity`. Either remove this function (callers use `uc/full?` with effective-capacity) or add a hits parameter.

7. **`units/carrier.cljc` lines 30-33** — `carrier/full?`
   - Same issue. Check if these unit-specific `full?` functions are called anywhere; if not, they can be removed.

#### Cargo drowning implementation

In `combat.cljc` `attempt-attack`, after `resolve-combat` returns a winner:

```clojure
;; After placing survivor at target-coords, check for cargo drowning
(when (and (= :attacker (:winner result))
           (#{:transport :carrier} (:type (:survivor result))))
  (let [survivor (:survivor result)
        cap (dispatcher/effective-capacity (:type survivor) (:hits survivor))
        count-key (if (= :transport (:type survivor)) :army-count :fighter-count)
        current-count (get survivor count-key 0)
        excess (- current-count cap)]
    (when (pos? excess)
      ;; Reduce cargo count to effective capacity
      (swap! atoms/game-map update-in (conj target-coords :contents)
             assoc count-key cap)
      ;; Display drowning message
      (let [unit-name (if (= :transport (:type survivor)) "armies" "fighters")
            msg (str excess " " unit-name " fell overboard and drowned in the assault.")]
        (atoms/set-line3-message msg 3000)))))
```

Also handle the case where the **defender** wins — if the defending container took damage, check its cargo too.

VMS `survive()` is called for the winner regardless of attacker/defender role.

#### Also handle: awake counts
When reducing `:army-count` or `:fighter-count`, also reduce `:awake-armies` or `:awake-fighters` proportionally (cap at new count).

**Tests:**
- `effective-capacity` returns base capacity at full health
- `effective-capacity` returns 4 for carrier at 4/8 hits
- `effective-capacity` returns 1 for carrier at 1/8 hits
- After combat: carrier wins with 4/8 hits and 6 fighters, 2 fighters drown (fighter-count becomes 4)
- After combat: carrier wins with 4/8 hits and 3 fighters, no drowning (3 < 4)
- Drowning message is displayed with correct count
- awake-fighters is capped at new fighter-count after drowning
- Loading respects effective capacity: fighter can't land on carrier at 4/8 hits already carrying 4 fighters
- Defending carrier that takes damage also has cargo checked

---

## 4. Investigation: Fighter Overfly (Point 5)

### VMS Behavior
- **User fighters:** When directed to enter a hostile city, the game prompts: "That's never worked before, sir. Do you really want to try?" If confirmed, the fighter is destroyed ("Your fighter was shot down."). The user can cancel.
- **Computer fighters:** Cities are NOT in the `fighter_attack[]` list (`"TCFBSDPA"`), so computer fighters never voluntarily enter hostile cities.
- **There is no automatic flak/anti-aircraft mechanic.** Fighters are not shot down by merely flying adjacent to or passing through hostile city airspace. The destruction only happens when a fighter explicitly attempts to enter/attack a hostile city.
- **Pathfinding:** Fighter terrain is `".+"` (water and land only — no city characters). Enemy cities return `T_UNKNOWN` in `terrain_type()`, making them hard obstacles that pathfinding routes around. Fighters literally cannot construct a path through an enemy city.

### Current Clojure Behavior
- Fighters are **deterministically (100%) destroyed** when they attempt to move into a hostile city.
- No confirmation prompt — it's automatic and instant.
- Moving fighters **sidestep around** hostile cities (and all non-target cities) via `should-sidestep-city?` in `movement.cljc` line 193. This uses 4-round look-ahead to find the best path around.
- If no sidestep is available, the fighter wakes with reason `:fighter-over-defended-city`.
- Test coverage confirms fighters sidestep around free, player, and computer cities that aren't their target.

### Sidestep Call Chain Verification
The `should-sidestep-city?` function is **active production code**, not a holdover from old FSM logic. The full call chain for player units in `:moving` mode:

```
should-sidestep-city?          (movement.cljc:193)
  ← handle-movement-result     (movement.cljc:253)
    ← move-unit                (movement.cljc:287)
      ← move-current-unit      (game_loop.cljc:60)
        ← process-one-item     (game_loop.cljc:361, case :moving)
          ← process-player-items-batch → advance-game → update-map → Quil loop
```

This executes every frame for any player unit in `:moving` mode.

**Computer AI does not use this code path.** Computer units use `computer/core.cljc:move-unit-to`, a simple direct-move function with no sidestepping or wake conditions. Computer obstacle avoidance happens at the path-planning level (BFS/pathfinding chooses directions that avoid cities) rather than at step execution.

### Comparison
The Clojure implementation is **consistent with VMS intent** but uses a different mechanism:
- **VMS:** Enemy cities are hard obstacles in pathfinding. Fighter terrain is `".+"` (excludes cities). `terrain_type()` returns `T_UNKNOWN` for enemy cities, so no path is ever constructed through them. The avoidance happens at the pathfinding layer.
- **Clojure (player):** Pathfinding doesn't exclude cities. Instead, `should-sidestep-city?` detects the obstacle at move-execution time and reroutes with 4-round look-ahead. The avoidance happens at the movement layer.
- **Clojure (computer):** Pathfinding (BFS) picks directions that don't hit cities, similar to VMS.
- Both systems prevent fighters from traversing enemy cities during automatic movement.
- Both systems destroy fighters that are explicitly directed into enemy cities (VMS with confirmation prompt, Clojure without).

### Recommendation
The core mechanic matches. The only UX difference is the lack of a confirmation prompt when a player manually directs a fighter into an enemy city. **No changes needed** unless a confirmation prompt is desired.
