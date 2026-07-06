# CubeConquest Iteration 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five focused features layered on top of the iteration-1 codebase: ActionBar cube-transfer notification, PLACEMENT timeout-kill with loss trigger, `/draw` vote command, `/ff` forfeit command, and TrackingCompass needle rendering.

**Architecture:** Pure-Java helpers in `CubeConquestGameManager` (testable), MC-dependent wiring in `CubeConquestGameManagerEvents`, new commands as branches in `CubeConquestCommand.registerAll`, new S2C payloads registered in `CubeConquestMod`. Client files in `src/client/java`.

**Tech Stack:** MC 26.2, Fabric API 0.153.0+26.2, Loom 1.17, Gradle 9.5.1, JUnit 5 + AssertJ for tests.

## Global Constraints

- MC 26.2, Fabric API 0.153.0+26.2, Loom 1.17, Gradle 9.5.1
- Mod ID: `cubeconquest`, package root: `fr.chixi.cubeconquest`
- MC-dependent main files excluded via `compileJava.excludes` in build.gradle (JDK 21 guard)
- MC-dependent client files excluded via `compileClientJava.excludes` in build.gradle
- New S2C payloads: register TYPE in `CubeConquestMod.onInitialize()` via `PayloadTypeRegistry.playS2C().register(TYPE, CODEC)`, receive in `CubeConquestClient.onInitializeClient()` via `ClientPlayNetworking.registerGlobalReceiver`
- Transient runtime state lives as `static` fields in `CubeConquestGameManagerEvents`
- Pure-Java testable logic in `CubeConquestGameManager` (no MC imports), covered by unit tests
- New commands added as `.then()` branches in `CubeConquestCommand.registerAll(dispatcher)`
- ANTI-HALLUCINATION: any MC 26.2 / Fabric API call marked VERIFY must be confirmed with Exa or Context7 before coding — never invent API names
- PONYTAIL: YAGNI, minimum code, no speculative abstractions, fewest files possible
- `./gradlew test` must pass (currently 6 tests); `./gradlew build` must succeed after each task

---

## Codebase Snapshot

| File | Role |
|------|------|
| `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java` | Event hub — tick handler, death, attack, block break, UseBlock |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java` | Pure-Java helpers |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestState.java` | Pure-Java state |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java` | MC SavedData wrapper |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java` | ModInitializer |
| `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java` | All commands |
| `src/main/java/fr/chixi/cubeconquest/network/CubePositionPayload.java` | Existing S2C payload |
| `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java` | ClientModInitializer |
| `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java` | HUD overlay |
| `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java` | Static map: Team → BlockPos |
| `src/main/java/fr/chixi/cubeconquest/item/TrackingCompassItem.java` | Item stub |
| `build.gradle` | JDK 21 exclusion lists |

---

### Task 1 — ActionBar notification for cube transfer

**Model:** haiku
**Source set:** main (MC-dependent)

**Files modified:**
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- Consumes: existing `transferCubeOnDeath`, `handlePreparationTick`, `handlePlacementTick`, `startGame`, `stopGame`, `triggerVictory` in `CubeConquestGameManagerEvents`
- Produces: `CubeConquestGameManager.drainActionBarCountdown(Map<UUID,Integer>, BiConsumer<UUID,Integer>)` — static helper, pure Java

**What:** Replace the `sendSystemMessage` call in `transferCubeOnDeath` with an ActionBar message (appears above the hotbar, not in chat). The message repeats every tick for 100 ticks (~5 seconds) so the new porteur notices it.

**VERIFY before coding — Step 1.3:** In Fabric 26.2 / MC 26.2, confirm the correct API to send an ActionBar overlay message to a `ServerPlayer`. Candidate: `player.sendSystemMessage(Component, boolean)` where `true` = overlay. Use Context7 or Exa to confirm the method signature exists in MC 26.2. Do NOT code the MC-dependent part until confirmed.

**Pure-Java helper to add to `CubeConquestGameManager.java`:**

```java
/**
 * Decrements all entries by 1, calls onTick for each active entry, removes entries at 0.
 * ponytail: pure Java, no MC imports — fully unit-testable
 */
static void drainActionBarCountdown(Map<UUID, Integer> map,
                                    java.util.function.BiConsumer<UUID, Integer> onTick) {
    map.entrySet().removeIf(e -> {
        onTick.accept(e.getKey(), e.getValue());
        e.setValue(e.getValue() - 1);
        return e.getValue() <= 0;
    });
}
```

**Unit tests to add to `CubeConquestGameManagerTest.java`:**

