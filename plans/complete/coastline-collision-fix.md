# Coastline Collision Fix

## Problem

A player patrol boat in `:coastline-follow` mode disappeared when it collided with a player transport also in `:coastline-follow` mode. The debug log showed:

- **game-map [39,14]:** empty (`:sea` with no contents)
- **computer-map [39,14]:** patrol boat ghost (`{type:patrol-boat owner:player mode::coastline-follow}`)

The patrol boat was overwritten by another unit. The `assoc` in `move-coastline-step` overwrites any existing contents without checking.

## Root Cause

In `coastline.cljc:169-176`:

```clojure
(if-let [next-pos (pick-coastline-move coords atoms/game-map visited prev-pos)]
  (let [next-cell (get-in @atoms/game-map next-pos)
        ...
    (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
    (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
```

The position is validated in `pick-coastline-move`, but there's no re-validation before the actual move. If two units pick the same destination in the same processing batch, the second one overwrites the first.

## Fix

### 1. Add Defensive Validation in `move-coastline-step`

In `src/empire/movement/coastline.cljc`, modify `move-coastline-step` to check that the destination is still empty before moving:

```clojure
(defn- move-coastline-step
  "Moves a coastline-following unit one step. Returns new coords or nil if done."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:coastline-steps unit config/coastline-steps))
        visited (or (:visited unit) #{})
        start-pos (:start-pos unit)
        prev-pos (:prev-pos unit)]
    (if-let [pre-wake (pre-move-wake-reason coords visited start-pos)]
      (do (wake-coastline-unit coords pre-wake) nil)
      (if-let [next-pos (pick-coastline-move coords atoms/game-map visited prev-pos)]
        (let [next-cell (get-in @atoms/game-map next-pos)]
          ;; DEFENSIVE CHECK: Verify destination is still empty
          (if (:contents next-cell)
            (do
              (debug/log-action! [:collision-avoided :coastline-follow coords next-pos
                                  (:type unit) (:type (:contents next-cell))])
              (wake-coastline-unit coords :blocked)
              nil)
            ;; Normal move
            (let [post-wake (post-move-wake-reason unit next-pos remaining-steps start-pos)
                  moved-unit (if post-wake
                               (make-woken-unit unit post-wake)
                               (make-continuing-unit unit remaining-steps visited next-pos coords))]
              (debug/log-action! [:coastline-move (:type unit) coords next-pos])
              (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
              (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
              (visibility/update-cell-visibility next-pos (:owner unit))
              (when-not post-wake next-pos))))
        (do (wake-coastline-unit coords :blocked) nil)))))
```

### 2. Add Debug Logging

Add require for debug namespace at top of `coastline.cljc`:

```clojure
(ns empire.movement.coastline
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.debug :as debug]  ;; ADD THIS
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.movement.explore :as explore]))
```

Log entries to add:
- `[:coastline-move <unit-type> <from> <to>]` - Normal coastline movement
- `[:collision-avoided :coastline-follow <from> <attempted-to> <moving-type> <blocking-type>]` - When defensive check prevents collision

### 3. Add Coastline Units Section to Debug Dump

In `src/empire/debug.cljc`, add a section to `format-dump` that lists all units in `:coastline-follow` mode:

```clojure
(defn- find-coastline-units
  "Find all units in coastline-follow mode."
  []
  (let [game-map @atoms/game-map]
    (for [row (range (count game-map))
          col (range (count (first game-map)))
          :let [cell (get-in game-map [row col])
                unit (:contents cell)]
          :when (= (:mode unit) :coastline-follow)]
      {:pos [row col]
       :type (:type unit)
       :owner (:owner unit)
       :visited (count (:visited unit))
       :steps-remaining (:coastline-steps unit)})))

(defn- format-coastline-section
  "Format coastline-follow units for debug dump."
  []
  (let [units (find-coastline-units)]
    (str "=== Coastline-Follow Units ===\n"
         (if (empty? units)
           "  (none)\n"
           (str/join "\n"
                     (for [{:keys [pos type owner visited steps-remaining]} units]
                       (str "  " pos " " (name type) " owner:" (name owner)
                            " visited:" visited " steps:" steps-remaining))))
         "\n\n")))
```

Then add call to `format-coastline-section` in `format-dump` after `sea-lane-section`.

### 4. Test

Add test to `spec/empire/movement/coastline_spec.clj`:

```clojure
(describe "collision prevention"
  (before (reset-all-atoms!))

  (it "prevents overwriting another unit when destination becomes occupied"
    ;; Set up: two adjacent ships in coastline-follow mode
    (reset! atoms/game-map (build-test-map ["------"
                                             "--~~--"
                                             "-~~~~-"
                                             "-~PP~-"  ; Two patrol boats adjacent
                                             "-~~~~-"
                                             "--~~--"]))
    (let [p1-coords [3 2]
          p2-coords [3 3]]
      ;; Set both to coastline-follow mode heading toward same area
      (swap! atoms/game-map assoc-in (conj p1-coords :contents)
             {:type :patrol-boat :owner :player :mode :coastline-follow
              :coastline-steps 10 :start-pos p1-coords :visited #{p1-coords} :prev-pos nil})
      (swap! atoms/game-map assoc-in (conj p2-coords :contents)
             {:type :patrol-boat :owner :player :mode :coastline-follow
              :coastline-steps 10 :start-pos p2-coords :visited #{p2-coords} :prev-pos nil})
      (reset! atoms/player-map (make-initial-test-map 6 6 nil))

      ;; Process both units - neither should disappear
      (coastline/move-coastline-unit p1-coords)
      (coastline/move-coastline-unit p2-coords)

      ;; Count patrol boats - should still be 2
      (let [patrol-boats (for [r (range 6) c (range 6)
                               :let [cell (get-in @atoms/game-map [r c])]
                               :when (= :patrol-boat (:type (:contents cell)))]
                           [r c])]
        (should= 2 (count patrol-boats))))))
```

## Files to Modify

1. `src/empire/movement/coastline.cljc` - Add defensive check and logging
2. `src/empire/debug.cljc` - Add coastline units section to dump
3. `spec/empire/movement/coastline_spec.clj` - Add collision prevention test

## Cyclomatic Complexity

The modified `move-coastline-step` adds one branch (the `if (:contents next-cell)` check). The function currently has approximately 4 decision points; adding one more keeps it at CC=5, within limits.
