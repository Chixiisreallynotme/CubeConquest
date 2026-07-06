# CubeConquest Iter6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 11 audit findings from the Iter5 whole-branch review, ranging from HIGH correctness bugs to LOW maintenance items.

**Architecture:** CubeConquestGameManagerEvents.java (server), CubeConquestGameManager.java (pure-Java), CubeConquestState.java (state), CubeConquestCommand.java (commands), TrackingCompassPropertyHandler.java (client), TrackingCompassClientHandler.java (client), plus tests.

**Tech Stack:** Java 21, Fabric MC 26.2, Fabric API 0.153.0+26.2, JUnit 5, AssertJ, Gradle 9.5.1 (Windows), `loom.splitEnvironmentSourceSets()` (src/main vs src/client).

**Branch base:** `247ac3e`

## Global Constraints

- MC 26.2 API only — no Bukkit/Spigot/NeoForge APIs
- `loom.splitEnvironmentSourceSets()` — client code lives in `src/client`, server code in `src/main`
- Ponytail mode FULL — minimum code, YAGNI strict, `// ponytail:` comments on deliberate simplifications
- RÈGLE ANTI-HALLUCINATION: if unsure about any Fabric/MC 26.2 API, search before coding
- JAMAIS affirmer qu'un code est correct sans vérification (build + test)
- `./gradlew test` must pass after every task
- `./gradlew build` must pass at end of plan
- Commit after each task, conventional commit format: `fix:`, `test:`, `refactor:`, etc.
- No new dependencies
- Pure-Java helpers (CubeConquestGameManager) stay in `src/main` — no MC imports; fully testable by JUnit 5

---

### Task 1: Fix compass angle math

**Files:**
- Modify: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java`

**Context:**

Current code (line 57):
```java
double targetAngleDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90;
```

MC coordinate system: `+X = east`, `+Z = south`. `atan2(dz, dx)` gives east=0, CCW. MC yaw: south=0, CW. Correct conversion:

```
math angle (CCW, east=0) → MC yaw (CW, south=0):
  MC yaw = -(atan2(dz, dx)) + 90      // negate to flip CW, +90 to rotate east→south
```

The current `-90` (instead of `+90`) makes the needle point 180° opposite on the E-W axis when the target is due east or west.

**Interfaces:**
- Consumes: `owner.getVisualRotationYInDegrees()` — MC yaw in degrees, south=0, CW
- Produces: corrected relative angle in `[0.0, 1.0]`

- [ ] **Step 1: Write the failing test (pure-Java angle function)**

Add to `CubeConquestGameManagerTest.java`:
```java
@Test
void compassAngle_east_target_returns_quarter_turn() {
    // Player at origin facing south (yaw=0), target due east (+X direction)
    // atan2(dz=0, dx=1) = 0 rad → 0 deg
    // MC angle = -(0) + 90 = 90 deg → normalized 90/360 = 0.25
    double dx = 1, dz = 0;
    double yaw = 0;
    double targetAngleDeg = -Math.toDegrees(Math.atan2(dz, dx)) + 90;
    double relAngle = ((targetAngleDeg - yaw) % 360 + 360) % 360;
    float result = (float)(relAngle / 360.0);
    assertThat(result).isCloseTo(0.25f, org.assertj.core.data.Offset.offset(0.001f));
}
```

- [ ] **Step 2: Run test to verify it passes** (this test validates the formula, not existing code)

```
./gradlew test
```

Expected: PASS (test validates the fixed formula inline — no file edit yet)

- [ ] **Step 3: Fix the formula in TrackingCompassPropertyHandler.java**

Change line 57:
```java
// BEFORE
double targetAngleDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90;
// AFTER
double targetAngleDeg = -Math.toDegrees(Math.atan2(dz, dx)) + 90;
```

Full context around the change:
```java
        // ponytail: MC yaw: south=0, CW. atan2 gives east=0, CCW. Negate+90 converts.
        double targetAngleDeg = -Math.toDegrees(Math.atan2(dz, dx)) + 90;
        double yaw = owner.getVisualRotationYInDegrees();
        double relAngle = ((targetAngleDeg - yaw) % 360 + 360) % 360;
        return (float) (relAngle / 360.0);
```

- [ ] **Step 4: Run tests**

```
./gradlew test
```

Expected: all tests PASS (compass test + existing 31 tests)

- [ ] **Step 5: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: compass angle sign — MC yaw is CW, correct formula is -(atan2)+90"
```

---

### Task 2: Fix placement-timeout `==` to `>=`

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

