# Iter9 Implementation Plan — 2026-07-01

Base commit: e26f4dc

## Findings approved (10)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Tracking compass not removed on game end |
| 2 | HIGH | saved.setTickCount(0) missing in triggerVictory/triggerDraw |
| 3 | HIGH | CubePositionPayload Team.valueOf no try-catch |
| 4 | MEDIUM | Compass given to offline members at startGame — use members list |
| 5 | MEDIUM | No /draw cancel command |
| 6 | MEDIUM | Creative porteur timeout lacks phase re-check before triggerVictory |
| 7 | MEDIUM | resetGame test missing cubePos null assertions |
| 8 | MEDIUM | HUD shows exact enemy coordinates — remove coords, keep timer |
| 9 | LOW | Vote state via package-private statics — add accessor methods |
| 10 | LOW | Junk files in working tree (0, 0), 50%), backtick) |

## Tasks

### Task 1 — HIGH: Remove tracking compass on game end
**File**: `CubeConquestGameManagerEvents.java`

Add a helper `removeCompassFromAll(server)` and call it in `triggerVictory`, `triggerDraw`, `stopGame`.

```java
private static void removeCompassFromAll(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                inv.setItem(i, ItemStack.EMPTY);
                break; // ponytail: one compass per player
            }
        }
    }
}
```

Call in:
- `triggerVictory`: after `removeCubeBlocksFromWorld`, before `state.resetGame()`
- `triggerDraw`: same position
- `stopGame`: before `removeCubeBlocksFromWorld` (same position as cube item removal)

### Task 2 — HIGH: setTickCount(0) in triggerVictory and triggerDraw
**File**: `CubeConquestGameManagerEvents.java`

In `triggerVictory` (which has `server` param):
```java
CubeConquestSavedData.getServerState(server).setTickCount(0);
```

In `triggerDraw` (which already has `CubeConquestSavedData saved`):
```java
saved.setTickCount(0);
```

Add immediately after `removeCubeBlocksFromWorld(server)` in each.

### Task 3 — HIGH: CubePositionPayload Team.valueOf try-catch
**File**: `src/main/java/fr/chixi/cubeconquest/network/CubePositionPayload.java`

Line 21: wrap `Team.valueOf(buf.readUtf())` with try-catch, same pattern as `PlayerTeamPayload`:

Current:
```java
buf -> Team.valueOf(buf.readUtf())
```

Target:
```java
buf -> {
    try {
        return Team.valueOf(buf.readUtf());
    } catch (IllegalArgumentException ex) {
        // ponytail: S2C-only; safety net for mismatched packet
        return Team.RED;
    }
}
```

### Task 4 — MEDIUM: Use members list for compass distribution
**File**: `CubeConquestGameManagerEvents.java:295-298`

Replace:
```java
for (UUID id : state.getPlayers(team)) {
    ServerPlayer p = server.getPlayerList().getPlayer(id);
    if (p != null) p.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
}
```

With:
```java
for (UUID id : members) {
    ServerPlayer p = server.getPlayerList().getPlayer(id);
    if (p != null) p.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
}
```

`members` is already defined earlier in the same block (online-only list for this team).

### Task 5 — MEDIUM: /draw cancel command
**File**: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

Add `.then(Commands.literal("cancel").executes(...))` to the `/draw` registration, following the `/ff cancel` pattern:

```java
.then(Commands.literal("cancel")
    .executes(ctx -> {
        CommandSourceStack source = ctx.getSource();
        CubeConquestState state = CubeConquestSavedData.getServerState(source.getServer()).getState();
        if (state.getPhase() != GamePhase.COMBAT) {
            source.sendFailure(Component.literal("/draw is only available during COMBAT phase"));
            return 0;
        }
        ServerPlayer voter = source.getPlayerOrException();
        if (state.getTeamOf(voter.getUUID()).isEmpty()) {
            source.sendFailure(Component.literal("You are not on a team"));
            return 0;
        }
        CubeConquestGameManagerEvents.drawVoters.remove(voter.getUUID());
        source.sendSuccess(() -> Component.literal("You withdrew your draw vote"), false);
        return 1;
    })
)
```

### Task 6 — MEDIUM: Phase re-check before creative triggerVictory
**File**: `CubeConquestGameManagerEvents.java` — in `handlePlacementTick`, creative branch

Current (approx):
```java
if (porteur.isCreative()) {
    // Creative mode: kill() won't work; trigger loss directly
    triggerVictory(server, team.opponent(), state);
}
```

Target:
```java
if (porteur.isCreative()) {
    // ponytail: re-check phase — if other team already triggered victory this tick, skip
    if (state.getPhase() == GamePhase.PLACEMENT) {
        triggerVictory(server, team.opponent(), state);
    }
}
```

### Task 7 — MEDIUM: resetGame test adds cubePos null assertions
**File**: `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`

In `resetGame_preserves_rosters`, after setting up state, also set cube positions,
then after `resetGame()`, add:
```java
assertThat(state.getCubePos(Team.RED)).isNull();
assertThat(state.getCubePos(Team.BLUE)).isNull();
```

Full updated test:
```java
@Test void resetGame_preserves_rosters() {
    CubeConquestState state = new CubeConquestState();
    UUID red = UUID.randomUUID();
    UUID blue = UUID.randomUUID();
    state.addPlayer(Team.RED, red);
    state.addPlayer(Team.BLUE, blue);
    state.setPorteur(Team.RED, red);
    state.setCubePos(Team.RED, new int[]{1, 2, 3});
    state.resetGame();
    assertThat(state.getPlayers(Team.RED)).contains(red);
    assertThat(state.getPlayers(Team.BLUE)).contains(blue);
    assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
    assertThat(state.getPorteur(Team.RED)).isNull();
    assertThat(state.getPorteur(Team.BLUE)).isNull();
    assertThat(state.getCubePos(Team.RED)).isNull();
    assertThat(state.getCubePos(Team.BLUE)).isNull();
}
```

### Task 8 — MEDIUM: Remove enemy coordinates from HUD
**File**: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java`

Remove the `TrackingCompassClientHandler.getPosition(enemyTeam).ifPresent(...)` block entirely.
The compass needle already points at the enemy cube — exact coordinates are redundant and defeat navigation gameplay.
Keep the placement countdown display (unchanged).

The `clientTeam` lookup is only used for the removed block — remove it too if it's no longer needed after the change.

### Task 9 — LOW: Accessor methods for vote state
**File**: `CubeConquestGameManagerEvents.java`

Add package-private static methods to encapsulate draw/ff vote mutations:
```java
static boolean addDrawVote(UUID id) { return drawVoters.add(id); }
static void removeDrawVote(UUID id) { drawVoters.remove(id); }
static boolean addFfVote(Team team, UUID id) {
    return ffVoteYes.computeIfAbsent(team, k -> new HashSet<>()).add(id);
}
static void removeFfVote(Team team, UUID id) {
    Set<UUID> bucket = ffVoteYes.get(team); if (bucket != null) bucket.remove(id);
}
```

Update `CubeConquestCommand` to use these methods instead of accessing the fields directly.
Mark `drawVoters` and `ffVoteYes` `private static` after the commands are updated.

### Task 10 — LOW: Delete junk files
Delete `0`, `0)`, `50%)`, backtick files from working tree.

## Order of execution

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

Each task: implementer subagent → reviewer subagent → commit on NICE.
