# CubeConquest Iter5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix cube placement guard during PREPARATION, reconnect sync, HUD enemy-only display, forfeit majority threshold, tickCount overflow / ThreadLocalRandom / spectator broadcast, and add matching tests.

**Architecture:** `CubeConquestGameManagerEvents` drives all server-side event handling. `CubeConquestGameManager` holds pure-Java helpers (unit-testable, no MC imports). `CubeConquestHud` is the client-side overlay. `CubeConquestCommand` owns the `/ff` command broadcast. All changes are surgical — no new files except tests.

**Tech Stack:** Java 21 / 25, Fabric API 0.153.0+26.2, Loom 1.17, Minecraft 26.2, JUnit 5 + AssertJ.

## Global Constraints

- MC 26.2 / Fabric API 0.153.0+26.2 — never assume API shapes from pre-26.2 tutorials
- YAGNI: no features beyond what's listed
- Ponytail mode: minimum diff, `// ponytail:` on simplifications
- All ≥28 existing tests must continue to pass
- `./gradlew build` must pass after every task

---

### Task 1: Block cube placement during PREPARATION; fix non-porteur placement

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Interfaces:**
- No new interfaces. All changes are within `onUseBlock` in `CubeConquestGameManagerEvents`.

**Context:**
The current `onUseBlock` phase guard at line 317 is:
```java
if (state.getPhase() != GamePhase.PLACEMENT) return InteractionResult.PASS;
```
This passes through (allows physical placement) during PREPARATION without tracking. A physical cube block placed by any player during PREPARATION can later trigger `onBlockBreak` → `triggerVictory` falsely.

The non-porteur guard at line 328–329 is:
```java
if (porteurId == null || !porteurId.equals(player.getUUID())) {
    return InteractionResult.PASS; // not the porteur, let it place as decoration
}
```
`InteractionResult.PASS` allows the block to be physically placed in the world as an untracked decoration. Any player who later breaks it triggers `onBlockBreak`, which checks `getBlockState` — if that breaks during COMBAT the game falsely ends.

- [ ] **Step 1.1: Replace the phase guard in `onUseBlock`**

  Current (line 317):
  ```java
  if (state.getPhase() != GamePhase.PLACEMENT) return InteractionResult.PASS;
  ```

  Replace with:
  ```java
  GamePhase phase = state.getPhase();
  if (phase == GamePhase.PREPARATION) {
      player.sendSystemMessage(
          Component.literal("You cannot place the cube yet! Wait for PLACEMENT phase.")
              .withStyle(ChatFormatting.RED));
      return InteractionResult.FAIL;
  }
  if (phase != GamePhase.PLACEMENT) return InteractionResult.PASS;
  ```

- [ ] **Step 1.2: Fix the non-porteur guard in `onUseBlock`**

  Current (lines 328–329):
  ```java
  if (porteurId == null || !porteurId.equals(player.getUUID())) {
      return InteractionResult.PASS; // not the porteur, let it place as decoration
  }
  ```

  Replace with:
  ```java
  if (porteurId == null || !porteurId.equals(player.getUUID())) {
      // ponytail: prevent rogue cube block from triggering break-victory
      return InteractionResult.FAIL;
  }
  ```

- [ ] **Step 1.3: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥28 tests pass.

- [ ] **Step 1.4: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "fix: block cube placement during PREPARATION; FAIL on non-porteur cube place"
  ```

**Self-review checklist:**
- [ ] PREPARATION phase → `InteractionResult.FAIL` with message (not PASS)
- [ ] Non-porteur placing cube block → `InteractionResult.FAIL` (not PASS)
- [ ] IDLE phase still returns `InteractionResult.PASS` (the `phase != PLACEMENT` guard handles it)
- [ ] Existing PLACEMENT + porteur path unchanged

---

### Task 2: startGame only clears team members; reconnect syncs cube positions

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Interfaces:**
- `CubeConquestSavedData.getCubePos(Team)` — already exists
- `CubePositionPayload(Team, Optional<BlockPos>)` — already exists
- `ServerPlayNetworking.send(ServerPlayer, CustomPacketPayload)` — already used

**Context:**
`startGame` at lines 197–199 calls `clearContent()` on ALL online players, including spectators and admins not on a team. This wipes inventory from uninvolved players.

The JOIN handler at lines 63–71 sends only `PlayerTeamPayload` to reconnecting players. A player who reconnects during COMBAT never receives the current cube positions, so their compass points at nothing.

- [ ] **Step 2.1: Fix `startGame` inventory clear — team members only**

  Current (lines 197–199):
  ```java
  for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      player.getInventory().clearContent();
  }
  ```

  Replace with:
  ```java
  for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      if (state.getTeamOf(player.getUUID()).isPresent()) {
          player.getInventory().clearContent();
      }
  }
  ```

- [ ] **Step 2.2: Fix JOIN handler — also sync cube positions during COMBAT**

  Current JOIN handler (lines 63–71):
  ```java
  ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
      CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
      if (state.getPhase() != GamePhase.IDLE) {
          state.getTeamOf(player.getUUID()).ifPresent(team ->
              ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
          );
      }
  });
  ```

  Replace with:
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
      }
  });
  ```