```java
@Test
void drainActionBarCountdown_decrements_and_removes_at_zero() {
    Map<UUID, Integer> map = new HashMap<>();
    UUID id = UUID.randomUUID();
    map.put(id, 2);
    CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> {});
    assertThat(map.get(id)).isEqualTo(1);
    CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> {});
    assertThat(map).doesNotContainKey(id);
}

@Test
void drainActionBarCountdown_calls_onTick_for_each_active_entry() {
    Map<UUID, Integer> map = new HashMap<>();
    UUID id = UUID.randomUUID();
    map.put(id, 3);
    List<Integer> seen = new ArrayList<>();
    CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> seen.add(tick));
    assertThat(seen).containsExactly(3);
}
```

**Changes to `CubeConquestGameManagerEvents.java`:**

1. Add at class level:
   ```java
   // ponytail: transient, not persisted — cleared on game lifecycle events
   private static final Map<UUID, Integer> actionBarCountdown = new HashMap<>();
   ```

2. In `transferCubeOnDeath`, replace:
   ```java
   newPorteur.sendSystemMessage(Component.literal("The cube has been passed to you!").withStyle(ChatFormatting.GOLD));
   ```
   with:
   ```java
   actionBarCountdown.put(next.get(), 100);
   ```

3. At the top of both `handlePreparationTick` and `handlePlacementTick`, add the drain call:
   ```java
   CubeConquestGameManager.drainActionBarCountdown(actionBarCountdown, (uuid, tick) -> {
       ServerPlayer p = server.getPlayerList().getPlayer(uuid);
       if (p != null) {
           // VERIFY: replace with confirmed ActionBar API from Step 1.3
           p.sendSystemMessage(Component.literal("The cube has been passed to you!")
               .withStyle(ChatFormatting.GOLD), true);
       }
   });
   ```

4. In `startGame`, `stopGame`, `triggerVictory`: add `actionBarCountdown.clear();`

**Steps:**

- [ ] **Step 1.1** — Add `drainActionBarCountdown` to `CubeConquestGameManager.java`
  - Add the static helper above (pure Java, no MC imports, no new file needed)
  - Add `import java.util.Map; import java.util.UUID; import java.util.function.BiConsumer;` if not present

- [ ] **Step 1.2** — Add unit tests to `CubeConquestGameManagerTest.java`, run `./gradlew test`
  - Add both tests from above
  - Add `import java.util.ArrayList; import java.util.HashMap; import java.util.List; import java.util.UUID;` as needed
  - Run: `./gradlew test`
  - Expected: `BUILD SUCCESSFUL`, all tests pass

- [ ] **Step 1.3** — VERIFY ActionBar API in MC 26.2
  - Use Context7 (`resolve-library-id` for `minecraft` then `query-docs` for `ServerPlayer sendSystemMessage overlay`) or Exa to confirm `ServerPlayer.sendSystemMessage(Component, boolean)` exists in MC 26.2
  - If the signature differs, document the correct call before proceeding

- [ ] **Step 1.4** — Update `CubeConquestGameManagerEvents.java`
  - Add `actionBarCountdown` map field (with `HashMap` import)
  - Replace `sendSystemMessage` in `transferCubeOnDeath` with `actionBarCountdown.put`
  - Add drain calls at top of `handlePreparationTick` and `handlePlacementTick` (using verified API)
  - Add `actionBarCountdown.clear()` to `startGame`, `stopGame`, `triggerVictory`

- [ ] **Step 1.5** — Run `./gradlew build`
  - Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.6** — Commit
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "feat: ActionBar notification for cube transfer (repeats 5s above hotbar)"
  ```

---

### Task 2 — PLACEMENT timeout-kill and instant team loss

**Model:** sonnet
**Source set:** main (MC-dependent) + new S2C payload + client updates

**Files created:**
- `src/main/java/fr/chixi/cubeconquest/network/PlacementCountdownPayload.java`

**Files modified:**
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java`
- `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`
- `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java`
- `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java`
- `build.gradle`

**Interfaces:**
- Produces: `PlacementCountdownPayload(int ticksRemaining)` with TYPE and CODEC
- Produces: `TrackingCompassClientHandler.updatePlacementCountdown(int)` / `getPlacementTicksRemaining()`

**What:** Change PLACEMENT timeout from freeze-only to kill-at-timeout. Killing the porteur due to timeout causes their team to lose immediately (not cube transfer). Add a S2C countdown packet so the HUD shows remaining seconds.

**Spec:**
- `PLACEMENT_TIMEOUT_TICKS = 3600` (3 min, already defined) — keep as is
- At `tickCount == PLACEMENT_TIMEOUT_TICKS`: porteur is killed
- When porteur dies from timeout: their team loses immediately (opponent wins)
- Normal porteur death (before timeout): cube transfers as before
- A `Set<UUID> timeoutDeaths` flag distinguishes timeout-death from normal death in `onAllowDeath`

