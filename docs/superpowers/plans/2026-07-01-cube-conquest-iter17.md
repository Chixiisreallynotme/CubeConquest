# Iter17 Implementation Plan — 2026-07-01

Base commit: d4d72a2

## Findings approved (5)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Dual non-creative porteur timeout → RED always loses (loop iteration bias) |
| 2 | HIGH | Both porteurs offline at PLACEMENT timeout → game stuck forever |
| 3 | MEDIUM | Cube item droppable — porteur loses cube with no in-game recovery |
| 4 | MEDIUM | Enemy breaks cube during PLACEMENT → first-placer penalized |
| 5 | LOW | Rogue cube items (picked up from drops) survive game end |

## Tasks

### Task 1 — HIGH+HIGH: Symmetric dual-timeout draw
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers findings #1 and #2. The existing dual-creative-porteur draw check (lines 244-256) only fires
when BOTH porteurs are online AND creative. Non-creative dual-timeout always kills RED first (loop
bias). Both-offline case never resolves (stuck game).

Replace the dual-creative check with a broader dual-failure check that fires whenever both porteurs
have unplaced cubes regardless of creative mode or online status:

```java
// ponytail: symmetric dual-timeout draw — covers non-creative, offline, and mixed cases
if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
    boolean redFailed = saved.getCubePos(Team.RED) == null && state.getPorteur(Team.RED) != null;
    boolean blueFailed = saved.getCubePos(Team.BLUE) == null && state.getPorteur(Team.BLUE) != null;
    if (redFailed && blueFailed) {
        triggerDraw(server);
        return;
    }
}
```

The for-loop below this block handles the one-sided timeout (only one team failed) exactly as before.

### Task 2 — MEDIUM+LOW: Cube item recovery + full sweep at game end
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers findings #3 and #5. Two changes:

**2a** — Add porteur item recovery in `handlePlacementTick` after the phantom-check block (~line 228):

```java
// ponytail: re-issue cube if porteur dropped it — no dedicated drop event; recover per-tick
for (Team team : Team.values()) {
    if (saved.getCubePos(team) != null) continue; // already placed
    UUID porteurId = state.getPorteur(team);
    if (porteurId == null) continue;
    ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
    if (porteur == null) continue;
    Item cubeItem = cubeBlockFor(team).asItem();
    boolean hasIt = false;
    for (int i = 0; i < porteur.getInventory().getContainerSize(); i++) {
        if (porteur.getInventory().getItem(i).getItem() == cubeItem) { hasIt = true; break; }
    }
    if (!hasIt) porteur.getInventory().add(new ItemStack(cubeItem));
}
```

**2b** — Add a `removeCubeItemsFromAll(MinecraftServer server)` helper that sweeps all online
players for both RED and BLUE cube items:

```java
// ponytail: sweep all online players — porteur-only cleanup misses dropped/given items
private static void removeCubeItemsFromAll(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        for (Team team : Team.values()) {
            removeCubeFromInventory(player, team);
        }
    }
}
```

Replace the porteur-only cube-item removal loops in `stopGame`, `triggerVictory`, and `triggerDraw`
with a single call to `removeCubeItemsFromAll(server)`.

### Task 3 — MEDIUM: Restrict cube break to COMBAT only
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

Covers finding #4. In `onBlockBreak`, line 446 currently allows cube-break logic during both
PLACEMENT and COMBAT. A player can break an enemy cube during PLACEMENT before the enemy has
a chance to place theirs.

Change line 446 from:
```java
if (state.getPhase() != GamePhase.PLACEMENT && state.getPhase() != GamePhase.COMBAT) return true;
```
to:
```java
if (state.getPhase() == GamePhase.PLACEMENT) return false; // ponytail: cube blocks indestructible during PLACEMENT
if (state.getPhase() != GamePhase.COMBAT) return true;
```

`return false` during PLACEMENT silently cancels the break (no drop, no event). The COMBAT path
is unchanged.

## Order of execution

Task 1: `handlePlacementTick` (dual-timeout section) — must be first
Tasks 2 + 3: parallel (Task 2 is multiple sections; Task 3 is `onBlockBreak`) — after Task 1

Each task: implementer subagent → reviewer subagent → commit on NICE.
