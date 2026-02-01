# Plan: Army Overproduction Fix

## Summary

Countries should maintain no more than 10 armies at a time. The existing 10-army cap in `decide-country-production` is correct but undercounts because `count-country-armies` misses armies aboard transports. Transported armies are stored as an integer `:army-count` on the transport cell, not as individual units with `:country-id` in `:contents`. The fix: include transported armies in the count by scanning transports belonging to the same country and adding their `:army-count`.

## Root Causes

### 1. Transported Armies Not Counted

`count-country-armies` (production.cljc:59) scans `game-map` for cells whose `:contents` is an army with a matching `:country-id`. When armies board a transport, they are removed from the cell's `:contents` and added to the transport's `:army-count` — a plain integer with no country tracking. A country with 5 armies on the map and 4 aboard a transport appears to have only 5 armies. The city keeps producing.

### 2. Non-Country Cities Have No Cap

`decide-production` (production.cljc:208) falls through to produce armies at non-country cities whenever the continent has land objectives. There is no army count check on this path. A newly conquered city without a country-id can produce armies indefinitely.

## Fix

### Modify `count-country-armies`

Add transported armies to the count. A transport's `:country-id` determines which country its cargo belongs to.

```clojure
(defn count-country-armies
  "Counts live armies belonging to the given country-id,
   including armies aboard transports of the same country."
  [country-id]
  (reduce
    (fn [total [i row]]
      (reduce
        (fn [total [j cell]]
          (let [unit (:contents cell)]
            (cond
              ;; Army on the ground with matching country-id
              (and unit
                   (= :computer (:owner unit))
                   (= :army (:type unit))
                   (= country-id (:country-id unit)))
              (inc total)

              ;; Transport with matching country-id — count its cargo
              (and unit
                   (= :computer (:owner unit))
                   (= :transport (:type unit))
                   (= country-id (:country-id unit)))
              (+ total (get unit :army-count 0))

              :else total)))
        total
        (map-indexed vector row)))
    0
    (map-indexed vector @atoms/game-map)))
```

### Cap Non-Country Army Production

Add a simple global cap for non-country armies. Count all computer armies with no `:country-id` and cap at 10:

```clojure
;; In decide-production, replace the non-country fallback:
(when (and (not country-id)
           (< (count-non-country-armies) 10)
           (continent/has-land-objective?
             (continent/scan-continent
               (continent/flood-fill-continent city-pos))))
  :army)
```

New helper:

```clojure
(defn- count-non-country-armies
  "Counts computer armies with no country-id."
  []
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :army (:type unit))
                          (nil? (:country-id unit)))]
           true)))
```

## Edge Cases

### Transport Carrying Armies From Multiple Countries

The current design doesn't track per-army country-id aboard transports. However, transports themselves have a `:country-id`, and armies board transports from the same country (transports pick up armies near their country's cities). Counting all cargo toward the transport's country-id is accurate in practice.

### Transport With No Country-ID

If a transport somehow has no `:country-id`, its cargo is not counted toward any country. This is consistent — those armies are effectively non-country armies.

### Armies In Transit (Unloaded, Pre-Conquest)

Unloaded armies have `:unload-event-id` but no `:country-id` until they conquer a city. These are counted by `count-non-country-armies` and capped at 10.

### Country With Many Transports

A country with 2 full transports (6 armies each) has 12 transported armies. The count correctly shows >= 10, so the city stops producing armies. The cap applies to the total across map and transports.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/production.cljc` | Modify `count-country-armies` to include transport cargo. Add `count-non-country-armies`. Modify `decide-production` to cap non-country army production at 10. |
| `spec/empire/computer/production_spec.clj` | Tests: country army count includes transported armies, non-country army cap, transport with no country-id excluded, cap stops production when transports are full. |

## Key Design Decisions

### Count cargo by transport's country-id

Transports inherit their `:country-id` from the producing city. Armies board nearby transports of the same owner. Attributing all cargo to the transport's country is simpler than tracking per-army country-id inside the container and matches actual game behavior.

### Same cap (10) for non-country armies

Non-country armies are temporary — they exist between unloading and conquering their first city. Capping them at 10 prevents unbounded production from non-country cities while leaving enough for a functional invasion force.
