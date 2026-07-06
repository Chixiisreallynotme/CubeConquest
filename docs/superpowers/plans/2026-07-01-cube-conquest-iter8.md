# Iter8 Implementation Plan — 2026-07-01

Base commit: 6c51fe7

## Findings approved (13)

| # | Sev | Title |
|---|-----|-------|
| 1 | HIGH | Slowness re-applied every tick — !hasEffect guard |
| 2 | HIGH | /stop callable in IDLE — destroys team rosters |
| 3 | HIGH | triggerVictory/triggerDraw clears rosters — rematch UX broken |
| 4 | MEDIUM | GamePhase.valueOf() crashes on corrupted save |
| 5 | MEDIUM | /draw no phase re-check before triggerDraw |
| 6 | MEDIUM | Compass not re-issued on reconnect |
| 7 | MEDIUM | Porteur immobilization loop runs post-timeout (redundant kills) |
| 8 | MEDIUM | No CubeConquestState unit tests |
| 9 | LOW | getPlayers() returns live-backed set — use Set.copyOf() |
| 10 | LOW | HUD shows exact enemy cube coords — add TODO comment |
| 11 | LOW | compassAngle test misleadingly named — rename |
| 12 | LOW | tickCount not reset in saved on game end |
| 13 | LOW | (Finding 6 ghost player — superseded by F3, N/A) |

## Tasks

### Task 1 — HIGH: Slowness guard (!hasEffect)
**File**: `CubeConquestGameManagerEvents.java:184-189`
**Change**: Wrap `addEffect` with `if (porteur != null && !porteur.hasEffect(MobEffects.SLOWNESS))`.
Eliminates per-tick object allocation; safe because MC replaces a lower-duration effect anyway,
but checking first avoids the alloc entirely.
```java
if (porteur != null && !porteur.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) {
    porteur.addEffect(new net.minecraft.world.effect.MobEffectInstance(
        net.minecraft.world.effect.MobEffects.SLOWNESS,
        PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
}
```

### Task 2 — HIGH: /stop IDLE guard
**File**: `CubeConquestCommand.java:50-55`
**Change**: Add `if (state.getPhase() == GamePhase.IDLE)` early-return with sendFailure.
```java
.then(Commands.literal("stop")
    .executes(ctx -> {
        MinecraftServer server = ctx.getSource().getServer();
        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
        if (state.getPhase() == GamePhase.IDLE) {
            ctx.getSource().sendFailure(Component.literal("No game is running."));
            return 0;
        }
        CubeConquestGameManagerEvents.stopGame(server);
        ctx.getSource().sendSuccess(() -> Component.literal("Game stopped."), true);
        return 1;
    }))
```

### Task 3 — HIGH: resetGame() preserves team rosters
**Files**: `CubeConquestState.java`, `CubeConquestGameManagerEvents.java`

Add `resetGame()` to `CubeConquestState`:
```java
/** Resets game state after a match ends, preserving team rosters for rematch. */
public void resetGame() {
    phase = GamePhase.IDLE;
    redPorteur = null; bluePorteur = null;
    redCubePos = null; blueCubePos = null;
    onDirty();
}
```

In `CubeConquestGameManagerEvents`:
- `triggerVictory`: replace `state.reset()` with `state.resetGame()`
- `triggerDraw`: replace `saved.getState().reset()` with `saved.getState().resetGame()`
- `stopGame`: keep `state.reset()` (admin teardown — intentionally clears rosters)

### Task 4 — MEDIUM: GamePhase.valueOf() crash guard
**File**: `CubeConquestSavedData.java:52`
**Change**:
```java
try { d.state.setPhase(GamePhase.valueOf(phase)); }
catch (IllegalArgumentException e) { d.state.setPhase(GamePhase.IDLE); }
```

### Task 5 — MEDIUM: /draw phase re-check before triggerDraw
**File**: `CubeConquestCommand.java:164-166`
**Change**:
```java
if (CubeConquestGameManager.isDrawThresholdMet(
        CubeConquestGameManagerEvents.drawVoters, redOnline, blueOnline)) {
    if (state.getPhase() == GamePhase.COMBAT) {
        CubeConquestGameManagerEvents.triggerDraw(server);
    }
}
```

### Task 6 — MEDIUM: Compass re-issued on reconnect
**File**: `CubeConquestGameManagerEvents.java` — JOIN handler (~line 86)
**Change**: After the cube re-issue block (after the PREPARATION/PLACEMENT cube re-issue), add
compass re-issue for any phase != IDLE:
```java
// ponytail: re-issue compass if player reconnects mid-game without one
if (state.getPhase() != GamePhase.IDLE) {
    state.getTeamOf(player.getUUID()).ifPresent(t -> {
        boolean hasCompass = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                hasCompass = true; break;
            }
        }
        if (!hasCompass) player.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
    });
}
```
Place this block AFTER the existing PREPARATION/PLACEMENT cube-re-issue block (after line 86),
BEFORE the COMBAT sync block (line 88).

### Task 7 — MEDIUM: Skip immobilization loop post-timeout
**File**: `CubeConquestGameManagerEvents.java:180-201`
**Change**: Skip the inner block when the porteur is already in `timeoutDeaths`:
```java
if (saved.getCubePos(team) == null && state.getPorteur(team) != null
        && !timeoutDeaths.contains(state.getPorteur(team))) {
```
This prevents re-applying `addEffect` and re-calling `kill()`/`triggerVictory` for a porteur
already being processed. `timeoutDeaths` is cleared on `resetTransientState`.

### Task 8 — MEDIUM: CubeConquestStateTest
**File**: new `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`
Tests to add:
1. `addPlayer_moves_player_between_teams` — add RED then BLUE, verify not in RED
2. `getTeamOf_returns_correct_team` — add to RED, assert getTeamOf returns RED
3. `getTeamOf_returns_empty_for_unknown` — new UUID, assert empty
4. `resetGame_preserves_rosters` — addPlayer RED/BLUE, resetGame, assert players still present
5. `reset_clears_rosters` — addPlayer RED/BLUE, reset, assert players gone
6. `setCubePos_stores_and_retrieves` — set int[3], get back same values
7. `getCubePos_returns_defensive_copy` — mutate returned array, assert stored unchanged

### Task 9 — LOW batch (getPlayers copyOf + HUD comment + test rename + tickCount reset)
**Files**: `CubeConquestState.java`, `CubeConquestClient.java` (or HUD file), `CubeConquestGameManagerTest.java`, `CubeConquestGameManagerEvents.java` / `stopGame`

9a. `getPlayers()` → `Set.copyOf(...)` in `CubeConquestState.java`
9b. HUD: add `// TODO: consider hiding exact coords behind config — compass bearing already gives direction` near enemy cube coordinate display
9c. Rename test `compassAngle_east_target_returns_quarter_turn` → `compassAngle_formula_east_returns_quarter_turn_sanity_check`
9d. `saved.setTickCount(0)` in `stopGame` after `state.reset()` to ensure persistence is clean

## Order of execution

1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 (batch)

Each task: implementer subagent → reviewer subagent → commit on NICE.
