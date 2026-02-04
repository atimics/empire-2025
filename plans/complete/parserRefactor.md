# Plan: Reduce Cyclomatic Complexity in Parser and Generator

## Problem

Three parser functions and one generator function far exceed the CC <= 5 guideline:

| Function | File | CC | Lines |
|---|---|---|---|
| `parse-single-then-clause` | parser.cljc:440-703 | 42 | 264 |
| `parse-when` | parser.cljc:326-436 | 18 | 111 |
| `parse-given-line` | parser.cljc:149-253 | 15 | 105 |
| `determine-needs` | generator.cljc:37-125 | 15 | 89 |

Additionally, `parse-unit-props-line` (parser.cljc:83-125) uses atoms for local accumulation instead of idiomatic reduce.

All four high-CC functions are giant `cond` dispatchers — pattern-matching tables encoded as imperative code. Each cond branch tests a regex, then extracts captures with the same regex again.

## Approach: Data-Driven Pattern Tables

Replace each `cond` dispatcher with a vector of `{:regex :handler}` entries scanned by a generic `first-matching-pattern` function:

```clojure
(defn- first-matching-pattern [patterns text]
  (loop [entries patterns]
    (when-let [{:keys [regex handler]} (first entries)]
      (if-let [match (re-find regex text)]
        (handler match)
        (recur (rest entries))))))
```

CC of dispatcher drops to ~2 regardless of pattern count. Each handler is a named private function with CC 1-3. Regex is matched only once.

## Files to Modify

- `src/empire/acceptance/parser.cljc` — Batches 1-5
- `spec/empire/acceptance/parser_spec.clj` — Batches 1-5 (new helper tests)
- `src/empire/acceptance/generator.cljc` — Batch 6
- `spec/empire/acceptance/generator_spec.clj` — Batch 6 (new tests)

## Verification

After each batch: `clj -M:spec spec/empire/acceptance/` (202 existing tests must pass).
After final batch: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/` (all 102 acceptance tests must pass).

---

## Batch 1: Infrastructure

Add to `parser.cljc`:

1. `first-matching-pattern [patterns text]` — loops over `{:regex :handler}` entries, returns first handler result or nil. CC=3.
2. `first-matching-pattern-with-context [patterns text context]` — same but passes `(match, context)` to handler. For WHEN and GIVEN patterns that need context. CC=3.

Write 3-4 parser spec tests for the helpers (empty patterns, no match, first-match-wins, context passing).

Run specs.

---

## Batch 2: Refactor `parse-single-then-clause` (CC 42 -> 3)

The THEN parser has a cross-cutting concern: timing prefixes ("at the next round/step/move"). Some patterns match before timing is stripped (`no-prefix`), others match after (`no-timing`). Split into two pattern tables.

### Steps

**2a.** Extract `strip-then-preamble [clause]` — returns `{:no-prefix text :no-timing text :timing-key key-or-nil}`. Write test.

**2b.** Extract ~26 handler functions for patterns that match against `no-prefix`. Name convention: `then-handle-*`. Each takes a regex match vector, returns an IR map. Examples:
- `then-handle-after-moves`
- `then-handle-unit-waiting`
- `then-handle-unit-at-coords`
- `then-handle-message-contains-literal`
- `then-handle-unit-has-prop`

Build `then-no-prefix-patterns` vector preserving current ordering.

**2c.** Extract ~15 handler functions for patterns that match against `no-timing`. Examples:
- `then-handle-will-be-at`
- `then-handle-occupies-cell`
- `then-handle-unit-present-coords`
- `then-handle-production-with-rounds`

Build `then-timed-patterns` vector preserving current ordering.

**2d.** Rewrite `parse-single-then-clause`:
```clojure
(defn- parse-single-then-clause [clause]
  (let [{:keys [no-prefix no-timing timing-key]} (strip-then-preamble clause)]
    (or (first-matching-pattern then-no-prefix-patterns no-prefix)
        (tag-timing timing-key
          (or (first-matching-pattern then-timed-patterns no-timing)
              {:type :unrecognized :text clause})))))