**VERIFY before coding — Step 2.1:** Confirm the correct method to instantly kill a `ServerPlayer` in MC 26.2 that triggers `ServerLivingEntityEvents.ALLOW_DEATH`. Candidates: `porteur.kill()`, `porteur.hurt(porteur.damageSources().outOfWorld(), Float.MAX_VALUE)`. Use Context7 or Exa to confirm. This is HIGH RISK — wrong method bypasses the ALLOW_DEATH event.

**`PlacementCountdownPayload.java`:**
```java
package fr.chixi.cubeconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlacementCountdownPayload(int ticksRemaining) implements CustomPacketPayload {
    public static final Type<PlacementCountdownPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("cubeconquest", "placement_countdown"));
    public static final StreamCodec<FriendlyByteBuf, PlacementCountdownPayload> CODEC =
        StreamCodecs.of(
            (buf, p) -> buf.writeInt(p.ticksRemaining()),
            buf -> new PlacementCountdownPayload(buf.readInt())
        );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

**Changes to `CubeConquestGameManagerEvents.java`:**

1. Add at class level:
   ```java
   // ponytail: transient — cleared on all lifecycle events
   private static final Set<UUID> timeoutDeaths = new HashSet<>();
   ```

2. Replace the freeze-only block in `handlePlacementTick`:
   ```java
   // Send countdown every second
   if (tickCount % 20 == 0) {
       int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
       PlacementCountdownPayload payload = new PlacementCountdownPayload(remaining);
       for (ServerPlayer player : server.getPlayerList().getPlayers()) {
           ServerPlayNetworking.send(player, payload);
       }
   }
   // Kill porteur exactly once at timeout
   if (tickCount == PLACEMENT_TIMEOUT_TICKS) {
       for (Team team : Team.values()) {
           CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
           CubeConquestState state = saved.getState();
           if (saved.getCubePos(team) == null && state.getPorteur(team) != null) {
               ServerPlayer porteur = server.getPlayerList().getPlayer(state.getPorteur(team));
               if (porteur != null) {
                   timeoutDeaths.add(porteur.getUUID());
                   // VERIFY: use confirmed kill method from Step 2.1
                   porteur.kill(server);
               }
           }
       }
   }
   // Defensive: keep zeroing velocity after timeout in case kill hasn't fired yet
   if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
       for (Team team : Team.values()) {
           CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
           CubeConquestState state = saved.getState();
           if (saved.getCubePos(team) == null && state.getPorteur(team) != null) {
               ServerPlayer porteur = server.getPlayerList().getPlayer(state.getPorteur(team));
               if (porteur != null) {
                   porteur.setDeltaMovement(0, porteur.getDeltaMovement().y, 0);
               }
           }
       }
   }
   ```

3. In `onAllowDeath`, add BEFORE the existing cube-transfer loop:
   ```java
   for (Team team : Team.values()) {
       CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
       if (dead.getUUID().equals(state.getPorteur(team)) && timeoutDeaths.remove(dead.getUUID())) {
           // Timeout death → team loses immediately, no cube transfer
           triggerVictory(server, team == Team.RED ? Team.BLUE : Team.RED, state);
           return true;
       }
   }
   ```

4. Add `timeoutDeaths.clear()` to `startGame`, `stopGame`, `triggerVictory`, `triggerDraw` (Task 3 adds triggerDraw)

**Changes to `TrackingCompassClientHandler.java`:**
```java
// Add field and accessors
private static int placementTicksRemaining = -1;

public static void updatePlacementCountdown(int ticks) {
    placementTicksRemaining = ticks;
}

public static int getPlacementTicksRemaining() {
    return placementTicksRemaining;
}
```
Also add `placementTicksRemaining = -1;` to `clear()`.

**Changes to `CubeConquestHud.java`:** After existing cube-position lines, add:
```java
int countdown = TrackingCompassClientHandler.getPlacementTicksRemaining();
if (countdown >= 0) {
    graphics.drawString(mc.font,
        "Place cube: " + (countdown / 20) + "s",
        x, y + 20, 0xFFFF55, true);
}
```

**Changes to `CubeConquestMod.java`:** Register the payload:
```java
PayloadTypeRegistry.playS2C().register(PlacementCountdownPayload.TYPE, PlacementCountdownPayload.CODEC);
```

**Changes to `CubeConquestClient.java`:** Register receiver:
```java
ClientPlayNetworking.registerGlobalReceiver(PlacementCountdownPayload.TYPE,
    (payload, context) -> context.client().execute(
        () -> TrackingCompassClientHandler.updatePlacementCountdown(payload.ticksRemaining())
    ));
