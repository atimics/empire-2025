# Stuck Transport Bug

## Symptom

A computer transport with 6 armies and `:unloading` mission sits motionless at sea, never delivering its troops.

## Observed State (Round 147, debug log 132117)

- **Transport at [163,0]**: sea, mission `:unloading`, army-count 6, mode `:awake`
- **Only visible city**: [161,3], computer-owned
- **No player or free cities** visible on the computer-map

## Root Cause

The transport has two fallback options, and both fail:

### 1. No unload target

`find-unload-target` (transport.cljc:77) searches the computer-map for player or free cities off the pickup continent. The computer has not discovered any enemy cities yet — the only known city is its own at [161,3]. So `resolve-unload-target` returns nil.

### 2. No unexplored sea reachable

The fallback `explore-sea` runs a BFS from the transport's position looking for unexplored sea cells. The transport is in a semi-enclosed bay:

```
[161,0]  sea (dead end — land on all north/east sides)
[162,0] [162,1] [162,2]  sea (land blocks further north)
[163,0] [163,1] [163,2] [163,3]  sea (land at col 4+)
[164,0] [164,1] [164,2] [164,3]  sea (land at col 4+)
```

All of these cells are already explored on the computer-map. The unexplored sea at rows 149–151 is on the other side of the landmass, unreachable by sea from this bay. The BFS exhausts all connected sea cells without finding any adjacent-to-unexplored cell, and returns nil.

### Result

No target city + no unexplored sea = the transport does nothing every round. It is trapped in a fully-explored bay with no known enemy to attack.

## Possible Fixes

- When an unloading transport has no target and no unexplored sea, have it navigate toward the nearest unexplored *land* boundary (the edge of explored territory as seen from sea) rather than giving up entirely.
- Allow the transport to pick a distant explored sea cell as a waypoint toward the far side of the map, so it can circumnavigate the landmass.
- If truly landlocked (BFS exhausts all connected sea), convert the transport's mission back to `:loading` and drop its armies at the nearest beach so they can explore on foot.