```
CC = 3.

Run all specs.

---

## Batch 3: Refactor `parse-when` (CC 18 -> 2)

### Steps

**3a.** Extract ~16 handler functions. Each takes `(match, context)`, returns IR map or vector of IR maps (for compound patterns). Examples:
- `when-handle-backtick`
- `when-handle-key-and-battle`
- `when-handle-key-press`
- `when-handle-new-round`

Build `when-patterns` vector preserving current ordering.

**3b.** Rewrite `parse-when` to use `first-matching-pattern-with-context`. Compound handlers return vectors; caller flattens with `mapcat`. CC = 2.

Run all specs.

---

## Batch 4: Refactor `parse-given-line` (CC 15 -> 4)

GIVEN patterns match against two different text variables: `stripped` for map directives, `no-given` for everything else. Two fallback patterns delegate to `parse-container-state-line` and `parse-unit-props-line`.

### Steps

**4a.** Extract handler functions for 4 map patterns and ~11 directive patterns. Examples:
- `given-handle-map-start`
- `given-handle-production-with-rounds`
- `given-handle-cell-props`
- `given-handle-player-items-multi`

Build `given-map-patterns` and `given-directive-patterns` vectors.

**4b.** Rewrite `parse-given-line`:
```clojure
(defn- parse-given-line [line context]
  (let [{:keys [stripped no-given]} (prepare-given-text line)]
    (or (first-matching-pattern given-map-patterns stripped)
        (first-matching-pattern-with-context given-directive-patterns no-given context)
        (parse-container-state-line line)
        (parse-unit-props-line line)
        {:directive :unrecognized :ir {:type :unrecognized :text (str/trim line)}})))
```
CC = 4.

Run all specs.

---

## Batch 5: Refactor `parse-unit-props-line` (atoms -> reduce)

### Steps

**5a.** Define `unit-prop-extractors` vector — each entry has `:regex` and `:extract-fn` returning a map of props to merge.

**5b.** Rewrite `parse-unit-props-line` to reduce over extractors instead of using atoms:
```clojure
(let [result (reduce (fn [acc {:keys [regex extract-fn]}]
                       (if-let [match (re-find regex rest-str)]
                         (merge-with merge acc (extract-fn match))
                         acc))
                     {:props {} :container-props {}}
                     unit-prop-extractors)]
  ...)
```
CC = 3.

Run all specs.

---

## Batch 6: Refactor generator `determine-needs` (CC 15 -> 2)

### Steps

**6a.** Extract `build-need-context [test]` — pre-computes aggregated data (all types, targets, map-rows, wfi-units) from a single test's IR. CC = 1.

**6b.** Define `need-rules` vector — each entry has `:need` keyword and `:pred` function taking a need-context. Each predicate has CC <= 4.

**6c.** Rewrite `determine-needs`:
```clojure
(defn determine-needs [tests]
  (let [all-contexts (map build-need-context tests)]
    (set (for [{:keys [need pred]} need-rules
               :when (some pred all-contexts)]
           need))))
```
CC = 2.

Write 2-3 new generator spec tests for the refactored structure.

Run all specs.

---

## Batch 7: Variable Renaming and Final Verification

**7a.** Rename confusing stripped-prefix variables:
- `no-when` -> `when-text`
- `no-prefix` -> `bare-text`
- `no-given` -> `given-text`
- `no-timing` -> `timed-text`

**7b.** Run full test suite: `clj -M:spec spec/empire/acceptance/`

**7c.** Run full acceptance pipeline: `clj -M:parse-tests && clj -M:generate-specs && clj -M:spec generated-acceptance-specs/`

---

## CC Summary

| Function | Before | After |
|---|---|---|
| `parse-single-then-clause` | 42 | 3 |
| `parse-when` | 18 | 2 |
| `parse-given-line` | 15 | 4 |
| `parse-unit-props-line` | ~8 | 3 |
| `determine-needs` | 15 | 2 |
| `first-matching-pattern` | n/a | 3 |
| Each handler function | n/a | 1-3 |

All functions CC <= 5.