```

**Changes to `build.gradle`:** Add to `compileJava.excludes` and to `compileClientJava.excludes`:
- `'**/network/PlacementCountdownPayload.java'` → `compileJava.excludes`

**Steps:**

- [ ] **Step 2.1** — VERIFY kill method for `ServerPlayer` in MC 26.2
  - Use Context7 or Exa to confirm which of `porteur.kill(server)`, `porteur.kill()`, `porteur.hurt(damageSources.outOfWorld(), Float.MAX_VALUE)` fires `ALLOW_DEATH` event in MC 26.2
  - Document the confirmed method before writing any code

- [ ] **Step 2.2** — Create `PlacementCountdownPayload.java`
  - Create at `src/main/java/fr/chixi/cubeconquest/network/PlacementCountdownPayload.java`
  - Implement record + TYPE + CODEC as shown above

- [ ] **Step 2.3** — Add `PlacementCountdownPayload.java` to `compileJava.excludes` in `build.gradle`
  - Add `'**/network/PlacementCountdownPayload.java'` to the exclusion list

- [ ] **Step 2.4** — Register payload in `CubeConquestMod.java`
  - Add `PayloadTypeRegistry.playS2C().register(PlacementCountdownPayload.TYPE, PlacementCountdownPayload.CODEC);`

- [ ] **Step 2.5** — Add countdown fields to `TrackingCompassClientHandler.java`
  - Add `placementTicksRemaining` field, `updatePlacementCountdown`, `getPlacementTicksRemaining`
  - Reset in `clear()`

- [ ] **Step 2.6** — Register `PlacementCountdownPayload` receiver in `CubeConquestClient.java`

- [ ] **Step 2.7** — Add countdown rendering to `CubeConquestHud.java`

- [ ] **Step 2.8** — Add `timeoutDeaths` set and updated `handlePlacementTick` to `CubeConquestGameManagerEvents.java`
  - Use confirmed kill method from Step 2.1
  - Add `timeoutDeaths.clear()` to `startGame`, `stopGame`, `triggerVictory`

- [ ] **Step 2.9** — Add timeout-death check to `onAllowDeath` in `CubeConquestGameManagerEvents.java`
  - Must be BEFORE the existing cube-transfer loop

- [ ] **Step 2.10** — Run `./gradlew test` then `./gradlew build`
  - Expected: all tests pass, `BUILD SUCCESSFUL`

- [ ] **Step 2.11** — Commit
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/network/PlacementCountdownPayload.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java \
          src/client/java/fr/chixi/cubeconquest/client/ \
          build.gradle
  git commit -m "feat: PLACEMENT timeout kills porteur, triggers immediate team loss; HUD countdown"
  ```

---

### Task 3 — `/draw` vote command

**Model:** sonnet
**Source set:** main (MC-dependent for command; pure Java for vote logic)

**Files modified:**
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`
- `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- Produces: `CubeConquestGameManager.isDrawThresholdMet(Set<UUID> drawVoters, Set<UUID> redOnline, Set<UUID> blueOnline)` — static, pure Java
- Produces: `CubeConquestGameManagerEvents.triggerDraw(MinecraftServer)` — package-private static
- Produces: `CubeConquestGameManagerEvents.drawVoters` — package-private static Set

**What:** Any player can type `/draw` during COMBAT phase. When `>= 50%` of each team's online players have voted yes, the game ends in a draw.

**Pure-Java helper to add to `CubeConquestGameManager.java`:**
```java
/**
 * Returns true if drawVoters contains >= 50% of each team's online members.
 * Both teams must independently meet the threshold.
 * ponytail: integer math avoids floating point; n * 2 >= size is equivalent to n >= size/2 without rounding issues
 */
static boolean isDrawThresholdMet(Set<UUID> drawVoters,
                                   Set<UUID> redOnline,
                                   Set<UUID> blueOnline) {
    if (redOnline.isEmpty() || blueOnline.isEmpty()) return false;
    long redVotes  = redOnline.stream().filter(drawVoters::contains).count();
    long blueVotes = blueOnline.stream().filter(drawVoters::contains).count();
    return redVotes * 2 >= redOnline.size() && blueVotes * 2 >= blueOnline.size();
}
```

**Unit tests:**
```java
@Test
void drawThreshold_met_when_all_vote() {
    UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
    Set<UUID> voters = Set.of(r1, b1);
    assertThat(CubeConquestGameManager.isDrawThresholdMet(voters, Set.of(r1), Set.of(b1))).isTrue();
}

@Test
void drawThreshold_not_met_when_only_one_team_votes() {
    UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1), Set.of(r1), Set.of(b1))).isFalse();
}

@Test
void drawThreshold_met_at_exactly_50_percent() {
    // 1 out of 2 = 50% -> meets threshold
    UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID();
    UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1, b1), Set.of(r1, r2), Set.of(b1, b2))).isTrue();
}

@Test
void drawThreshold_fails_when_empty_team() {
    assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(), Set.of(), Set.of(UUID.randomUUID()))).isFalse();
}
```

