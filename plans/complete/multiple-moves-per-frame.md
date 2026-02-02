# Plan: Multiple Moves Per Frame

## Problem

`update-state` calls `advance-game` once per frame. `advance-game` processes one batch of items, effectively moving one unit per frame (at 30 FPS). This makes the game feel slow when many units are in motion.

## Approach

Call `advance-game` up to N times per frame, controlled by a config constant `advances-per-frame` (default 10). Terminate the loop early when there are no more items to process, the game is paused, or waiting for player input.

## Changes

### 1. `src/empire/config.cljc` — new constant

```clojure
(def advances-per-frame 10)
```

### 2. `src/empire/game_loop.cljc` — new function `advance-game-batch`

```clojure
(defn advance-game-batch []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (and (> remaining 1)
                 (not @atoms/paused)
                 (not @atoms/waiting-for-input)
                 (or (seq @atoms/player-items) (seq @atoms/computer-items)))
        (recur (dec remaining))))))
```

Always calls `advance-game` at least once (to handle round start when both lists are empty). Stops looping when:
- Reached `advances-per-frame` limit
- Game is paused
- Waiting for player input
- No items left to process

### 3. `src/empire/ui/core.cljc` — `update-state`

Replace `game-loop/advance-game` with `game-loop/advance-game-batch`:

```clojure
(defn update-state [state]
  (game-loop/update-player-map)
  (game-loop/update-computer-map)
  (game-loop/advance-game-batch)
  (rendering/update-hover-status)
  state)
```

## Tests

1. **Processes multiple units in one batch**: Set up 3 sentry units (no attention needed). Call `advance-game-batch`. Verify all 3 removed from `player-items` in a single call.

2. **Stops when items exhausted**: Set up 2 units. Call `advance-game-batch`. Verify only 2 advances occurred (not 10).

3. **Stops when waiting for input**: Set up units where the first needs attention. Call `advance-game-batch`. Verify game blocks on first attention request and remaining items are untouched.

4. **Stops when paused**: Set paused flag. Call `advance-game-batch`. Verify only one (no-op) advance occurred.

## Files Modified

- `src/empire/config.cljc` (add `advances-per-frame`)
- `src/empire/game_loop.cljc` (add `advance-game-batch`)
- `src/empire/ui/core.cljc` (call `advance-game-batch` instead of `advance-game`)
- `spec/empire/game_loop_spec.clj` (new tests)

## Verification

```bash
clj -M:spec
```

## Status: reviewed
