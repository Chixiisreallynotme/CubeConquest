# Iter15 Implementation Plan — 2026-07-01

Base commit: 10946d9

## Findings approved (6)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | onUseBlock sets cubePos before vanilla confirms placement — phantom race into COMBAT |
| 2 | MEDIUM | startGame doesn't removeSlownessFromAll — crash-recovery can start with slowed players |
| 3 | MEDIUM | Compass issued during PREPARATION when no cube positions exist yet |
| 4 | MEDIUM | Reconnecting porteur not re-slowed until next server tick |
| 5 | LOW | Phantom-check restores cube item but Slowness may have expired (per-tick re-applies next tick) |
| 6 | LOW | No test for isForfeitPassing/isDrawThresholdMet with asymmetric team sizes |

## Tasks

### Task 1 — HIGH: Defer transitionToCombat by one tick after cubePos registration
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

`onUseBlock` calls `saved.setCubePos(team, pos)` and immediately checks if both positions are
non-null to call `transitionToCombat`. The problem: `UseBlockCallback` fires BEFORE vanilla
finalises the block placement. If vanilla rejects the placement (spawn protection, another mod),
`cubePos` is set but the block never lands — COMBAT starts with a phantom position.

Fix: remove the `transitionToCombat` call from `onUseBlock`. Let `handlePlacementTick` drive
the transition instead — it already runs every tick and checks both positions:

In `handlePlacementTick`, after the per-team loop, add:
```java
// ponytail: both positions registered — transition on the tick AFTER onUseBlock fires,
// giving vanilla time to finalise block placement
if (saved.getCubePos(Team.RED) != null && saved.getCubePos(Team.BLUE) != null) {
    transitionToCombat(server);
    return;
}
```

Remove the existing `if both non-null → transitionToCombat` block from `onUseBlock`.

The phantom-check already runs earlier in `handlePlacementTick` and will catch any position
that has no corresponding block on the next tick.

### Task 2 — MEDIUM: removeSlownessFromAll in startGame
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Add `removeSlownessFromAll(server)` near the top of `startGame`, after clearing inventories
but before distributing cubes and compasses. Guards against crash-recovery or edge-path
leakage where Slowness wasn't cleaned up.

```java
// ponytail: defensive sweep — prior game may have left Slowness if server crashed mid-PLACEMENT
removeSlownessFromAll(server);
```

### Task 3 — MEDIUM: Issue compasses at transitionToCombat, not startGame
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Compasses given during PREPARATION have no valid target (no cube positions yet), showing 0°
for the entire PREPARATION + PLACEMENT phases. Move compass distribution to
`transitionToCombat`, where cube positions are guaranteed to exist.

In `startGame`: remove the `distributeCompassToAll(server)` call.
In `transitionToCombat`: add `distributeCompassToAll(server)` after broadcasting cube positions.

Verify no other code path assumes compasses exist during PREPARATION.

### Task 4 — MEDIUM: Re-apply Slowness to porteur immediately on reconnect
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

In the JOIN handler, the reconnect block at lines ~112-118 removes Slowness from non-porteurs.
Extend it to re-apply Slowness to the reconnecting porteur immediately (don't wait for the
next `handlePlacementTick` iteration):

```java
// ponytail: re-immobilize porteur immediately — don't wait for next tick
if (phase == GamePhase.PLACEMENT) {
    state.getTeamOf(player.getUUID()).ifPresent(t -> {
        if (player.getUUID().equals(state.getPorteur(t))
                && saved.getCubePos(t) == null
                && !player.isCreative()) {
            player.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SLOWNESS, PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
        }
    });
}
```

### Task 5 — LOW: Re-apply Slowness in phantom-check after restoring cube
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

After the phantom-check clears `cubePos` and re-gives the cube item to the porteur, also
re-apply Slowness if the effect has expired. The per-tick loop re-applies on the next tick
anyway, but making it explicit here is cleaner and avoids the 1-tick gap.

After the `porteur.getInventory().add(...)` and `porteur.sendSystemMessage(...)` lines, add:
```java
// ponytail: re-immobilize — Slowness may have expired if phantom-check fires late
if (!porteur.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && !porteur.isCreative()) {
    porteur.addEffect(new MobEffectInstance(
        net.minecraft.world.effect.MobEffects.SLOWNESS, PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
}
```

### Task 6 — LOW: Add asymmetric team size test for forfeit/draw threshold
**File**: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

Add two tests covering asymmetric team compositions:

1. `forfeit_passes_with_majority_of_larger_team` — e.g. RED has 4 players, BLUE has 1.
   3 RED votes → passes (ceil(4/2)+1 = 3). 2 RED votes → does not pass.
2. `draw_threshold_asymmetric_teams` — same 4v1 setup, verify draw quorum is computed
   against total online players correctly.

Use the existing `setUp` pattern with `state.addPlayerToTeam(...)` calls.

## Order of execution

Tasks 1–5: same file (CubeConquestGameManagerEvents.java) — sequential.
Task 6: different file (test only) — can run after or in parallel with 1–5.

Each task: implementer subagent → reviewer subagent → commit on NICE.
