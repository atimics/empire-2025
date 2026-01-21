# Phase 3: Transport Operations and Smart Production

## Goal
Computer uses transports to move armies across water and makes intelligent production decisions based on game state.

---

## 3.1 Transport Operations

Transports are critical for conquest - armies need them to cross water to reach enemy territory.

### Transport Behavior State Machine

```
EMPTY at city     → Load armies → LOADING
LOADING           → Full or timeout → EMBARKING
EMBARKING         → Navigate to target shore → EN_ROUTE
EN_ROUTE          → Adjacent to land → DISEMBARKING
DISEMBARKING      → All armies unloaded → Return to base → EMPTY
```

### New Transport Fields

Add to transport unit state:
```clojure
:transport-mission  ; :idle, :loading, :en-route, :unloading
:transport-target   ; Target shore/city coordinates
:loading-timeout    ; Rounds spent waiting at dock
```

### Loading Logic

**File: `src/empire/computer.cljc`**

```clojure
(defn- find-armies-needing-transport
  "Finds computer armies on same cell or adjacent to transport that need transport."
  [transport-pos]
  ...)

(defn- find-loading-dock
  "Finds nearest computer city adjacent to water for loading."
  [transport-pos]
  ...)

(defn- should-start-loading?
  "Returns true if transport should move to dock and start loading."
  [transport-pos transport]
  (and (zero? (:army-count transport 0))
       (= (:transport-mission transport :idle) :idle)))

(defn- load-adjacent-army
  "Loads an adjacent computer army onto the transport."
  [transport-pos]
  ...)
```

### Target Selection

```clojure
(defn- find-invasion-target
  "Finds best shore position near enemy/free city for invasion.
   Prefers positions adjacent to free cities, then player cities."
  []
  (let [free-cities (find-visible-cities #{:free})
        player-cities (find-visible-cities #{:player})]
    ;; Find shore tiles (sea adjacent to land) near target cities
    ...))

(defn- find-shore-near-city
  "Finds sea cells adjacent to land near target city."
  [city-pos]
  ...)
```

### Unloading Logic

```clojure
(defn- find-disembark-target
  "Finds adjacent land cell for army disembarkation."
  [transport-pos]
  (first (filter (fn [neighbor]
                   (let [cell (get-in @atoms/game-map neighbor)]
                     (and (= :land (:type cell))
                          (nil? (:contents cell)))))
                 (get-neighbors transport-pos))))

(defn- disembark-army
  "Disembarks one army from transport to adjacent land."
  [transport-pos land-pos]
  ...)
```

### Updated `decide-transport-move`

```clojure
(defn decide-transport-move
  "Decides transport movement based on mission state."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)
        mission (:transport-mission transport :idle)
        army-count (:army-count transport 0)]
    (case mission
      :idle
      (if (pos? army-count)
        ;; Has armies but no mission - find invasion target
        (when-let [target (find-invasion-target)]
          (set-transport-mission pos :en-route target)
          (pathfinding/next-step pos target :transport))
        ;; Empty - find dock to load
        (when-let [dock (find-loading-dock pos)]
          (pathfinding/next-step pos dock :transport)))

      :loading
      ;; Stay at dock, load armies, start mission when full or timeout
      (cond
        (>= army-count 6) ; Transport capacity
        (do (set-transport-mission pos :en-route (find-invasion-target))
            nil) ; Stay put this turn, move next turn

        (> (:loading-timeout transport 0) 3)
        (when (pos? army-count)
          (set-transport-mission pos :en-route (find-invasion-target))
          nil)

        :else
        (do (increment-loading-timeout pos)
            (load-adjacent-army pos)
            nil))

      :en-route
      (let [target (:transport-target transport)]
        (if (adjacent-to-land? pos)
          (do (set-transport-mission pos :unloading target)
              nil)
          (pathfinding/next-step pos target :transport)))

      :unloading
      (if (zero? army-count)
        (do (set-transport-mission pos :idle nil)
            ;; Return to base
            (when-let [base (find-nearest-friendly-city pos)]
              (pathfinding/next-step pos base :transport)))
        (when-let [land (find-disembark-target pos)]
          (disembark-army pos land)
          nil))

      ;; Default - treat as idle
      nil)))
```

---

## 3.2 Smart Production

Cities should produce units based on strategic needs, not just armies.

### Production Strategy

```clojure
(defn- count-computer-units
  "Counts computer units by type."
  []
  (let [units (for [i (range (count @atoms/game-map))
                    j (range (count (first @atoms/game-map)))
                    :let [cell (get-in @atoms/game-map [i j])
                          unit (:contents cell)]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

(defn- city-is-coastal?
  "Returns true if city has adjacent sea cells."
  [city-pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors city-pos)))

(defn- need-transports?
  "Returns true if we need more transports for invasion."
  [unit-counts]
  (let [armies (get unit-counts :army 0)
        transports (get unit-counts :transport 0)]
    ;; Need transport if we have armies but few transports
    (and (> armies 3)
         (< transports (quot armies 4)))))

(defn- need-fighters?
  "Returns true if we need air support."
  [unit-counts]
  (let [fighters (get unit-counts :fighter 0)]
    (< fighters 2)))

(defn- need-warships?
  "Returns true if we need naval combat vessels."
  [unit-counts]
  (let [destroyers (get unit-counts :destroyer 0)
        patrol-boats (get unit-counts :patrol-boat 0)]
    (< (+ destroyers patrol-boats) 2)))
```

### Updated `decide-production`

