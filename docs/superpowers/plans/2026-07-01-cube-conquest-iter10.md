# Iter10 Implementation Plan — 2026-07-01

Base commit: fcd0099

## Findings approved (3)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Porteur death after cube placed wrongly triggers loss |
| 2 | HIGH | Slowness 127 persists on porteur after cube placement |
| 3 | MEDIUM | Static transient state not cleared on server lifecycle |

## Tasks

### Task 1 — HIGH: Guard porteur death/disconnect after cube already placed
**File**: `CubeConquestGameManagerEvents.java`

In `onAllowDeath`, the PLACEMENT branch unconditionally calls `triggerVictory(opponent)` when a porteur dies. This is wrong if the porteur already placed their cube.

Add a guard at the top of the PLACEMENT death path, before `removeCubeFromInventory`:
```java
if (saved.getCubePos(team) != null) return true; // ponytail: cube already placed — porteur death is irrelevant
```

Same guard needed in `onPlayerDisconnect` PLACEMENT branch before `triggerVictory`.

### Task 2 — HIGH: Remove Slowness on cube placement success
**File**: `CubeConquestGameManagerEvents.java`

In `onUseBlock`, after `saved.setCubePos(cubeTeam, placedPos)` (successful cube placement), remove the Slowness effect from the porteur:

```java
ServerPlayer porteur = serverLevel.getServer().getPlayerList().getPlayer(porteurId);
if (porteur != null) porteur.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
```

This pairs with the existing `transitionToCombat` removal — porteur is freed as soon as they place.

### Task 3 — MEDIUM: Clear transient state on server lifecycle
**File**: `CubeConquestGameManagerEvents.java`

Register `ServerLifecycleEvents.SERVER_STOPPING` in `register()` to call `resetTransientState()`.
This prevents stale vote/timer state from leaking into a freshly loaded world on singleplayer.

```java
net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(
    server -> resetTransientState()
);
```

## Order of execution

1 → 2 → 3 (all in same file — sequential)

Each task: implementer subagent → reviewer subagent → commit on NICE.