- [ ] **Step 2.3: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥28 tests pass.

- [ ] **Step 2.4: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "fix: startGame clears only team members; reconnect during COMBAT syncs cube positions"
  ```

**Self-review checklist:**
- [ ] `clearContent()` called only for players whose UUID is in a team
- [ ] JOIN handler sends `CubePositionPayload` for both teams only when phase is COMBAT
- [ ] JOIN handler: non-team players still get nothing (no change to that path)
- [ ] `Optional.ofNullable` used correctly (cube may not be placed yet at join time)

---

### Task 3: HUD shows only enemy cube to each team

**Files:**
- Modify: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java`

**Interfaces:**
- `TrackingCompassClientHandler.getClientTeam()` — returns `Team` or `null` (already exists)
- `TrackingCompassClientHandler.getPosition(Team)` — returns `Optional<BlockPos>` (already exists)

**Context:**
`CubeConquestHud.render` currently shows both RED and BLUE cube positions to every player unconditionally. A RED team player should only see the BLUE cube (their enemy's), not their own cube. Showing own-cube coordinates leaks tactical info the compass already provides.

Current render method shows:
- "Red cube: x,y,z" to everyone
- "Blue cube: x,y,z" to everyone

Required behavior:
- RED team player sees only "Enemy Cube: x,y,z" (the BLUE cube)
- BLUE team player sees only "Enemy Cube: x,y,z" (the RED cube)
- No team assigned (spectator, null clientTeam) → hide all cube positions

- [ ] **Step 3.1: Rewrite cube-position rendering in `CubeConquestHud.render`**

  Replace the two team-specific blocks:
  ```java
  // Red cube position
  TrackingCompassClientHandler.getPosition(Team.RED).ifPresent(pos ->
      graphics.drawString(mc.font,
          "Red cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
          x, y, 0xFF5555, true)
  );

  // Blue cube position
  TrackingCompassClientHandler.getPosition(Team.BLUE).ifPresent(pos ->
      graphics.drawString(mc.font,
          "Blue cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
          x, y + 10, 0x5555FF, true)
  );
  ```

  Replace with:
  ```java
  // ponytail: show only enemy cube — own cube position is visible via compass heading
  Team clientTeam = TrackingCompassClientHandler.getClientTeam();
  if (clientTeam != null) {
      Team enemyTeam = clientTeam.opponent();
      TrackingCompassClientHandler.getPosition(enemyTeam).ifPresent(pos ->
          graphics.drawString(mc.font,
              "Enemy Cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
              x, y, enemyTeam == Team.RED ? 0xFF5555 : 0x5555FF, true)
      );
  }
  ```

  Note: The countdown block (lines 39–44) is unchanged — it stays at `y + 10` (was `y + 20`). Adjust `y` offset to `y + 10` for the countdown since there is now only one cube line instead of two.

  Full updated render method:
  ```java
  private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;

      int y = 5;
      int x = mc.getWindow().getGuiScaledWidth() / 2 - 50;

      // ponytail: show only enemy cube — own cube position is visible via compass heading
      Team clientTeam = TrackingCompassClientHandler.getClientTeam();
      if (clientTeam != null) {
          Team enemyTeam = clientTeam.opponent();
          TrackingCompassClientHandler.getPosition(enemyTeam).ifPresent(pos ->
              graphics.drawString(mc.font,
                  "Enemy Cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                  x, y, enemyTeam == Team.RED ? 0xFF5555 : 0x5555FF, true)
          );
      }

      // Placement countdown
      int countdown = TrackingCompassClientHandler.getPlacementTicksRemaining();
      if (countdown >= 0) {
          graphics.drawString(mc.font,
              "Place cube: " + (countdown / 20) + "s",
              x, y + 10, 0xFFFF55, true);
      }
  }
  ```

- [ ] **Step 3.2: Build**
  ```bash
  ./gradlew build
  ```
  Expected: `BUILD SUCCESSFUL`. (HUD is client-only, no JUnit tests for it.)

- [ ] **Step 3.3: Commit**
  ```bash
  git add src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java
  git commit -m "fix: HUD shows only enemy cube position; spectators see nothing"
  ```

**Self-review checklist:**
- [ ] `clientTeam` null check present — spectators see no cube positions
- [ ] `clientTeam.opponent()` used to derive enemy team
- [ ] Color matches enemy team (RED enemy → red color, BLUE enemy → blue color)
- [ ] Countdown offset updated from `y + 20` to `y + 10` (one fewer cube line)
- [ ] Old "Red cube:" / "Blue cube:" labels removed

---

### Task 4: Fix forfeit threshold — require strict majority

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- `isForfeitPassing(Set<UUID>, Set<UUID>, Set<UUID>)` — signature unchanged, logic changes

**Context:**
Current `isForfeitPassing` logic (line 60):
```java
return yesVotes > 0 && noVotes < 2;
```
One player can solo-forfeit a 10-player team (1 yes, 0 no → passes). The fix requires `yesVotes * 2 > teamOnline.size()` (strict majority — >50%).

The `/ff` command broadcast in `CubeConquestCommand` (line 188–191) currently says:
```
"<name> voted to forfeit for team <team> — <noVotes> objection(s) (2 needed to cancel)"
```
This references the old "2 objections" rule. After the fix, the message should say how many yes votes are needed.

The existing test `forfeit_passes_with_one_yes_and_one_no` (line 123–127) tests that `isForfeitPassing(Set.of(p1), Set.of(p2), Set.of(p1, p2))` returns `true`. With strict majority on a 2-player team, 1 yes = 50% which is NOT >50%, so this returns `false`. The test must be renamed and updated.

- [ ] **Step 4.1: Replace `isForfeitPassing` in `CubeConquestGameManager.java`**

  Current (lines 57–61):
  ```java
  static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> ffNo, Set<UUID> teamOnline) {
      long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
      long noVotes  = teamOnline.stream().filter(ffNo::contains).count();
      return yesVotes > 0 && noVotes < 2;
  }
  ```

  Replace with:
  ```java
  static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> ffNo, Set<UUID> teamOnline) {
      if (teamOnline.isEmpty()) return false;
      long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
      // ponytail: strict majority required — single player cannot solo forfeit the team
      return yesVotes * 2 > teamOnline.size();
  }
  ```

  Remove the unused `ffNo` parameter — wait, `ffNo` is still in the signature because `CubeConquestCommand` passes it. Keep the parameter, just don't use it in the body. Add `@SuppressWarnings` if the IDE warns, or leave as-is; the parameter stays for API compatibility with the command.

  Actually: `ffNo` is no longer used in the body. Keep the parameter to avoid changing the call site signature. The compiler will warn about an unused parameter but it won't break the build.

- [ ] **Step 4.2: Update `/ff` broadcast message in `CubeConquestCommand.java`**

  Current (lines 185–191):
  ```java
  long noVotes = teamOnline.stream()
      .filter(CubeConquestGameManagerEvents.ffVoteNo::contains).count();

  Component msg = Component.literal(
      voter.getName().getString() + " voted to forfeit for team "
      + voterTeam.name() + " — " + noVotes + " objection(s) (2 needed to cancel)");
  ```

  Replace with:
  ```java
  long yesVotes = teamOnline.stream()
      .filter(CubeConquestGameManagerEvents.ffVoteYes::contains).count();
  long needed = (teamOnline.size() / 2) + 1;

  Component msg = Component.literal(
      voter.getName().getString() + " voted to forfeit for team "
      + voterTeam.name() + " — " + yesVotes + "/" + teamOnline.size()
      + " voted yes (" + needed + " needed)");
  ```

- [ ] **Step 4.3: Update tests in `CubeConquestGameManagerTest.java`**

  Rename `forfeit_passes_with_one_yes_and_one_no` → `forfeit_passes_with_majority_yes` and update:

  Old test (lines 123–127):
  ```java
  @Test
  void forfeit_passes_with_one_yes_and_one_no() {
      UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(p1), Set.of(p2), Set.of(p1, p2))).isTrue();
  }
  ```

  Replace with:
  ```java
  @Test
  void forfeit_passes_with_majority_yes() {
      UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(p1, p2), Set.of(p3), Set.of(p1, p2, p3))).isTrue();
  }
  ```

  Also update `forfeit_passes_with_one_yes_zero_no` — with strict majority, 1/1 IS a majority (1*2 > 1 → 2 > 1 → true). That test remains valid, no change needed.

  Also update `forfeit_blocked_by_two_no_votes` — the logic no longer uses `noVotes`. This test will now fail because `Set.of(p2, p3)` as `ffNo` is ignored, and 1 yes out of 3 online = 1*2=2 which is NOT > 3. Test still passes (result is `false`) but for a different reason. The test name is misleading but the assertion is still correct. Leave it.

- [ ] **Step 4.4: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥28 tests pass.

- [ ] **Step 4.5: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java \
          src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java \
          src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "fix: forfeit requires strict majority (>50%) instead of 1-yes-and-fewer-than-2-no"
  ```

**Self-review checklist:**
- [ ] `isForfeitPassing` uses `yesVotes * 2 > teamOnline.size()`
- [ ] Empty `teamOnline` returns `false`
- [ ] `ffNo` parameter kept for API compatibility (unused in body is acceptable)
- [ ] `/ff` broadcast says "X/Y voted yes (Z needed)"
- [ ] `forfeit_passes_with_one_yes_and_one_no` test renamed to `forfeit_passes_with_majority_yes` with 2/3 assertion
- [ ] `forfeit_passes_with_one_yes_zero_no` still passes (1/1 → 1*2 > 1 = true)

---

### Task 5: Fix tickCount overflow; use ThreadLocalRandom; filter spectator broadcast

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`

**Interfaces:**
- No interface changes. All changes are internal implementations.

**Context:**

**Fix 5A — tickCount overflow:** `handleCombatTick` (line 150) increments `tickCount` without bound. After 2^31-1 ticks (~25 days), it wraps to `Integer.MIN_VALUE`. The `% 20 == 0` check still works (negative numbers modulo 20 can be negative in Java, but `Integer.MIN_VALUE % 20 == 0` so it fires once per wrap). Adding a wrap guard is safer and documents intent.

**Fix 5B — ThreadLocalRandom:** `pickRandom` uses `new Random()` per call (line 19). `new Random()` is expensive and creates a new seed per invocation. `ThreadLocalRandom.current()` is both faster and thread-safe for this usage pattern.

**Fix 5C — spectator broadcast in `handleCombatTick`:** Lines 155–157 send `CubePositionPayload` to ALL online players, including spectators and admins not on a team. Spectators have no compass, so the packets are wasted. Filter to team members only.

- [ ] **Step 5.1: Fix tickCount overflow in `handleCombatTick`**

  Current (lines 150–151):
  ```java
  private static void handleCombatTick(MinecraftServer server, CubeConquestSavedData saved) {
      tickCount++;
  ```

  Replace with:
  ```java
  private static void handleCombatTick(MinecraftServer server, CubeConquestSavedData saved) {
      tickCount++;
      if (tickCount < 0) tickCount = 1; // ponytail: wrap on overflow — happens after ~25 days
  ```

- [ ] **Step 5.2: Replace `new Random()` with `ThreadLocalRandom` in `CubeConquestGameManager.java`**

  Add import at the top of `CubeConquestGameManager.java`:
  ```java
  import java.util.concurrent.ThreadLocalRandom;
  ```

  Current `pickRandom` (lines 17–20):
  ```java
  static UUID pickRandom(List<UUID> players) {
      if (players.isEmpty()) throw new IllegalArgumentException("Cannot pick from empty list");
      return players.get(new Random().nextInt(players.size()));
  }
  ```

  Replace with:
  ```java
  static UUID pickRandom(List<UUID> players) {
      if (players.isEmpty()) throw new IllegalArgumentException("Cannot pick from empty list");
      return players.get(ThreadLocalRandom.current().nextInt(players.size()));
  }
  ```

  Also remove the `Random` import from the existing `import java.util.*` — it's covered by the wildcard so no import change needed.

- [ ] **Step 5.3: Filter cube position broadcast to team members only in `handleCombatTick`**

  Current (lines 152–158):
  ```java
  if (tickCount % 20 == 0) {
      for (Team team : Team.values()) {
          CubePositionPayload payload = new CubePositionPayload(team,
              Optional.ofNullable(saved.getCubePos(team)));
          for (ServerPlayer player : server.getPlayerList().getPlayers()) {
              ServerPlayNetworking.send(player, payload);
          }
      }
  }
  ```

  Replace with:
  ```java
  if (tickCount % 20 == 0) {
      CubeConquestState state = saved.getState();
      for (Team team : Team.values()) {
          CubePositionPayload payload = new CubePositionPayload(team,
              Optional.ofNullable(saved.getCubePos(team)));
          for (ServerPlayer player : server.getPlayerList().getPlayers()) {
              if (state.getTeamOf(player.getUUID()).isPresent()) {
                  ServerPlayNetworking.send(player, payload);
              }
          }
      }
  }
  ```

- [ ] **Step 5.4: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥28 tests pass.

- [ ] **Step 5.5: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java
  git commit -m "fix: tickCount overflow guard; ThreadLocalRandom in pickRandom; filter combat broadcast to team members"
  ```

**Self-review checklist:**
- [ ] `if (tickCount < 0) tickCount = 1;` added after `tickCount++` in `handleCombatTick`
- [ ] `ThreadLocalRandom.current()` used in `pickRandom`; `import java.util.concurrent.ThreadLocalRandom;` added
- [ ] Cube position packets sent only to players with a team (`getTeamOf(...).isPresent()`)
- [ ] `state` retrieved once outside the team loop (not per-player)

---

### Task 6: Add missing tests

**Files:**
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- Tests call `CubeConquestGameManager.isForfeitPassing` — which now uses strict majority (Task 4 already done before this task).

**Context:**
After Task 4 changes `isForfeitPassing`, the test suite needs coverage for:
1. Majority threshold — 1/3 fails, 2/3 passes
2. Offline voters ignored — only `teamOnline` members count
3. Empty team → always false

The existing `forfeit_passes_with_one_yes_and_one_no` was already renamed in Task 4 to `forfeit_passes_with_majority_yes`. These new tests add explicit coverage for the new behavior.

- [ ] **Step 6.1: Add three new tests to `CubeConquestGameManagerTest.java`**

  Add after the existing forfeit tests:

  ```java
  @Test
  void forfeit_requires_majority_of_online_team() {
      UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
      // 1 yes out of 3 is not majority
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(p1), Set.of(), Set.of(p1, p2, p3))).isFalse();
      // 2 yes out of 3 is majority
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(p1, p2), Set.of(), Set.of(p1, p2, p3))).isTrue();
  }

  @Test
  void forfeit_ignores_offline_voter() {
      UUID online = UUID.randomUUID();
      UUID offline = UUID.randomUUID();
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(offline), Set.of(), Set.of(online))).isFalse();
  }

  @Test
  void forfeit_empty_team_returns_false() {
      assertThat(CubeConquestGameManager.isForfeitPassing(
          Set.of(UUID.randomUUID()), Set.of(), Set.of())).isFalse();
  }
  ```

- [ ] **Step 6.2: Run tests**
  ```bash
  ./gradlew test
  ```
  Expected: all tests pass (≥31 total after adding 3 new tests).

- [ ] **Step 6.3: Build**
  ```bash
  ./gradlew build
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6.4: Commit**
  ```bash
  git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "test: add forfeit majority threshold, offline voter, empty team coverage"
  ```

**Self-review checklist:**
- [ ] 3 new tests added
- [ ] `forfeit_requires_majority_of_online_team` covers both 1/3 (false) and 2/3 (true)
- [ ] `forfeit_ignores_offline_voter` confirms offline UUID in `ffYes` is not counted
- [ ] `forfeit_empty_team_returns_false` confirms the empty-guard added in Task 4
- [ ] No production code changed in this task
- [ ] Total tests ≥31

---