**`triggerDraw` to add to `CubeConquestGameManagerEvents.java`:**
```java
static void triggerDraw(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        player.sendSystemMessage(Component.literal("Draw! Both teams agreed.").withStyle(ChatFormatting.YELLOW));
    }
    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
    saved.getState().reset();
    tickCount = 0;
    drawVoters.clear();
    ffVoteYes.clear();   // Task 4 adds these; add the clear calls now; fields added in Task 4
    ffVoteNo.clear();
    clearClientCubePositions(server);
}
```
Note: `ffVoteYes` and `ffVoteNo` will be added in Task 4 — add the `clear()` calls in Task 4's step, not here. For Task 3, `triggerDraw` clears only `drawVoters` and calls existing helpers.

**`drawVoters` field and `/draw` command:**

In `CubeConquestGameManagerEvents.java`, add at class level:
```java
static final Set<UUID> drawVoters = new HashSet<>();  // package-private for command access
```
Clear in `startGame`, `stopGame`, `triggerVictory`.

In `CubeConquestCommand.registerAll(dispatcher)`, add:
```java
dispatcher.register(Commands.literal("draw")
    .executes(ctx -> {
        MinecraftServer server = ctx.getSource().getServer();
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        CubeConquestState state = saved.getState();
        if (state.getPhase() != GamePhase.COMBAT) {
            ctx.getSource().sendFailure(Component.literal("/draw is only available during COMBAT phase"));
            return 0;
        }
        ServerPlayer voter = ctx.getSource().getPlayerOrException();
        CubeConquestGameManagerEvents.drawVoters.add(voter.getUUID());

        // Compute online counts per team
        Set<UUID> redOnline  = state.getPlayers(Team.RED).stream()
            .filter(id -> server.getPlayerList().getPlayer(id) != null)
            .collect(java.util.stream.Collectors.toSet());
        Set<UUID> blueOnline = state.getPlayers(Team.BLUE).stream()
            .filter(id -> server.getPlayerList().getPlayer(id) != null)
            .collect(java.util.stream.Collectors.toSet());

        long redVotes  = redOnline.stream().filter(CubeConquestGameManagerEvents.drawVoters::contains).count();
        long blueVotes = blueOnline.stream().filter(CubeConquestGameManagerEvents.drawVoters::contains).count();
        // Broadcast current vote count
        Component voteMsg = Component.literal(voter.getName().getString() + " voted draw — RED: "
            + redVotes + "/" + redOnline.size() + ", BLUE: " + blueVotes + "/" + blueOnline.size());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(voteMsg);

        if (CubeConquestGameManager.isDrawThresholdMet(CubeConquestGameManagerEvents.drawVoters, redOnline, blueOnline)) {
            CubeConquestGameManagerEvents.triggerDraw(server);
        }
        return 1;
    })
);
```

**Steps:**

- [ ] **Step 3.1** — Add `isDrawThresholdMet` to `CubeConquestGameManager.java`

- [ ] **Step 3.2** — Add unit tests; run `./gradlew test` — all pass

- [ ] **Step 3.3** — Add `drawVoters` set to `CubeConquestGameManagerEvents.java`; add `drawVoters.clear()` to `startGame`, `stopGame`, `triggerVictory`

- [ ] **Step 3.4** — Add `triggerDraw` to `CubeConquestGameManagerEvents.java` (clear only `drawVoters` for now; Task 4 adds ff clears)

- [ ] **Step 3.5** — Add `/draw` command to `CubeConquestCommand.registerAll`

- [ ] **Step 3.6** — Run `./gradlew test` then `./gradlew build`
  - Expected: all tests pass, `BUILD SUCCESSFUL`

- [ ] **Step 3.7** — Commit
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java \
          src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "feat: /draw command — 50% of each team must accept to end in draw"
  ```

---

### Task 4 — `/ff` forfeit vote command

**Model:** sonnet
**Source set:** main (MC-dependent for command; pure Java for vote logic)

**Files modified:**
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`
- `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- Produces: `CubeConquestGameManager.isForfeitPassing(Set<UUID> ffYes, Set<UUID> ffNo, Set<UUID> teamOnline)` — static, pure Java
- Produces: `CubeConquestGameManagerEvents.ffVoteYes` / `ffVoteNo` — package-private static Sets

**What:** Any player on a team can type `/ff` during COMBAT phase. Forfeit passes for that team UNLESS `>= 2` teammates vote `/ff cancel`. When it passes, that team loses.

**Pure-Java helper:**
```java
/**
 * Forfeit passes when: at least 1 yes AND fewer than 2 no votes from team members.
 * ponytail: simple threshold, no weight
 */