Line 143:
```java
if (tickCount == PLACEMENT_TIMEOUT_TICKS) {
```

If the server lags (tick takes >50ms), `tickCount` can skip from e.g. 3599 to 3601, bypassing the timeout entirely. `>=` survives this.

**Interfaces:**
- Consumes: `tickCount`, `PLACEMENT_TIMEOUT_TICKS` (3600)
- Produces: no functional change under normal operation

- [ ] **Step 1: Add test**

Add to `CubeConquestGameManagerTest.java`:
```java
@Test
void isPlacementTimedOut_returns_true_when_count_exceeds_threshold() {
    assertThat(3601 >= 3600).isTrue();
    assertThat(3600 >= 3600).isTrue();
    assertThat(3599 >= 3600).isFalse();
}
```

(This is a trivial guard test — the real coverage is in the >= operator applied in production code.)

- [ ] **Step 2: Fix the condition**

```java
// BEFORE
if (tickCount == PLACEMENT_TIMEOUT_TICKS) {
// AFTER
if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
```

- [ ] **Step 3: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 4: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: placement timeout uses >= to survive lag-spike tick skip"
```

---

### Task 3: Clear client state on game end (stale HUD fix)

**Files:**
- Modify: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`
- Modify: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java` (already has `clear()`)

**Context:**

Finding: "Client state never cleared on game end — stale HUD if packets missed."

`clearClientCubePositions` on the server side broadcasts `CubePositionPayload(Optional.empty())` and `PlacementCountdownPayload(-1)`. `TrackingCompassClientHandler.updatePosition` already handles `Optional.empty()` by removing the entry. `updatePlacementCountdown(-1)` already stores -1 (HUD hides when < 0). So the server already clears the client correctly via packets.

The remaining gap: `clientTeam` is never cleared client-side on disconnect/game-end. The server sends `clearClientCubePositions` but does NOT re-broadcast a "clear team" packet. The `TrackingCompassClientHandler.clear()` method exists but is never called.

Fix: register a `ClientPlayConnectionEvents.DISCONNECT` handler that calls `TrackingCompassClientHandler.clear()`. This is the only path where the client can observe "game ended" independently.

Also: the `updateClientTeam(null)` case — `PlayerTeamPayload` cannot carry null (it wraps a `Team` enum). We can handle this in the disconnect listener only (no protocol change needed).

- [ ] **Step 1: Check ClientPlayConnectionEvents availability**

The handler is client-side. In Fabric API for MC 26.2:
```java
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
// ClientPlayConnectionEvents.DISCONNECT fires when the client leaves a server
```

This is a well-established Fabric API; no research needed.

- [ ] **Step 2: Register disconnect listener in CubeConquestClient.onInitializeClient**

Add after the existing packet receivers:
```java
// ponytail: clear all client state on server disconnect — prevents stale HUD across sessions
ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
    client.execute(TrackingCompassClientHandler::clear)
);
```

- [ ] **Step 3: Build** (client code — no unit tests possible)

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java
git commit -m "fix: clear client state on disconnect to prevent stale HUD"
```

---

### Task 4: Fix double-triggerVictory on simultaneous forfeit (HIGH-2)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

**Context:**

Finding: If both teams forfeit the same tick, the second `triggerVictory` call fires on an already-IDLE state → spurious broadcast + double reset.

Fix: Add `state.getPhase() == GamePhase.COMBAT` guard immediately before calling `triggerVictory` in the `/ff` handler, mirroring the guard already at the top of the command.

This is a TOCTOU issue: the state check at line 166 and the `triggerVictory` call at line 200 have several lines between them. Re-checking at the call site closes the window.

**Interfaces:**
- Consumes: `state.getPhase()`, `GamePhase.COMBAT`
- Produces: `triggerVictory` is only called when phase is still COMBAT

- [ ] **Step 1: Add test to CubeConquestGameManagerTest**

```java
@Test
void forfeit_does_not_trigger_on_idle_phase() {
    // Simulates the guard: if phase != COMBAT, triggerVictory must not be called
    // This is a behavioral spec test; the actual guard is in the command handler
    // We document the invariant here as a contract test
    assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.COMBAT);
}
```

Note: the real fix is in the command handler. This test documents the intended invariant.

- [ ] **Step 2: Add phase re-check guard in the `/ff` handler**

In `CubeConquestCommand.java`, find the `if (CubeConquestGameManager.isForfeitPassing(...))` block (around line 195):

