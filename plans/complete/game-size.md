# Plan: Configurable Game Size from Command Line

## Overview

Reverse the current size derivation flow. Currently: hardcoded window-size → font metrics → map-size. New flow: map-size `[cols rows]` from command line (default `[60 100]`) → calculate window size and all dependent constants → store in `atoms/map-size-constants`.

## Current Flow

```
config/window-size [1400 900]  (hardcoded)
    ↓
Quil defsketch :size
    ↓
setup → create-fonts → measure char-width, char-height
    ↓
cols = screen-w / char-width   (~106)
rows = (screen-h - text-h) / char-height  (~27)
    ↓
atoms/map-size [cols rows]
    ↓
init/make-initial-map
```

## New Flow

```
Command line args: [cols rows]  (default [60 100])
    ↓
config/cell-size [13 29]  (fixed constant, current measured values)
    ↓
window-width  = cols * cell-width
window-height = rows * cell-height + text-area-height
    ↓
Check against monitor bounds (exit with message if too large)
    ↓
Quil defsketch :size [window-width window-height]
    ↓
Store all derived constants in atoms/map-size-constants
    ↓
init/make-initial-map
```

## Changes

### 1. `src/empire/atoms.cljc`

Add new atom:

```clojure
(def map-size-constants
  "Map of all constants derived from the [cols rows] map size."
  (atom {}))
```

Structure when populated:

```clojure
{:cols 60                  ;; map columns (from command line)
 :rows 100                 ;; map rows (from command line)
 :number-of-cities 70      ;; scaled with map area (70 at reference 6000 cells)
 :window-w 780             ;; cols * cell-width
 :window-h 2994            ;; rows * cell-height + text-area-height + text-area-gap
 :map-display-w 780        ;; cols * cell-width (pixel width of map area)
 :map-display-h 2900       ;; rows * cell-height (pixel height of map area)
 :text-area-x 0            ;; text area left edge
 :text-area-y 2907         ;; map-display-h + text-area-gap
 :text-area-w 780          ;; same as window width
 :text-area-h 87}          ;; text-area-rows (3) * cell-height
```

This consolidates the current `atoms/map-size`, `atoms/map-screen-dimensions`, and `atoms/text-area-dimensions` into a single map.

### 2. `src/empire/config.cljc`

Replace `window-size` with a fixed cell size constant. Change `text-area-rows` from 4 to 3 (only 3 message lines are used):

```clojure
;; Cell dimensions (pixels) — fixed, derived from current Courier New 22pt font metrics
(def cell-size [13 29])   ;; [width height] in pixels per map cell
```

Add a function that computes the constants derived from `[cols rows]`. Only `number-of-cities` scales with map area. All step counts, move limits, distances, and radii are fixed gameplay constants that remain in `config.cljc`:

```clojure
(defn compute-size-constants
  "Computes constants derived from the map size [cols rows].
   Returns a map to be stored in atoms/map-size-constants."
  [cols rows]
  (let [area (* cols rows)
        ref-area 6000]         ;; reference area for [60 100] default
    {:cols cols
     :rows rows
     :number-of-cities (max 10 (int (* 70 (/ area ref-area))))}))
```

All other constants stay as fixed `def` values in `config.cljc` — they are gameplay constants, not size-derived:
- `smooth-count`, `land-fraction`, `min-city-distance`, `min-surrounding-land`
- `explore-steps`, `coastline-steps`, `max-sidesteps`
- `sea-lane-local-radius`, `sea-lane-extended-radius`, `max-sea-lane-nodes`, `max-sea-lane-segments`, `sea-lane-min-segment-length`, `sea-lane-min-network-nodes`
- `carrier-spacing`, `max-placement-attempts`

### 3. `src/empire/ui/core.cljc`

#### Parse command line args and calculate window size in `-main`

Cell size is now a fixed config constant, so window size is a straightforward multiplication — no font measurement needed before Quil starts:

```clojure
(defn -main [& args]
  (let [[cols rows] (if (>= (count args) 2)
                      [(Integer/parseInt (first args))
                       (Integer/parseInt (second args))]
                      [60 100])
        [cell-w cell-h] config/cell-size
        text-area-h (* config/text-area-rows cell-h)
        window-w (* cols cell-w)
        window-h (+ (* rows cell-h) text-area-h config/text-area-gap)]
    ;; Check monitor bounds (see Monitor Bounds Check section)
    (reset! atoms/map-size [cols rows])
    (reset! atoms/map-size-constants (config/compute-size-constants cols rows))
    ;; Launch Quil sketch with [window-w window-h]
    ...))
```

#### Refactor `compute-screen-dimensions`

Currently computes map-size FROM window size. Simplify: map-size is already known. It only needs to compute pixel rendering dimensions from the fixed cell size:

```clojure
map-display-w = cols * cell-w
map-display-h = rows * cell-h
text-area at y = map-display-h + text-area-gap
```

### 4. Callers of `number-of-cities`

Only `number-of-cities` moves from a fixed `config/` constant to `@atoms/map-size-constants`. The single call site:

| Constant | Current Source | Call Site |
|----------|---------------|-----------|
| `number-of-cities` | `config.cljc:34` | `init.cljc` (make-initial-map) |

All other config constants (`smooth-count`, `explore-steps`, `coastline-steps`, sea-lane constants, etc.) remain as fixed `def` values in `config.cljc` — no callers need updating.

### 5. `deps.edn`

Update `:run` alias to pass args through:

```clojure
:run {:main-opts ["-m" "empire.ui.core"]}
```

Usage: `clj -M:run 60 100` or `clj -M:run 80 120`

## Monitor Bounds Check

Before launching Quil, query the monitor size using Java AWT:

```clojure
(import '[java.awt Toolkit])

(defn screen-dimensions []
  (let [screen (.getScreenSize (Toolkit/getDefaultToolkit))]
    [(.width screen) (.height screen)]))
```

After calculating the required window size from `[cols rows]` and the fixed cell size, compare against the monitor dimensions. If the window would exceed the monitor bounds, print an error message showing the maximum map size and exit:

```clojure
(let [[screen-w screen-h] (screen-dimensions)
      [cell-w cell-h] config/cell-size
      text-area-h (* config/text-area-rows cell-h)
      max-cols (quot screen-w cell-w)
      max-rows (quot (- screen-h text-area-h config/text-area-gap) cell-h)]
  (when (or (> window-w screen-w) (> window-h screen-h))
    (println (format "Map size [%d %d] exceeds monitor bounds (%dx%d pixels)."
                     cols rows screen-w screen-h))
    (println (format "Maximum map size for this monitor: [%d %d]"
                     max-cols max-rows))
    (System/exit 1)))
```

This check happens in `-main` after computing window dimensions but before launching the Quil sketch.

## Execution Order

1. Add `map-size-constants` atom to `atoms.cljc`
2. Add `cell-size` constant and `compute-size-constants` function to `config.cljc`; remove `window-size`
3. Refactor `-main` to parse `[cols rows]` args, calculate window size, check monitor bounds
4. Refactor `setup` / `compute-screen-dimensions` to use known map-size and fixed cell-size
5. Update `init.cljc` to read `number-of-cities` from `@atoms/map-size-constants`
6. Run `clj -M:spec` after each step

## Verification

Run `clj -M:spec` for test suite. Run `clj -M:run` with no args (default 60x100) and with explicit args (e.g., `clj -M:run 40 60`) to verify window sizing and map generation adapt correctly.

## Status: reviewed