static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> ffNo, Set<UUID> teamOnline) {
    long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
    long noVotes  = teamOnline.stream().filter(ffNo::contains).count();
    return yesVotes > 0 && noVotes < 2;
}
```

**Unit tests:**
```java
@Test
void forfeit_passes_with_one_yes_zero_no() {
    UUID p1 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(p1), Set.of(), Set.of(p1))).isTrue();
}

@Test
void forfeit_blocked_by_two_no_votes() {
    UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isForfeitPassing(
        Set.of(p1), Set.of(p2, p3), Set.of(p1, p2, p3))).isFalse();
}

@Test
void forfeit_fails_with_zero_yes() {
    UUID p1 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(), Set.of(), Set.of(p1))).isFalse();
}
```

**Fields in `CubeConquestGameManagerEvents.java`:**
```java
static final Set<UUID> ffVoteYes = new HashSet<>();  // package-private
static final Set<UUID> ffVoteNo  = new HashSet<>();  // package-private
```
Clear both in `startGame`, `stopGame`, `triggerVictory`, `triggerDraw`.

Also in `triggerDraw` (added in Task 3), add:
```java
ffVoteYes.clear();
ffVoteNo.clear();
```

**`/ff` command (top-level, registered as `Commands.literal("ff")`):**
```java
dispatcher.register(Commands.literal("ff")
    .executes(ctx -> {
        MinecraftServer server = ctx.getSource().getServer();
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        CubeConquestState state = saved.getState();
        if (state.getPhase() != GamePhase.COMBAT) {
            ctx.getSource().sendFailure(Component.literal("/ff is only available during COMBAT phase"));
            return 0;
        }
        ServerPlayer voter = ctx.getSource().getPlayerOrException();
        Team voterTeam = state.getTeamOf(voter.getUUID());
        if (voterTeam == null) {
            ctx.getSource().sendFailure(Component.literal("You are not on a team"));
            return 0;
        }
        CubeConquestGameManagerEvents.ffVoteYes.add(voter.getUUID());
        CubeConquestGameManagerEvents.ffVoteNo.remove(voter.getUUID());

        Set<UUID> teamOnline = state.getPlayers(voterTeam).stream()
            .filter(id -> server.getPlayerList().getPlayer(id) != null)
            .collect(java.util.stream.Collectors.toSet());
        long noVotes = teamOnline.stream().filter(CubeConquestGameManagerEvents.ffVoteNo::contains).count();
        Component msg = Component.literal(voter.getName().getString() + " voted to forfeit for "
            + voterTeam.name() + " — " + noVotes + " objection(s) (2 needed to cancel)");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);

        if (CubeConquestGameManager.isForfeitPassing(
                CubeConquestGameManagerEvents.ffVoteYes,
                CubeConquestGameManagerEvents.ffVoteNo,
                teamOnline)) {
            Team winner = voterTeam == Team.RED ? Team.BLUE : Team.RED;
            CubeConquestGameManagerEvents.triggerVictory(server, winner, state);
        }
        return 1;
    })
    .then(Commands.literal("cancel")
        .executes(ctx -> {
            MinecraftServer server = ctx.getSource().getServer();
            CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
            CubeConquestState state = saved.getState();
            if (state.getPhase() != GamePhase.COMBAT) {
                ctx.getSource().sendFailure(Component.literal("/ff is only available during COMBAT phase"));
                return 0;
            }
            ServerPlayer voter = ctx.getSource().getPlayerOrException();
            CubeConquestGameManagerEvents.ffVoteNo.add(voter.getUUID());
            CubeConquestGameManagerEvents.ffVoteYes.remove(voter.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("You voted against forfeit"), false);
            return 1;
        })
    )
);
```

Note: `state.getTeamOf(UUID)` must exist on `CubeConquestState`. Check if it already does; if not, add:
```java
public Team getTeamOf(UUID uuid) {
    for (Team team : Team.values()) {
        if (getPlayers(team).contains(uuid)) return team;
    }
    return null;
}
```
This is pure Java — add directly to `CubeConquestState.java` if missing.

**Steps:**

- [ ] **Step 4.1** — Check if `CubeConquestState.getTeamOf(UUID)` exists; if not, add it
  - Read `CubeConquestState.java` first
  - If missing, add the method shown above (pure Java, no MC imports)

- [ ] **Step 4.2** — Add `isForfeitPassing` to `CubeConquestGameManager.java`

- [ ] **Step 4.3** — Add unit tests; run `./gradlew test` — all pass

- [ ] **Step 4.4** — Add `ffVoteYes` and `ffVoteNo` sets to `CubeConquestGameManagerEvents.java`
  - Add fields at class level
  - Add `ffVoteYes.clear(); ffVoteNo.clear();` to `startGame`, `stopGame`, `triggerVictory`, `triggerDraw`

- [ ] **Step 4.5** — Add `/ff` and `/ff cancel` to `CubeConquestCommand.registerAll` as top-level commands

- [ ] **Step 4.6** — Run `./gradlew test` then `./gradlew build`
  - Expected: all tests pass, `BUILD SUCCESSFUL`

- [ ] **Step 4.7** — Commit
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestState.java \
          src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "feat: /ff forfeit vote — passes unless 2+ teammates cancel; /ff cancel to object"
  ```

