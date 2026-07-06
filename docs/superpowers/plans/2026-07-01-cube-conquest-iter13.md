# Iter13 Implementation Plan — 2026-07-01

Base commit: 6bf89f5

## Findings approved (1)

| # | Sev | Title |
|---|-----|-------|
| 1 | MEDIUM | Creative-mode porteur timeout has deterministic RED/BLUE asymmetry |

## Tasks

### Task 1 — MEDIUM: Symmetric draw when both creative porteurs time out
**File**: `CubeConquestGameManagerEvents.java`

In `handlePlacementTick`, the per-team timeout loop processes `Team.values()` (RED first).
When both porteurs are creative and both miss the placement deadline, RED is penalized first
→ `triggerVictory(BLUE)` resets phase → BLUE's penalty never fires → BLUE always wins.

Fix: before the per-team loop body that fires the creative timeout, add a pre-check:
if `tickCount >= PLACEMENT_TIMEOUT_TICKS` AND both teams have an unplaced cube AND both
porteurs are online creative-mode players → call `triggerDraw(server)` and return early.

```java
// ponytail: symmetric draw when both creative porteurs time out — prevents RED-first bias
if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
    boolean redFailed = saved.getCubePos(Team.RED) == null
        && state.getPorteur(Team.RED) != null
        && Optional.ofNullable(server.getPlayerList().getPlayer(state.getPorteur(Team.RED)))
               .map(LivingEntity::isCreative).orElse(false);
    boolean blueFailed = saved.getCubePos(Team.BLUE) == null
        && state.getPorteur(Team.BLUE) != null
        && Optional.ofNullable(server.getPlayerList().getPlayer(state.getPorteur(Team.BLUE)))
               .map(LivingEntity::isCreative).orElse(false);
    if (redFailed && blueFailed) {
        triggerDraw(server);
        return;
    }
}
```

Place this block at the top of `handlePlacementTick`, before (or at the start of) the
per-team loop, so it fires before any individual team is processed.

## Order of execution

1 task only.

Task: implementer subagent → reviewer subagent → commit on NICE.
