# Plan: Consolidate Computer Production Constants

## Overview

Extract all computer production magic numbers from `src/empire/computer/production.cljc` into `src/empire/config.cljc` as named constants with explanatory comments. Replace magic numbers in `computer/production.cljc` with references to those constants. Player production (`empire.player.production`) is not changed.

## Constants to Extract

All magic numbers currently in `src/empire/computer/production.cljc` become named constants in `src/empire/config.cljc`:

| Constant | Value | Source Location | Purpose |
|----------|-------|-----------------|---------|
| `max-transports-per-country` | 2 | line 160 | Cap on transports one country will build |
| `armies-before-transport` | 6 | line 161 | Army count a country needs before building transports |
| `max-armies-per-country` | 10 | line 165 | Cap on armies per country (includes armies aboard transports) |
| `max-non-country-armies` | 10 | line 238 | Cap on armies from cities not assigned to any country |
| `max-patrol-boats-per-country` | 1 | line 171 (zero? check) | Patrol boats per country |
| `max-fighters-per-country` | 2 | line 181 | Fighters per country |
| `carrier-city-threshold` | 10 | line 198 | Computer needs >N cities before building carriers |
| `max-live-carriers` | 8 | line 199 | Global fleet cap on live carriers |
| `max-carrier-producers` | 2 | line 200 | Max cities simultaneously producing carriers |
| `satellite-city-threshold` | 15 | line 217 | Computer needs >N cities before building satellites |
| `max-satellites` | 1 | line 218 (zero? check) | Global cap on live satellites |

Also: the literal `10` in `computer/core.cljc` line 136 (`attempt-conquest-computer`) should become `config/max-armies-per-country`.

## Files Modified

### `src/empire/config.cljc`

Add after the existing production-related constants (after line 98):

```clojure
;; --- Computer production thresholds ---

;; Per-country unit caps
(def max-transports-per-country 2)     ;; transports one country will build
(def armies-before-transport 6)        ;; army count before country starts building transports
(def max-armies-per-country 10)        ;; cap on armies per country (includes armies aboard transports)
(def max-non-country-armies 10)        ;; cap on armies from cities not assigned to any country
(def max-patrol-boats-per-country 1)   ;; patrol boats per country
(def max-fighters-per-country 2)       ;; fighters per country

;; Global production gates for expensive units
(def carrier-city-threshold 10)        ;; computer needs >N cities before building carriers
(def max-live-carriers 8)             ;; global fleet cap on live carriers
(def max-carrier-producers 2)          ;; max cities simultaneously producing carriers
(def satellite-city-threshold 15)      ;; computer needs >N cities before building satellites
(def max-satellites 1)                 ;; global cap on live satellites
```

### `src/empire/computer/production.cljc`

Replace all magic numbers with `config/` constants. Add `[empire.config :as config]` to requires. Specific replacements:

| Line | Before | After |
|------|--------|-------|
| 160 | `(< (count-country-transports country-id) 2)` | `(< (count-country-transports country-id) config/max-transports-per-country)` |
| 161 | `(>= (count-country-armies country-id) 6)` | `(>= (count-country-armies country-id) config/armies-before-transport)` |
| 165 | `(< (count-country-armies country-id) 10)` | `(< (count-country-armies country-id) config/max-armies-per-country)` |
| 171 | `(zero? (count-country-patrol-boats country-id))` | `(< (count-country-patrol-boats country-id) config/max-patrol-boats-per-country)` |
| 181 | `(< (count-country-fighters country-id) 2)` | `(< (count-country-fighters country-id) config/max-fighters-per-country)` |
| 198 | `(> (count-computer-cities) 10)` | `(> (count-computer-cities) config/carrier-city-threshold)` |
| 199 | `(< (get unit-counts :carrier 0) 8)` | `(< (get unit-counts :carrier 0) config/max-live-carriers)` |
| 200 | `(< (count-carrier-producers) 2)` | `(< (count-carrier-producers) config/max-carrier-producers)` |
| 217 | `(> (count-computer-cities) 15)` | `(> (count-computer-cities) config/satellite-city-threshold)` |
| 218 | `(zero? (get unit-counts :satellite 0))` | `(< (get unit-counts :satellite 0) config/max-satellites)` |
| 238 | `(< (count-non-country-armies) 10)` | `(< (count-non-country-armies) config/max-non-country-armies)` |

### `src/empire/computer/core.cljc`

Replace literal `10` on line 136 with `config/max-armies-per-country`. Add `[empire.config :as config]` to requires if not already present.

## Execution Order

1. Add named constants to `config.cljc`
2. Add `config` require to `computer/production.cljc`
3. Replace all magic numbers in `computer/production.cljc` with config constants
4. Replace literal `10` in `computer/core.cljc` with config constant
5. Run `clj -M:spec` -- all tests pass

## Verification

The existing 55+ computer production tests in `spec/empire/computer/production_spec.clj` cover every threshold and decision path. Run `clj -M:spec` after each step.

## Status: reviewed
