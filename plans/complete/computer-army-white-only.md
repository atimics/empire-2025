# Plan: Computer Armies White Only

## Summary

Computer armies are currently drawn in different colors based on their mode: white for awake, pink for sentry, green for explore/coast-walk. They should all be drawn in white regardless of mode. Player armies retain their mode-based colors.

## Current Behavior

`unit->color` in `config.cljc:153` returns a color based on the unit's mode via `mode->color`. This applies uniformly to all units (player and computer, all types). Computer armies in `:sentry` mode show as pink `[255 128 128]` and in `:explore` or `:coast-walk` mode show as green `[144 238 144]`.

## Fix

### Modified: `unit->color` in `config.cljc`

Add a check: if the unit is a computer army, return white regardless of mode.

```clojure
(defn unit->color
  "Returns the RGB color for a unit based on owner, type, mission, and mode.
   Computer armies are always white."
  [unit]
  (cond
    ;; Computer armies: always white
    (and (= :computer (:owner unit))
         (= :army (:type unit)))
    awake-unit-color

    ;; Loading mission units: black
    (= :loading (:mission unit))
    sleeping-unit-color

    ;; All others: mode-based color
    :else
    (mode->color (:mode unit))))
```

## What This Affects

| Unit | Before | After |
|------|--------|-------|
| Computer army (awake) | White | White (unchanged) |
| Computer army (sentry) | Pink | White |
| Computer army (explore) | Green | White |
| Computer army (coast-walk) | Green | White |
| Player army (any mode) | Mode color | Mode color (unchanged) |
| Computer ships (any mode) | Mode color | Mode color (unchanged) |
| Computer fighters | Mode color | Mode color (unchanged) |

Only computer armies change. All other units (player units, computer ships, computer fighters) keep their existing mode-based coloring.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/config.cljc` | Modify `unit->color` to return white for computer armies. |
| `spec/empire/config_spec.clj` | Tests: computer army returns white regardless of mode, player army still returns mode-based color, computer ships still return mode-based color. |
