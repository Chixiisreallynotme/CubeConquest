# Iter12 Implementation Plan — 2026-07-01

Base commit: 30ef28e

## Findings approved (2)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Slowness 127 not removed on game-end paths (stopGame/triggerVictory/triggerDraw) |
| 2 | HIGH | Former porteur retains Slowness 127 after reconnect — JOIN handler doesn't clear it |

## Tasks

### Task 1 — HIGH: Remove Slowness on all game-end paths
**File**: `CubeConquestGameManagerEvents.java`

Add a helper (mirroring `removeCompassFromAll`):
```java
private static void removeSlownessFromAll(MinecraftServer server) {
    // ponytail: sweep all online players — porteurs may change during transfer races
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
    }
}
```

Call it in:
- `triggerVictory`: after the porteur cube-item loop, before `removeCompassFromAll`
- `triggerDraw`: same position
- `stopGame`: after the porteur cube-item loop, before `removeCompassFromAll`

### Task 2 — HIGH: Clear stale Slowness on reconnect for non-porteur players
**File**: `CubeConquestGameManagerEvents.java`

In the JOIN handler, after the team/porteur/cube re-issue block, add:
```java
// ponytail: clear stale Slowness — former porteur may have disconnected mid-PLACEMENT before effect expired
state.getTeamOf(player.getUUID()).ifPresent(t -> {
    UUID currentPorteur = state.getPorteur(t);
    if (!player.getUUID().equals(currentPorteur)) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
    }
});
```

This removes Slowness from any reconnecting team member who is NOT the current active porteur.

## Order of execution

1 → 2 (same file — sequential)

Each task: implementer subagent → reviewer subagent → commit on NICE.