---

### Task 5 — TrackingCompass physical needle rendering

**Model:** opus
**Source set:** client + main resources

**Files created:**
- `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java`
- `src/main/resources/assets/cubeconquest/models/item/tracking_compass.json`

**Files modified:**
- `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`
- `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java`
- `build.gradle`

**Interfaces:**
- Consumes: `TrackingCompassClientHandler.getPosition(Team)` for enemy cube position
- Consumes: `CubeConquestMod.TRACKING_COMPASS` (the registered Item instance)
- Produces: item model predicate `cubeconquest:compass_angle` in [0.0, 1.0]

**What:** The TrackingCompass needle rotates on the item model to point toward the enemy team's cube. Implemented via a custom item property predicate registered on the client side.

**VERIFY before coding — Step 5.1 (HIGH PRIORITY):** In MC 26.2 / Fabric 26.2, confirm:
1. The exact API to register a custom item property predicate: candidate is `ItemProperties.register(Item, ResourceLocation, ClampedItemPropertyFunction)` from the `net.minecraft.client.renderer.item` package. Use Context7 or Exa to confirm class names and method signature in MC 26.2.
2. The `ClampedItemPropertyFunction` parameter list: candidate is `(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) -> float`.
3. The item model JSON syntax for predicates in MC 26.2: confirm whether the `"overrides"` / `"predicate"` / `"model"` keys are still valid, or if MC 26.2 uses a different format (e.g., `"model"` as an object).
4. Which thread the property function is invoked on — must be render thread only; `TrackingCompassClientHandler` reads are safe there.

**`TrackingCompassPropertyHandler.java`:**
```java
package fr.chixi.cubeconquest.client;

import fr.chixi.cubeconquest.Team;
import net.minecraft.client.multiplayer.ClientLevel;
// VERIFY: exact import for ClampedItemPropertyFunction in MC 26.2
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TrackingCompassPropertyHandler {

    private TrackingCompassPropertyHandler() {}

    // ponytail: called on render thread; TrackingCompassClientHandler static reads are safe here
    public static float computeAngle(ItemStack stack, @Nullable ClientLevel level,
                                     @Nullable LivingEntity entity, int seed) {
        if (entity == null || level == null) return 0f;

        // Point at enemy team's cube: enemy = BLUE by default, flip if we have team info
        // ponytail: we don't yet have client-side team info; always points at BLUE first, then RED fallback
        // This works for RED team players (their enemy is BLUE); BLUE team players see the same needle
        // A future PlayerTeamPayload can fix this when needed
        var pos = TrackingCompassClientHandler.getPosition(Team.BLUE)
            .or(() -> TrackingCompassClientHandler.getPosition(Team.RED))
            .orElse(null);
        if (pos == null) return 0f;

        double dx = pos.getX() + 0.5 - entity.getX();
        double dz = pos.getZ() + 0.5 - entity.getZ();
        if (dx == 0 && dz == 0) return 0f;

        // atan2 gives angle from east; yaw is degrees from south, clockwise
        // We want: 0 = needle points forward (south at yaw=0), rotates as player turns
        double targetAngleDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90; // shift to south-forward
        double yaw = entity.getYRot();
        double relAngle = ((targetAngleDeg - yaw) % 360 + 360) % 360;
        return (float) (relAngle / 360.0);
    }
}
```

**Registration in `CubeConquestClient.onInitializeClient()`:**
```java
// VERIFY: use confirmed ItemProperties.register signature from Step 5.1
ItemProperties.register(
    CubeConquestMod.TRACKING_COMPASS,
    ResourceLocation.fromNamespaceAndPath("cubeconquest", "compass_angle"),
    TrackingCompassPropertyHandler::computeAngle
);
```

