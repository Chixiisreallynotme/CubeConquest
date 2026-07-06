# Iter19 Implementation Plan — 2026-07-02

Base commit: 6ffdc6b

## Findings approved (3)

| # | Sev | Title |
|---|-----|-------|
| 1 | MEDIUM | 1-tick mobilité pour le nouveau porteur après transfert de cube pendant PLACEMENT |
| 2 | MEDIUM | Joueurs offline conservent cube/compass après fin de partie |
| 3 | LOW | startGame — add() return non vérifié (protégé par clearContent, fragile) |

## Tasks

### Task 1 — MEDIUM: Immobilize new porteur immediately after cube transfer during PLACEMENT
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

In `transferCubeOnDeath`, after `state.setPorteur(team, next.get())` and the `add` call,
add Slowness if the current phase is PLACEMENT:

```java
        state.setPorteur(team, next.get());
        ServerPlayer newPorteur = server.getPlayerList().getPlayer(next.get());
        if (newPorteur != null) {
            newPorteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()));
            actionBarCountdown.put(next.get(), 100);
            // ponytail: immobilize new porteur immediately — handlePlacementTick applies on next tick, 1-tick gap exploitable
            CubeConquestState state2 = CubeConquestSavedData.getServerState(server).getState(); // already have state param
```

Wait — `state` is already a parameter. Simpler:

```java
        state.setPorteur(team, next.get());
        ServerPlayer newPorteur = server.getPlayerList().getPlayer(next.get());
        if (newPorteur != null) {
            newPorteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()));
            actionBarCountdown.put(next.get(), 100);
            // ponytail: immobilize new porteur immediately — handlePlacementTick would cover next tick but 1-tick gap exists
            if (state.getPhase() == GamePhase.PLACEMENT && !newPorteur.isCreative()) {
                newPorteur.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS,
                    PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
            }
        }
```

`PLACEMENT_TIMEOUT_TICKS` is accessible (same class, static field).

### Task 2 — MEDIUM: Sweep offline players' inventories on JOIN if game is IDLE
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

In the JOIN handler, after the `if (state.getPhase() != GamePhase.IDLE)` block,
add a cleanup block for when the game IS idle (a player rejoins after the game ended
while they were offline):

```java
            } else {
                // ponytail: clean up orphaned cube/compass items from games that ended while player was offline
                for (Team team : Team.values()) {
                    removeCubeFromInventory(player, team);
                }
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                        inv.setItem(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }
```

The JOIN lambda currently has `if (state.getPhase() != GamePhase.IDLE) { ... }` with no else.
Add an `else` block.

### Task 3 — LOW: Check add() return in startGame
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

In `startGame`, the line:
```java
                porteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()));
```
Change to:
```java
                // ponytail: clearContent() above guarantees space; add() should never fail here
                if (!porteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()))) {
                    CubeConquestMod.LOGGER.error("Failed to give cube to porteur {} — inventory full despite clearContent", porteur.getUUID());
                }
```

This logs an error if the invariant is violated without changing behavior.

## Order of execution

Tasks 1, 2, 3: all in the same file, independent sections — run in parallel.

Each task: implementer subagent → reviewer subagent → commit on NICE.
