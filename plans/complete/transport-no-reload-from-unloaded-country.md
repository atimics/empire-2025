# Plan: Transport Avoids Reloading From Recently Unloaded Country

## Summary

Transports should not pick up armies from a country where they unloaded in the last 10 rounds. This prevents transports from shuttling armies in a pointless loop — unloading at a continent, sailing back, and immediately reloading armies from the country they just reinforced.

## New Field on Transport

- `:unloaded-countries` — A map of `{country-id round-number}` recording which countries the transport has unloaded into and when. Updated at unload time, entries expire after 10 rounds.

## Recording Unloads

In `unload-armies` (transport.cljc:182), after unloading armies, record the country-id of the land where armies were placed. The unloaded armies may not have a country-id yet (they get one on conquest), but the land cells they're placed on may have a `:country-id` stamp, and the adjacent continent may belong to a country. Use the country-id of the first unloaded land cell that has one:

```clojure
;; After unloading armies in unload-armies:
(let [unloaded-country-id (->> (take to-unload land-neighbors)
                                (keep #(:country-id (get-in @atoms/game-map %)))
                                first)]
  (when unloaded-country-id
    (swap! atoms/game-map update-in (conj pos :contents :unloaded-countries)
           assoc unloaded-country-id @atoms/round-number)))
```

Also record the country-id of the transport's own country at unload time — since the transport may be from the same country it's reinforcing:

```clojure
(when-let [transport-country (:country-id transport)]
  (swap! atoms/game-map update-in (conj pos :contents :unloaded-countries)
         assoc transport-country @atoms/round-number))
```

## Filtering Armies During Loading

### Modified: `find-nearest-army`

Add a filter to exclude armies whose `:country-id` appears in the transport's `:unloaded-countries` map and the entry is less than 10 rounds old:

```clojure
(defn- recently-unloaded-country?
  "Returns true if the country-id was unloaded to within the last 10 rounds."
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- @atoms/round-number unload-round) 10)))

(defn- find-nearest-army
  "Find the nearest army to the transport. Filters by pickup-continent if provided.
   Excludes armies from countries the transport recently unloaded into."
  [transport-pos pickup-continent unloaded-countries]
  (let [armies (find-armies-to-load)
        candidates (cond->> armies
                     pickup-continent
                     (filter #(contains? pickup-continent %))

                     (seq unloaded-countries)
                     (remove (fn [army-pos]
                               (let [unit (get-in @atoms/game-map (conj army-pos :contents))]
                                 (and (:country-id unit)
                                      (recently-unloaded-country?
                                        unloaded-countries (:country-id unit)))))))]
    (when (seq candidates)
      (apply min-key #(core/distance transport-pos %) candidates))))
```

### Modified: `process-transport`

Thread the transport's `:unloaded-countries` through to `find-nearest-army` in the loading mission branch:

```clojure
;; Loading transport - go get armies
(= current-mission :loading)
(let [pickup-continent (when-let [ocp (:pickup-continent-pos transport)]
                         (continent/flood-fill-continent ocp))
      unloaded-countries (:unloaded-countries transport)]
  (if-let [army-pos (find-nearest-army pos pickup-continent unloaded-countries)]
    (move-toward-position pos army-pos)
    (explore-sea pos)))
```

## Expiry

Entries in `:unloaded-countries` older than 10 rounds are effectively ignored by the `recently-unloaded-country?` check. No explicit cleanup is needed — the map stays small since transports typically interact with only a few countries.

## Edge Cases

### Army Has No Country-ID

Armies without a `:country-id` (e.g., unloaded invasion forces that haven't conquered a city yet) are never filtered out. The country-exclusion only applies to armies with a known country affiliation.

### All Armies Filtered Out

If every army on the pickup continent belongs to a recently-unloaded country, `find-nearest-army` returns nil and the transport falls through to `explore-sea`. After 10 rounds the exclusion expires and the transport can pick up those armies again.

### Transport Unloads at Multiple Countries

Each unload records its own country-id with its own timestamp. A transport that unloads at country 3 on round 20 and country 5 on round 25 will avoid both until rounds 30 and 35 respectively.

### New Country Created After Unload

If unloaded armies conquer a city and mint a new country-id, that new id won't be in `:unloaded-countries` (only the land's country-id at unload time is recorded). This is fine — the new country is a fresh entity that the transport hasn't directly reinforced.

### Unloaded Land Has No Country-ID

If the unload destination is unclaimed land (no `:country-id`), nothing is recorded. This typically happens during first invasions of uncontested continents, where the reload-loop problem doesn't occur.

## Files Modified

| File | Change |
|------|--------|
| `src/empire/computer/transport.cljc` | Add `recently-unloaded-country?`. Modify `find-nearest-army` to accept and apply unloaded-countries filter. Modify `unload-armies` to record country-id in `:unloaded-countries`. Modify `process-transport` to thread `:unloaded-countries` to `find-nearest-army`. |
| `spec/empire/computer/transport_spec.clj` | Tests: transport avoids armies from recently unloaded country, exclusion expires after 10 rounds, armies with no country-id not filtered, multiple countries tracked independently, transport falls back to explore when all armies filtered. |

## Key Design Decisions

### Map on the transport, not a global atom

Storing `:unloaded-countries` on the transport unit itself (rather than a global atom keyed by transport-id) keeps the data local and eliminates cleanup concerns when transports are destroyed.

### 10-round window matches the issue description

The 10-round cooldown prevents the immediate reload loop while allowing the transport to return to that country's armies once sufficient time has passed and the tactical situation may have changed.

### No explicit map cleanup

The `:unloaded-countries` map grows by at most one entry per unload event. Transports have short operational cycles and interact with few countries, so the map stays small without needing periodic pruning.
