# Iter11 Implementation Plan — 2026-07-01

Base commit: 1bf1518

## Findings approved (4)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Piston can displace cube → compass permanently stale during COMBAT |
| 2 | MEDIUM | triggerVictory/triggerDraw don't remove cube items from porteur inventories |
| 3 | MEDIUM | Porteur disconnect during PLACEMENT = instant loss (no transfer attempt) |
| 4 | LOW | Phantom-check clears cubePos but doesn't restore cube item to porteur |

## Tasks

### Task 1 — HIGH: Block piston push on CubeBlock
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java`

Add `.pushReaction(PushReaction.BLOCK)` to both cube block property chains:

```java
new CubeBlock(Team.RED, BlockBehaviour.Properties.of()
    .mapColor(MapColor.COLOR_RED)
    .strength(50f, 1200f)
    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))
```

Same for BLUE. Two-line change (one per block registration). Import: `net.minecraft.world.level.material.PushReaction`.

### Task 2 — MEDIUM: Remove cube items from porteurs in triggerVictory/triggerDraw
**File**: `CubeConquestGameManagerEvents.java`

`stopGame` (lines 352-358) already has porteur cube-item cleanup. Add the same pattern at the start of `triggerVictory` and `triggerDraw`, before `removeCompassFromAll` and `removeCubeBlocksFromWorld`:

```java
// ponytail: mirror stopGame cube-item cleanup — handles creative porteur who never placed
for (Team t : Team.values()) {
    UUID pid = state.getPorteur(t);
    if (pid != null) {
        ServerPlayer p = server.getPlayerList().getPlayer(pid);
        if (p != null) removeCubeFromInventory(p, t);
    }
}
```

### Task 3 — MEDIUM: Transfer cube on porteur disconnect during PLACEMENT
**File**: `CubeConquestGameManagerEvents.java`

Currently, porteur disconnect during PLACEMENT calls `triggerVictory(opponent)` immediately (no transfer). PREPARATION transfers via `transferCubeOnDeath`. Apply the same logic during PLACEMENT:

In `onPlayerDisconnect`, find the PLACEMENT disconnect branch (the one that fires when cube is NOT placed — i.e., after the `if (saved.getCubePos(team) != null) return;` guard). Replace the direct `triggerVictory(opponent)` with `transferCubeOnDeath` logic — exactly the same as the PREPARATION branch.

Pattern (mirrors the PREPARATION path):
```java
// ponytail: attempt transfer on disconnect — same logic as PREPARATION
List<UUID> candidates = state.getPlayers(team).stream()
    .filter(id -> !id.equals(player.getUUID()))
    .filter(id -> server.getPlayerList().getPlayer(id) != null)
    .toList();
if (candidates.isEmpty()) {
    triggerVictory(server, team.opponent(), state);
} else {
    UUID newPorteur = candidates.get(0);
    state.setPorteur(team, newPorteur);
    ServerPlayer newP = server.getPlayerList().getPlayer(newPorteur);
    if (newP != null) newP.getInventory().add(new ItemStack(/* cube block item for team */));
}
```

Check how the PREPARATION branch gives the cube item (it should use `CubeConquestMod.RED_CUBE_BLOCK` / `BLUE_CUBE_BLOCK` block items) and replicate exactly.

### Task 4 — LOW: Notify porteur when phantom-check clears their cubePos
**File**: `CubeConquestGameManagerEvents.java`

When `setCubePos(team, null)` is called in the phantom checker, notify the porteur and give back their cube item:

```java
// ponytail: restore cube item — phantom clears only via admin /setblock or mods
UUID porteurId = state.getPorteur(team);
ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
if (porteur != null) {
    porteur.getInventory().add(new ItemStack(/* cube block item for team */));
    porteur.sendSystemMessage(Component.literal("[CubeConquest] Your cube was removed from the world — place it again."));
}
```

## Order of execution

1 → 2 → 3 → 4 (Tasks 2-4 all touch GameManagerEvents — sequential)

Each task: implementer subagent → reviewer subagent → commit on NICE.