```clojure
(defn decide-production
  "Decides what a computer city should produce based on strategic needs."
  [city-pos]
  (let [unit-counts (count-computer-units)
        coastal? (city-is-coastal? city-pos)]
    (cond
      ;; Coastal cities can build naval units
      (and coastal? (need-transports? unit-counts))
      :transport

      (and coastal? (need-warships? unit-counts))
      (rand-nth [:destroyer :patrol-boat])

      (need-fighters? unit-counts)
      :fighter

      ;; Default to armies
      :else
      :army)))
```

---

## 3.3 Army Coordination with Transports

Armies should move toward transports/docks when invasion is planned.

### Army Awareness of Transports

```clojure
(defn- find-loading-transport
  "Finds a transport in loading state that has room."
  []
  (first (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :transport (:type unit))
                          (= :loading (:transport-mission unit))
                          (< (:army-count unit 0) 6))]
           [i j])))

(defn- army-should-board-transport?
  "Returns true if army should move toward a loading transport."
  [army-pos]
  (let [free-cities (find-visible-cities #{:free})
        player-cities (find-visible-cities #{:player})
        reachable-targets (filter #(pathfinding/next-step army-pos % :army)
                                   (concat free-cities player-cities))]
    ;; Board transport if no cities reachable by land
    (empty? reachable-targets)))
```

### Updated `decide-army-move`

Add transport boarding logic:

```clojure
(defn decide-army-move [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        adjacent-target (find-adjacent-target pos)
        adjacent-transport (find-adjacent-loading-transport pos)
        passable (find-passable-neighbors pos)]
    (cond
      adjacent-target adjacent-target

      ;; Board adjacent transport if no land route to targets
      (and adjacent-transport (army-should-board-transport? pos))
      adjacent-transport  ; Move onto transport

      (should-retreat? pos unit @atoms/computer-map)
      (retreat-move pos unit @atoms/computer-map passable)

      (empty? passable) nil

      ;; Move toward loading transport if should board
      (army-should-board-transport? pos)
      (when-let [transport (find-loading-transport)]
        (or (pathfinding/next-step pos transport :army)
            (move-toward-city-or-explore pos passable)))

      :else
      (move-toward-city-or-explore pos passable))))
```

---

## 3.4 Files to Modify/Create

| File | Changes |
|------|---------|
| `src/empire/computer.cljc` | Transport mission logic, smart production, army-transport coordination |
| `spec/empire/computer_spec.clj` | Tests for transport operations and production strategy |

---

## 3.5 Implementation Order (Test-First)

| Step | Test | Implementation |
|------|------|----------------|
| 1 | `city-is-coastal?` returns true for coastal cities | Helper function |
| 2 | `count-computer-units` counts all unit types | Helper function |
| 3 | `need-transports?` returns true when army/transport ratio high | Production helper |
| 4 | `decide-production` returns `:transport` for coastal city when needed | Smart production |
| 5 | `decide-production` returns `:fighter` when air support needed | Smart production |
| 6 | `decide-production` returns `:army` by default | Smart production |
| 7 | `find-loading-dock` finds coastal city | Transport helper |
| 8 | `find-invasion-target` finds shore near enemy city | Transport helper |
| 9 | `find-disembark-target` finds adjacent land | Transport helper |
| 10 | Transport moves to dock when empty | Transport mission |
| 11 | Transport loads adjacent armies | Transport mission |
| 12 | Transport navigates to invasion target when loaded | Transport mission |
| 13 | Transport unloads armies on shore | Transport mission |
| 14 | Transport returns to base when empty | Transport mission |
| 15 | Army boards adjacent transport when no land route | Army coordination |
| 16 | Army moves toward loading transport | Army coordination |

---

## 3.6 Testing Strategy

### Production Tests

```clojure
(describe "city-is-coastal?"
  (it "returns true when city has adjacent sea"
    ;; City with sea neighbor
    )
  (it "returns false for inland city"
    ;; City surrounded by land
    ))

(describe "decide-production"
  (it "returns :transport at coastal city when armies outnumber transports"
    ;; 8 armies, 1 transport → need transport
    )
  (it "returns :army at inland city even when transports needed"
    ;; Inland cities can't build ships
    )
  (it "returns :fighter when no fighters exist"
    )
  (it "returns :army by default"
    ))
```

### Transport Tests

```clojure
(describe "transport loading"
  (it "transport moves toward dock when empty"
    )
  (it "transport loads adjacent army"
    )
  (it "transport starts mission when full"
    )
  (it "transport starts mission after timeout with partial load"
    ))

(describe "transport navigation"
  (it "transport uses A* to reach invasion target"
    )
  (it "transport switches to unloading when adjacent to land"
    ))

(describe "transport unloading"
  (it "transport disembarks army to adjacent land"
    )
  (it "transport returns to base when empty"
    ))
```

### Army-Transport Coordination Tests

```clojure
(describe "army transport coordination"
  (it "army boards adjacent loading transport when no land route"
    )
  (it "army moves toward loading transport when should board"
    )
  (it "army ignores transport when land route exists"
    ))
```

---

## 3.7 Edge Cases

1. **No coastal cities** - Computer is landlocked, only produces armies
2. **Transport blocked** - Can't reach shore, circles back or waits
3. **All targets conquered** - Transport returns to base
4. **Army loaded but transport destroyed** - Armies die with transport
5. **Multiple transports** - Each operates independently
6. **Transport under attack while loading** - Should retreat if damaged
7. **No room on transport** - Army waits or finds another transport

---

## 3.8 Performance Considerations

1. **Cache coastal status** - Don't recalculate each production decision
2. **Limit transport pathfinding** - Max 30 steps to avoid long computations
3. **Batch unit counting** - Count once per round, not per decision

---

## 3.9 Future Enhancements (Phase 4+)

- Carrier operations (launch/recover fighters)
- Fleet coordination (escorts for transports)
- Submarine hunting
- Strategic target prioritization
- Multi-front warfare
