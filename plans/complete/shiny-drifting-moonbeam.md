# Plan: Limit Fighter Production to 2 Per Country

Fighters currently have no per-country cap. They are the global fallback (priority 9) in `decide-global-production`. This change adds a per-country fighter limit of 2, following the same pattern as transports (2), armies (10), and patrol boats (1).

---

## 1. Assign country-id to fighters at spawn

**File**: `src/empire/player/production.cljc`

`apply-country-id` (line 51-56) currently only assigns country-id to `:army` and `:transport`. Change the set to `#{:army :transport :fighter}` so spawned fighters inherit the city's country-id.

---

## 2. Add count-country-fighters

**File**: `src/empire/computer/production.cljc`

Add `count-country-fighters` following the exact pattern of `count-country-armies` (lines 59-70):

```clojure
(defn- count-country-fighters
  "Counts live fighters belonging to the given country-id."
  [country-id]
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])
                     unit (:contents cell)]
               :when (and unit
                          (= :computer (:owner unit))
                          (= :fighter (:type unit))
                          (= country-id (:country-id unit)))]
           true)))
```

---

## 3. Add fighter limit in decide-country-production

**File**: `src/empire/computer/production.cljc`

Add a 5th priority at the end of `decide-country-production` (after the destroyer check, before returning nil):

```clojure
;; 5. Fighter: < 2 per country
(< (count-country-fighters country-id) 2)
:fighter
```

This means country cities produce up to 2 fighters per country via per-country logic. Cities without a country-id (or countries at the fighter cap) fall through to global production, which still has `:fighter` as the ultimate fallback.

CC of `decide-country-production` goes from 4 to 5 (still within threshold).

---

## Tests (written before implementation)

**File**: `spec/empire/computer/production_spec.clj`

- Country city produces fighter when country has 0 fighters (armies at cap, transports at cap, patrol boat present, destroyers at cap)
- Country city produces fighter when country has 1 fighter (same conditions)
- Country city does NOT produce fighter per-country when country already has 2 fighters (falls through to global)
- Fighter spawned from country city inherits country-id

**File**: `spec/empire/player/production_spec.clj` (if exists, otherwise production_spec)

- `apply-country-id` assigns country-id to fighter when cell has country-id
- `apply-country-id` does not assign country-id to fighter when cell lacks country-id

---

## Verification

1. `clj -M:spec` - all tests pass
2. Visual: run game, observe that computer countries produce at most 2 fighters each rather than unlimited fighters