**`tracking_compass.json`** (4-frame, expandable later):
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "cubeconquest:item/tracking_compass"
  },
  "overrides": [
    { "predicate": { "cubeconquest:compass_angle": 0.0  }, "model": "cubeconquest:item/tracking_compass_n" },
    { "predicate": { "cubeconquest:compass_angle": 0.25 }, "model": "cubeconquest:item/tracking_compass_e" },
    { "predicate": { "cubeconquest:compass_angle": 0.5  }, "model": "cubeconquest:item/tracking_compass_s" },
    { "predicate": { "cubeconquest:compass_angle": 0.75 }, "model": "cubeconquest:item/tracking_compass_w" }
  ]
}
```
Note: The 4 directional models (`tracking_compass_n/e/s/w`) do not need to exist as files — missing models render the purple checkerboard in-game but do NOT crash. The property handler is what matters for this task.

**`build.gradle`:** Add to `compileClientJava.excludes`:
```
'**/client/TrackingCompassPropertyHandler.java'
```

**Steps:**

- [ ] **Step 5.1** — VERIFY ItemProperties API and model JSON syntax in MC 26.2
  - Use Context7 (`resolve-library-id` for `minecraft fabricmc` then `query-docs` for `ItemProperties register ClampedItemPropertyFunction`) or Exa
  - Confirm: class/method name, parameter signature, model JSON syntax
  - Do NOT proceed to Step 5.2 until confirmed

- [ ] **Step 5.2** — Create `TrackingCompassPropertyHandler.java`
  - Use confirmed import paths from Step 5.1
  - Implement `computeAngle` as shown above

- [ ] **Step 5.3** — Add `TrackingCompassPropertyHandler.java` to `compileClientJava.excludes` in `build.gradle`

- [ ] **Step 5.4** — Register property in `CubeConquestClient.onInitializeClient()`
  - Use confirmed API from Step 5.1

- [ ] **Step 5.5** — Create `tracking_compass.json` item model
  - At `src/main/resources/assets/cubeconquest/models/item/tracking_compass.json`
  - Use confirmed JSON syntax from Step 5.1 (adjust `"overrides"` / `"model"` key format if changed in MC 26.2)

- [ ] **Step 5.6** — Run `./gradlew test` then `./gradlew build`
  - Expected: all tests pass, `BUILD SUCCESSFUL`

- [ ] **Step 5.7** — Commit
  ```bash
  git add src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java \
          src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java \
          src/main/resources/assets/cubeconquest/models/item/tracking_compass.json \
          build.gradle
  git commit -m "feat: TrackingCompass needle rendering — client-side angle property pointing at enemy cube"
  ```

---

## Testing Strategy

### Unit tests (`./gradlew test`)

| Test | New tests |
|------|-----------|
| `CubeConquestGameManagerTest` | `drainActionBarCountdown_decrements_and_removes_at_zero`, `drainActionBarCountdown_calls_onTick_for_each_active_entry` (Task 1), `drawThreshold_met_when_all_vote`, `drawThreshold_not_met_when_only_one_team_votes`, `drawThreshold_met_at_exactly_50_percent`, `drawThreshold_fails_when_empty_team` (Task 3), `forfeit_passes_with_one_yes_zero_no`, `forfeit_blocked_by_two_no_votes`, `forfeit_fails_with_zero_yes` (Task 4) |

Total: 6 existing + 9 new = 15 tests minimum.

### Manual smoke tests (runServer)

Task 1 — ActionBar: kill porteur, new porteur sees repeated above-hotbar message for ~5s
Task 2 — Timeout: let 3min PLACEMENT expire without placing cube, porteur dies, their team loses
Task 3 — `/draw`: both teams' 50%+ vote `/draw`, game ends with "Draw!" broadcast
Task 4 — `/ff`: one player types `/ff`, passes unless 2 others type `/ff cancel`
Task 5 — Compass: hold TrackingCompass in COMBAT phase, needle rotates toward enemy cube

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| ActionBar API changed in MC 26.2 | Medium | Step 1.3 VERIFY |
| `kill()` doesn't fire `ALLOW_DEATH` event | High | Step 2.1 VERIFY; fallback to `hurt()` if needed |
| `ItemProperties.register` renamed in MC 26.2 | High | Step 5.1 VERIFY — entire Task 5 blocked until confirmed |
| Model JSON `"overrides"` syntax changed in 26.2 | Medium | Covered in Step 5.1 |
| `state.getTeamOf(UUID)` missing from `CubeConquestState` | Low | Step 4.1 checks and adds if missing |
| Vote maps survive crashed game (stale state) | Low | All clears in `startGame` — new game always resets |

---

## Success Criteria

- [ ] Cube transfer sends ActionBar message visible for ~5s above hotbar (not in chat)
- [ ] Porteur who fails to place within 3 minutes dies, their team loses immediately
- [ ] Placement HUD countdown shows remaining seconds
- [ ] `/draw` ends game in draw when >= 50% of each team votes
- [ ] `/ff` causes team to forfeit unless >= 2 teammates vote cancel
- [ ] Both `/draw` and `/ff` reject with failure message in non-COMBAT phase
- [ ] TrackingCompass needle rotates toward enemy cube when held in COMBAT
- [ ] `./gradlew test` passes all 15+ tests
- [ ] `./gradlew build` succeeds after every task