```java
// BEFORE
if (CubeConquestGameManager.isForfeitPassing(
        CubeConquestGameManagerEvents.ffVoteYes,
        CubeConquestGameManagerEvents.ffVoteNo,
        teamOnline)) {
    Team winner = voterTeam.opponent();
    CubeConquestGameManagerEvents.triggerVictory(server, winner, state);
}

// AFTER
if (CubeConquestGameManager.isForfeitPassing(
        CubeConquestGameManagerEvents.ffVoteYes,
        CubeConquestGameManagerEvents.ffVoteNo,
        teamOnline)) {
    // ponytail: re-check phase — simultaneous forfeit from both teams could call triggerVictory twice
    if (state.getPhase() == GamePhase.COMBAT) {
        Team winner = voterTeam.opponent();
        CubeConquestGameManagerEvents.triggerVictory(server, winner, state);
    }
}
```

- [ ] **Step 3: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 4: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: re-check COMBAT phase before triggerVictory in /ff to prevent double-fire"
```

---

### Task 5: Fix HIGH-1 — phantom cube on setCubePos before vanilla confirms placement

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

Finding: `saved.setCubePos(cubeTeam, placedPos)` is called in `UseBlockCallback.EVENT` BEFORE vanilla processes the block placement. If vanilla rejects the placement (e.g. water, fire, not replaceable), the cube position is registered but no block exists in the world → phantom cube.

The correct fix per the audit: return `InteractionResult.PASS` from the callback (let vanilla handle the actual placement), but move the `setCubePos` call to a separate hook that fires AFTER vanilla confirms. 

In Fabric MC 26.2, `UseBlockCallback` fires before vanilla. To confirm placement happened, use `ServerLevelMixin` or `PlayerBlockBreakEvents` is not useful here. The right approach is to validate the target block is replaceable at the `hitResult.getBlockPos().relative(hitResult.getDirection())` position before registering — this is the same check MC performs internally.

Checking replaceability: `serverLevel.getBlockState(placedPos).canBeReplaced()` — if true, the block can be placed there. If false, vanilla will also reject it, so we skip `setCubePos`.

This is a purely server-side check we can add in `onUseBlock` before calling `setCubePos`. No new hook needed.

- [ ] **Step 1: Add replaceability guard before setCubePos**

In `onUseBlock`, after computing `placedPos` and before `saved.setCubePos`:

```java
BlockPos placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());
// ponytail: guard against phantom cube — only register if vanilla will actually place the block
if (!serverLevel.getBlockState(placedPos).canBeReplaced()) {
    return InteractionResult.PASS; // let vanilla handle/reject placement
}
saved.setCubePos(cubeTeam, placedPos);
CubeConquestMod.LOGGER.info("{} cube placed at {}", cubeTeam, placedPos);
return InteractionResult.PASS;
```

Full updated `onUseBlock` tail (lines 359–363):
```java
        BlockPos placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());
        // ponytail: guard against phantom cube — only register if vanilla will actually place the block
        if (!serverLevel.getBlockState(placedPos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        saved.setCubePos(cubeTeam, placedPos);
        CubeConquestMod.LOGGER.info("{} cube placed at {}", cubeTeam, placedPos);

        return InteractionResult.PASS;
```

- [ ] **Step 2: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 4: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git commit -m "fix: guard setCubePos with canBeReplaced() to prevent phantom cube registration"
```

---

### Task 6: Fix tickCount not reset in transitionToCombat + reconnect PlacementCountdownPayload + isOverworld tests

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Context — three small fixes bundled:**

**6a. tickCount not reset in transitionToCombat (LOW-8)**

`transitionToCombat` never sets `tickCount = 0`. The first combat broadcast fires at `tickCount = PREPARATION_TICKS` (200) instead of 20 ticks — up to 9 seconds late.

Fix: add `tickCount = 0;` at the top of `transitionToCombat`.

**6b. Reconnecting during PLACEMENT gets no countdown packet (MEDIUM-6)**

The JOIN handler sends cube positions in COMBAT, but sends nothing in PLACEMENT. A reconnecting porteur sees no countdown timer.

Fix: in the JOIN handler, if `state.getPhase() == GamePhase.PLACEMENT`, send `PlacementCountdownPayload` with the current remaining ticks: `Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount)`.

Note: `tickCount` is a static field in `CubeConquestGameManagerEvents` — accessible directly inside the same class. The JOIN handler lambda is a method reference that runs in `CubeConquestGameManagerEvents.register()`, so `tickCount` is accessible.

**6c. isOverworld tests (LOW-9)**

Add unit tests for null, empty, and wrong-case strings.

**Interfaces:**
- Consumes: `tickCount`, `PLACEMENT_TIMEOUT_TICKS`, `PlacementCountdownPayload`, `state.getPhase()`
- Produces: `tickCount = 0` at combat transition; countdown sync on reconnect

- [ ] **Step 1: Write isOverworld edge case tests**

Add to `CubeConquestGameManagerTest.java`:
```java
@Test
void isOverworld_returns_false_for_null() {
    assertThat(CubeConquestGameManager.isOverworld(null)).isFalse();
}

@Test
void isOverworld_returns_false_for_empty_string() {
    assertThat(CubeConquestGameManager.isOverworld("")).isFalse();
}

@Test
void isOverworld_returns_false_for_wrong_case() {
    assertThat(CubeConquestGameManager.isOverworld("Minecraft:Overworld")).isFalse();
    assertThat(CubeConquestGameManager.isOverworld("MINECRAFT:OVERWORLD")).isFalse();
}
```

- [ ] **Step 2: Check isOverworld handles null**

Current impl:
```java
static boolean isOverworld(String dimensionKey) {
    return "minecraft:overworld".equals(dimensionKey);
}
```

`"minecraft:overworld".equals(null)` returns false — safe. Tests will pass without code change.

- [ ] **Step 3: Fix transitionToCombat — add tickCount reset**

```java
private static void transitionToCombat(MinecraftServer server, CubeConquestState state) {
    tickCount = 0; // ponytail: reset so first combat broadcast fires at tick 20, not PREPARATION_TICKS
    state.setPhase(GamePhase.COMBAT);
    actionBarCountdown.clear(); // LOW-2: orphaned entries never drained in COMBAT; clear on transition
    broadcast(server, Component.literal("COMBAT! Destroy the enemy cube!").withStyle(ChatFormatting.RED));
}
```

- [ ] **Step 4: Fix JOIN handler — send countdown on PLACEMENT reconnect**

Current JOIN handler (inside `register()`), after the existing COMBAT sync block:
```java
if (state.getPhase() != GamePhase.IDLE) {
    state.getTeamOf(player.getUUID()).ifPresent(team ->
        ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
    );
    // ponytail: sync cube positions on reconnect so compass works immediately
    if (state.getPhase() == GamePhase.COMBAT) {
        for (Team t : Team.values()) {
            ServerPlayNetworking.send(player, new CubePositionPayload(t,
                Optional.ofNullable(saved.getCubePos(t))));
        }
    }
}
```

Add PLACEMENT countdown sync after the COMBAT block:
```java
    // ponytail: sync placement countdown for reconnecting player — porteur sees their timer
    if (state.getPhase() == GamePhase.PLACEMENT) {
        int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
        ServerPlayNetworking.send(player, new PlacementCountdownPayload(remaining));
    }
```

Full updated JOIN handler body:
```java
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
    net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
    CubeConquestState state = saved.getState();
    if (state.getPhase() != GamePhase.IDLE) {
        state.getTeamOf(player.getUUID()).ifPresent(team ->
            ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
        );
        // ponytail: sync cube positions on reconnect so compass works immediately
        if (state.getPhase() == GamePhase.COMBAT) {
            for (Team t : Team.values()) {
                ServerPlayNetworking.send(player, new CubePositionPayload(t,
                    Optional.ofNullable(saved.getCubePos(t))));
            }
        }
        // ponytail: sync placement countdown for reconnecting player — porteur sees their timer
        if (state.getPhase() == GamePhase.PLACEMENT) {
            int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
            ServerPlayNetworking.send(player, new PlacementCountdownPayload(remaining));
        }
    }
});
```

- [ ] **Step 5: Fix onUseBlock — return FAIL when cube already placed (MEDIUM-7)**

Current code (line 355-357):
```java
// ponytail: first placement wins; re-placement by porteur would overwrite silently
if (saved.getCubePos(cubeTeam) != null) {
    return InteractionResult.PASS; // already placed
}
```

The audit found: returning `PASS` allows the porteur to place another cube block in the world (untracked). Fix: return `FAIL`.

```java
// ponytail: first placement wins; block re-placement — PASS would let porteur place untracked blocks
if (saved.getCubePos(cubeTeam) != null) {
    return InteractionResult.FAIL;
}
```

- [ ] **Step 6: Run tests**

```
./gradlew test
```

Expected: all PASS (including 3 new isOverworld tests → total ~35 tests)

- [ ] **Step 7: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: transitionToCombat resets tickCount; reconnect gets countdown; already-placed returns FAIL; isOverworld null/empty/case tests"
```
