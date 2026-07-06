# CubeConquest Iter4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix spec divergence (PREPARATION/PLACEMENT phase logic), repair all bugs identified in the Iter3 audit, add missing tests, and apply DRY/quality improvements.

**Architecture:** CubeConquestGameManagerEvents drives the state machine tick-by-tick. CubeConquestGameManager holds pure-Java helpers (unit-testable). CubeConquestState is the pure-Java state object. Changes are surgical — no new files unless adding tests.

**Tech Stack:** Java 21 / 25, Fabric API 0.153.0+26.2, Loom 1.17, Minecraft 26.2, JUnit 5 + AssertJ.

## Global Constraints

- MC 26.2 / Fabric API 0.153.0+26.2 — never assume API shapes, verify with Context7 or Exa
- YAGNI: no features beyond what's listed
- All existing 24 tests must continue to pass
- `./gradlew build` must pass after every task
- Ponytail mode: minimum diff, no speculative abstractions

---

### Task 1: Fix PREPARATION / PLACEMENT phase spec

**The spec (authoritative):**
- **PREPARATION** (200 ticks / 10s): porteur can move freely. Porteur can place cube. If porteur dies → cube transferred to another team member, game continues. PvP BLOCKED.
- **PLACEMENT** (3600 ticks / 3min after PREP ends): porteur is IMMOBILIZED immediately from the start of this phase (velocity forced to zero every tick). Porteur can still place cube while immobilized. If porteur dies during PLACEMENT (any cause) → team LOSES immediately. PvP BLOCKED.
- COMBAT: PvP enabled. Cube can be broken by enemy to trigger victory.

**Current bugs to fix in this task:**
1. PREP→PLACEMENT transition should NOT start a separate placement timer. Currently the whole game is: PREP (200 ticks) → PLACEMENT (3600 ticks). This is correct structure but the PLACEMENT phase has wrong PvP behavior (PvP is enabled in PLACEMENT currently — `onAttack` only blocks PvP during PREPARATION).
2. Porteur immobilization must start at tick 1 of PLACEMENT, not only at `tickCount >= PLACEMENT_TIMEOUT_TICKS`.
3. Porteur death during PLACEMENT must trigger team loss (currently it transfers the cube, same as PREPARATION).
4. `clearContent()` on death/disconnect must only remove the CUBE ITEM from the porteur's inventory, not wipe everything.
5. `onAllowDeath` timeout path: if `porteur.kill()` fails (creative mode), `tickCount` never resets and the game soft-locks. Fix: after `timeoutDeaths.add()`, if the porteur is in creative mode (or `kill()` won't work), call `triggerVictory` directly instead of relying on `onAllowDeath` to catch it.

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Interfaces:**
- No new interfaces. All changes internal to `CubeConquestGameManagerEvents`.

**VERIFY before coding:**
- Confirm how to check if a `ServerPlayer` is in creative mode in MC 26.2. Candidate: `player.isCreative()`. Verify via Context7 or jar inspection.
- Confirm `ItemStack.is(Item)` is available in MC 26.2 for removing a specific item. Candidate: `player.getInventory().items.removeIf(stack -> stack.is(CubeConquestMod.RED_CUBE_BLOCK.asItem()))`. Verify the `Inventory.items` field access pattern — alternative: iterate `getInventory()` slots and call `setItem(slot, ItemStack.EMPTY)` for the matching item.

- [ ] **Step 1.1: VERIFY creative mode check and inventory item removal API**

  Use Context7 (`resolve-library-id` for "minecraft fabric" then `query-docs`) or Exa (`mcp__plugin_exa_exa__web_search_exa`) to confirm:
  - `ServerPlayer.isCreative()` exists and returns boolean
  - How to remove a specific item from a player inventory without clearing everything. Confirm `Inventory.items` is accessible or find the right method.

- [ ] **Step 1.2: Fix `onAttack` — block PvP during PREPARATION AND PLACEMENT**

  Current code (line ~249):
  ```java
  if (state.getPhase() == GamePhase.PREPARATION && target instanceof Player) {
      return InteractionResult.FAIL;
  }
  ```

  Replace with:
  ```java
  GamePhase phase = state.getPhase();
  if ((phase == GamePhase.PREPARATION || phase == GamePhase.PLACEMENT)
          && target instanceof Player) {
      return InteractionResult.FAIL;
  }
  ```

- [ ] **Step 1.3: Fix porteur immobilization — starts at tick 1 of PLACEMENT, not just at timeout**

  In `handlePlacementTick`, the velocity-zero block currently guards on `tickCount >= PLACEMENT_TIMEOUT_TICKS`. Move it to run every tick from tick 1 of PLACEMENT:

  Remove the two separate blocks:
  ```java
  // Kill porteur exactly once at timeout
  if (tickCount == PLACEMENT_TIMEOUT_TICKS) { ... }
  // Defensive: zero velocity every tick >= timeout in case kill hasn't fired yet
  if (tickCount >= PLACEMENT_TIMEOUT_TICKS) { ... }
  ```

  Replace with a single block that runs every tick and handles both immobilization (always) and kill (at timeout):
  ```java
  // Immobilize porteur every tick during PLACEMENT; kill at timeout
  for (Team team : Team.values()) {
      if (saved.getCubePos(team) == null && state.getPorteur(team) != null) {
          ServerPlayer porteur = server.getPlayerList().getPlayer(state.getPorteur(team));
          if (porteur != null) {
              // ponytail: zero horizontal velocity every tick — immobilizes porteur from phase start
              porteur.setDeltaMovement(0, porteur.getDeltaMovement().y, 0);
              if (tickCount == PLACEMENT_TIMEOUT_TICKS) {
                  if (porteur.isCreative()) {
                      // Creative mode: kill() won't work; trigger loss directly
                      triggerVictory(server, team.opponent(), state);
                  } else {
                      timeoutDeaths.add(porteur.getUUID());
                      porteur.kill(porteur.level());
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 1.4: Fix `onAllowDeath` — porteur death during PLACEMENT triggers team loss**

  Current `onAllowDeath` death handler at ~line 339 (non-timeout path) transfers the cube unconditionally:
  ```java
  for (Team team : Team.values()) {
      if (dead.getUUID().equals(state.getPorteur(team))) {
          transferCubeOnDeath(server, state, team, dead);
      }
  }
  ```

  Replace with phase-conditional logic:
  ```java
  for (Team team : Team.values()) {
      if (dead.getUUID().equals(state.getPorteur(team))) {
          if (state.getPhase() == GamePhase.PLACEMENT) {
              // Porteur dies during PLACEMENT → team loses immediately
              removeCubeFromInventory(dead, team);
              triggerVictory(server, team.opponent(), state);
          } else {
              // PREPARATION: transfer to another team member
              transferCubeOnDeath(server, state, team, dead);
          }
          return true;
      }
  }
  ```

- [ ] **Step 1.5: Fix `onPlayerDisconnect` — same phase-conditional logic**

  In `onPlayerDisconnect`, replace the `transferCubeOnDeath` call:
  ```java
  for (Team team : Team.values()) {
      if (player.getUUID().equals(state.getPorteur(team))) {
          if (state.getPhase() == GamePhase.PLACEMENT) {
              removeCubeFromInventory(player, team);
              triggerVictory(server, team.opponent(), state);
          } else {
              transferCubeOnDeath(server, state, team, player);
          }
          return;
      }
  }
  ```

- [ ] **Step 1.6: Replace `clearContent()` with `removeCubeFromInventory` helper**

  Add private static helper before `onAllowDeath`:
  ```java
  // ponytail: removes only the team's cube item, not the full inventory
  private static void removeCubeFromInventory(ServerPlayer player, Team team) {
      net.minecraft.world.item.Item cubeItem = team == Team.RED
          ? CubeConquestMod.RED_CUBE_BLOCK.asItem()
          : CubeConquestMod.BLUE_CUBE_BLOCK.asItem();
      // VERIFY: confirm iteration pattern over player inventory slots in MC 26.2
      var inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
          if (inv.getItem(i).is(cubeItem)) {
              inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
              break; // only one cube at a time
          }
      }
  }
  ```

  Update `transferCubeOnDeath` to call `removeCubeFromInventory` instead of `clearContent()`:
  ```java
  private static void transferCubeOnDeath(MinecraftServer server, CubeConquestState state,
                                           Team team, ServerPlayer deadPorteur) {
      removeCubeFromInventory(deadPorteur, team);
      // ... rest unchanged
  ```

  Delete the old `deadPorteur.getInventory().clearContent();` line.

- [ ] **Step 1.7: Build**
  ```bash
  ./gradlew build
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 1.8: Run tests**
  ```bash
  ./gradlew test
  ```
  Expected: ≥24 tests pass.

- [ ] **Step 1.9: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "fix: correct PREP/PLACEMENT phase logic — immobilize on PLACEMENT start, porteur death in PLACEMENT loses team, removeCubeFromInventory instead of clearContent"
  ```

**Self-review checklist:**
- [ ] PvP blocked in PREP and PLACEMENT
- [ ] Porteur immobilized from tick 1 of PLACEMENT (not only at timeout)
- [ ] Porteur death in PLACEMENT → `triggerVictory` for opponent
- [ ] Porteur death in PREPARATION → `transferCubeOnDeath` (unchanged behavior)
- [ ] `removeCubeFromInventory` called instead of `clearContent()` in all paths
- [ ] Creative-mode porteur at timeout → `triggerVictory` directly (no kill())
- [ ] Disconnect: same phase-conditional logic

---

### Task 2: Fix cube blocks left in world after `/cubeconquest stop`

**The bug:** `stopGame()` calls `state.reset()` which clears in-memory coordinates but does NOT remove the physical cube blocks placed in the Overworld. On the next game, an enemy player breaking those old blocks triggers a false victory.

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java` (to add a helper that returns the saved BlockPos before reset)

**Interfaces:**
- `CubeConquestSavedData.getCubePos(Team)` returns `BlockPos` (already exists)
- Need: `ServerLevel.setBlock(BlockPos, BlockState, int)` to set air — confirm API in MC 26.2

**VERIFY before coding:**
- `ServerLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)` — confirm the flags parameter meaning in MC 26.2. Flag `3` = UPDATE_NEIGHBORS | BLOCK_UPDATE. Verify via Context7 or jar.
- How to get the Overworld `ServerLevel` from `MinecraftServer`: `server.getLevel(net.minecraft.world.level.Level.OVERWORLD)`. Verify.

- [ ] **Step 2.1: VERIFY ServerLevel.setBlock and Overworld access**

- [ ] **Step 2.2: Add `removeCubeBlocksFromWorld` private helper**

  Add to `CubeConquestGameManagerEvents`, before `stopGame()`:
  ```java
  // ponytail: cubes are always placed in Overworld per placement guard; no other dimension to check
  private static void removeCubeBlocksFromWorld(MinecraftServer server) {
      CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
      net.minecraft.server.level.ServerLevel overworld = server.getLevel(
          net.minecraft.world.level.Level.OVERWORLD);
      if (overworld == null) return;
      for (Team team : Team.values()) {
          net.minecraft.core.BlockPos pos = saved.getCubePos(team);
          if (pos != null) {
              net.minecraft.world.level.block.state.BlockState existing = overworld.getBlockState(pos);
              if (existing.getBlock() instanceof fr.chixi.cubeconquest.block.CubeBlock) {
                  overworld.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
              }
          }
      }
  }
  ```

- [ ] **Step 2.3: Call `removeCubeBlocksFromWorld` in `stopGame`, `triggerVictory`, `triggerDraw`**

  In `stopGame()`, add before `state.reset()`:
  ```java
  removeCubeBlocksFromWorld(server);
  ```

  In `triggerVictory()`, add before `state.reset()`:
  ```java
  removeCubeBlocksFromWorld(server);
  ```

  In `triggerDraw()`, add before `saved.getState().reset()`:
  ```java
  removeCubeBlocksFromWorld(server);
  ```

- [ ] **Step 2.4: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥24 tests pass.

- [ ] **Step 2.5: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "fix: remove physical cube blocks from world on game end/stop"
  ```

**Self-review checklist:**
- [ ] Called in all 3 termination paths (stop, victory, draw)
- [ ] Only removes if block is a `CubeBlock` (not if block was replaced by the world)
- [ ] Overworld null guard present

---

### Task 3: Fix INT3_CODEC bounds check and double-placement guard

Two small fixes in one commit.

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Fix A — INT3_CODEC:** The decoder `l -> new int[]{l.get(0), l.get(1), l.get(2)}` throws `IndexOutOfBoundsException` if the saved list has fewer than 3 elements (corrupted NBT). Wrap in a guard:

```java
private static final Codec<int[]> INT3_CODEC = Codec.INT.listOf().xmap(
    l -> l.size() >= 3
        ? new int[]{l.get(0), l.get(1), l.get(2)}
        : new int[]{0, 0, 0}, // ponytail: corrupted save; default to origin rather than crash
    a -> List.of(a[0], a[1], a[2])
);
```

**Fix B — double placement guard:** In `onUseBlock`, only register the cube placement if the position is not already set:

In the `onUseBlock` method, after the `porteurId` check, add before `saved.setCubePos(cubeTeam, placedPos)`:
```java
// ponytail: first placement wins; re-placement by porteur would overwrite silently
if (saved.getCubePos(cubeTeam) != null) {
    return InteractionResult.PASS; // already placed
}
```

- [ ] **Step 3.1: Apply Fix A to `CubeConquestSavedData.java`**

  Read the file, replace `INT3_CODEC` as shown above.

- [ ] **Step 3.2: Apply Fix B to `CubeConquestGameManagerEvents.java`**

  In `onUseBlock`, after the line `if (porteurId == null || !porteurId.equals(player.getUUID()))`,
  add the double-placement guard shown above.

- [ ] **Step 3.3: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥24 tests pass.

- [ ] **Step 3.4: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "fix: INT3_CODEC bounds check on corrupt save; block double cube placement"
  ```

---

### Task 4: DRY refactors — resetTransientState, cubeItem helper, clearClientCubePositions dedup

Three mechanical extractions. No behavior change.

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/Team.java` (add `cubeBlock()` helper)
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java` (read to confirm field names)

**VERIFY before coding:** Read `CubeConquestMod.java` to confirm the field names `RED_CUBE_BLOCK` and `BLUE_CUBE_BLOCK` and their type (should be a `Block` or similar with `.asItem()`).

- [ ] **Step 4.1: Read `CubeConquestMod.java`** to confirm `RED_CUBE_BLOCK` / `BLUE_CUBE_BLOCK` types.

- [ ] **Step 4.2: Add `Team.cubeBlock()` helper**

  In `src/main/java/fr/chixi/cubeconquest/Team.java`, add:
  ```java
  // ponytail: avoids duplicating the RED/BLUE ternary in two places in Events
  public net.minecraft.world.level.block.Block cubeBlock() {
      return this == RED
          ? fr.chixi.cubeconquest.CubeConquestMod.RED_CUBE_BLOCK
          : fr.chixi.cubeconquest.CubeConquestMod.BLUE_CUBE_BLOCK;
  }
  ```

  Note: This adds a MC dependency to `Team.java`. If Team is used in unit tests without MC, this will break compilation. Check `TeamTest.java` first — if it only tests `opponent()` and `displayName()`, add the method but mark it `// MC-dependent` and ensure the build.gradle excludes don't exclude Team.java from any compilation target. If adding MC dependency to Team.java is problematic, skip this step and instead extract a private static helper in `CubeConquestGameManagerEvents`:

  ```java
  private static net.minecraft.world.level.block.Block cubeBlockFor(Team team) {
      return team == Team.RED ? CubeConquestMod.RED_CUBE_BLOCK : CubeConquestMod.BLUE_CUBE_BLOCK;
  }
  ```

  Use this helper in both `startGame` (line ~201) and `transferCubeOnDeath` (line ~366):
  - `new ItemStack(team == Team.RED ? CubeConquestMod.RED_CUBE_BLOCK.asItem() : CubeConquestMod.BLUE_CUBE_BLOCK.asItem())` → `new ItemStack(cubeBlockFor(team).asItem())`

  Also update `removeCubeFromInventory` (Task 1) to use `cubeBlockFor(team).asItem()`.

- [ ] **Step 4.3: Extract `resetTransientState` helper**

  Add private static method to `CubeConquestGameManagerEvents`:
  ```java
  // ponytail: called from stopGame, triggerVictory, triggerDraw — single source of truth
  private static void resetTransientState() {
      tickCount = 0;
      actionBarCountdown.clear();
      timeoutDeaths.clear();
      drawVoters.clear();
      ffVoteYes.clear();
      ffVoteNo.clear();
  }
  ```

  Replace the 6-line reset block in `stopGame`, `triggerVictory`, and `triggerDraw` with a single call `resetTransientState()`.

- [ ] **Step 4.4: Fix `clearClientCubePositions` duplicate countdown packet**

  Current code sends `resetCountdown` inside the team loop, so every player receives it twice (once per team). Move the countdown send outside the team loop:

  ```java
  private static void clearClientCubePositions(MinecraftServer server) {
      PlacementCountdownPayload resetCountdown = new PlacementCountdownPayload(-1);
      for (Team team : Team.values()) {
          CubePositionPayload posPayload = new CubePositionPayload(team, Optional.empty());
          for (ServerPlayer player : server.getPlayerList().getPlayers()) {
              ServerPlayNetworking.send(player, posPayload);
          }
      }
      // ponytail: send once after both position clears — was sent inside team loop (×2 per player)
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
          ServerPlayNetworking.send(player, resetCountdown);
      }
  }
  ```

- [ ] **Step 4.5: Remove "// Fix N" archaeological comments**

  Find and remove comments like `// Fix 1:`, `// Fix 2:`, `// Fix 5:` in `CubeConquestGameManagerEvents.java`. These were tracking notes from development and are now noise. Replace with intent-describing comments if the code isn't self-explanatory.

- [ ] **Step 4.6: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, ≥24 tests pass.

- [ ] **Step 4.7: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/main/java/fr/chixi/cubeconquest/Team.java
  git commit -m "refactor: extract resetTransientState, cubeBlockFor helper, fix clearClientCubePositions duplicate packet"
  ```

---

### Task 5: Add missing unit tests (CubeConquestState + GameManager edge cases)

**Files:**
- Create: `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**New tests in `CubeConquestStateTest.java`:**

```java
package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CubeConquestStateTest {

    @Test
    void addPlayer_adds_to_correct_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        assertThat(state.getPlayers(Team.RED)).contains(id);
        assertThat(state.getPlayers(Team.BLUE)).doesNotContain(id);
    }

    @Test
    void addPlayer_moves_player_from_other_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        state.addPlayer(Team.BLUE, id);
        assertThat(state.getPlayers(Team.RED)).doesNotContain(id);
        assertThat(state.getPlayers(Team.BLUE)).contains(id);
    }

    @Test
    void getTeamOf_returns_correct_team() {
        CubeConquestState state = new CubeConquestState();
        UUID red = UUID.randomUUID(), blue = UUID.randomUUID();
        state.addPlayer(Team.RED, red);
        state.addPlayer(Team.BLUE, blue);
        assertThat(state.getTeamOf(red)).contains(Team.RED);
        assertThat(state.getTeamOf(blue)).contains(Team.BLUE);
        assertThat(state.getTeamOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void reset_clears_all_fields() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        state.setPorteur(Team.RED, id);
        state.setCubePos(Team.RED, new int[]{1, 2, 3});
        state.setPhase(GamePhase.COMBAT);
        state.reset();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
        assertThat(state.getPlayers(Team.RED)).isEmpty();
        assertThat(state.getPorteur(Team.RED)).isNull();
        assertThat(state.getCubePos(Team.RED)).isNull();
    }

    @Test
    void setCubePos_defensive_copy() {
        CubeConquestState state = new CubeConquestState();
        int[] arr = {10, 20, 30};
        state.setCubePos(Team.RED, arr);
        arr[0] = 999; // mutate original
        assertThat(state.getCubePos(Team.RED)[0]).isEqualTo(10); // stored copy unchanged
    }

    @Test
    void getCubePos_returns_defensive_copy() {
        CubeConquestState state = new CubeConquestState();
        state.setCubePos(Team.RED, new int[]{10, 20, 30});
        int[] got = state.getCubePos(Team.RED);
        got[0] = 999; // mutate returned array
        assertThat(state.getCubePos(Team.RED)[0]).isEqualTo(10); // stored copy unchanged
    }
}
```

**New tests to add to `CubeConquestGameManagerTest.java`:**

```java
@Test
void pickReplacementOpt_returns_empty_when_only_old_uuid_in_list() {
    UUID old = UUID.randomUUID();
    assertThat(CubeConquestGameManager.pickReplacementOpt(old, List.of(old))).isEmpty();
}

@Test
void drawThreshold_not_met_when_voters_empty() {
    UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isDrawThresholdMet(
        Set.of(), Set.of(r1), Set.of(b1))).isFalse();
}

@Test
void forfeit_passes_with_one_yes_and_one_no() {
    // One no vote is not enough to cancel (threshold is < 2)
    UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
    assertThat(CubeConquestGameManager.isForfeitPassing(
        Set.of(p1), Set.of(p2), Set.of(p1, p2))).isTrue();
}

@Test
void drainActionBarCountdown_callback_receives_pre_decrement_value() {
    Map<UUID, Integer> map = new HashMap<>();
    UUID id = UUID.randomUUID();
    map.put(id, 1);
    List<Integer> seen = new ArrayList<>();
    CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> seen.add(tick));
    // Entry at 1 is removed; callback was called with 1 (pre-decrement)
    assertThat(seen).containsExactly(1);
    assertThat(map).doesNotContainKey(id); // removed because value hit 0 after decrement
}
```

- [ ] **Step 5.1: Create `CubeConquestStateTest.java`** with all 6 tests above.

- [ ] **Step 5.2: Add 4 new tests to `CubeConquestGameManagerTest.java`** as shown above.

- [ ] **Step 5.3: Run tests — confirm new tests fail first (RED), then pass after check**

  ```bash
  ./gradlew test
  ```
  Expected: all tests pass (the new tests should pass immediately since they test existing behavior).

- [ ] **Step 5.4: Build**
  ```bash
  ./gradlew build
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.5: Commit**
  ```bash
  git add src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java \
          src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
  git commit -m "test: add CubeConquestState unit tests and GameManager edge case coverage"
  ```

**Self-review checklist:**
- [ ] 6 new tests in `CubeConquestStateTest`
- [ ] 4 new tests in `CubeConquestGameManagerTest`
- [ ] All tests pass
- [ ] No production code changed in this task

---

### Task 6: Title screen on PREPARATION→PLACEMENT transition

Add `sendTitle` call when transitioning from PREPARATION to PLACEMENT, matching the pattern used in `triggerVictory` / `triggerDraw`.

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

- [ ] **Step 6.1: In `handlePreparationTick`, add `sendTitle` call at the PREP→PLACEMENT transition**

  The transition code (inside `if (tickCount >= PREPARATION_TICKS)`):
  ```java
  tickCount = 0;
  state.setPhase(GamePhase.PLACEMENT);
  broadcast(server, Component.literal("Place your Cube!").withStyle(ChatFormatting.YELLOW));
  ```

  Add after `broadcast(...)`:
  ```java
  sendTitle(server,
      Component.literal("Place your Cube!").withStyle(ChatFormatting.YELLOW),
      Component.literal("You have 3 minutes").withStyle(ChatFormatting.GRAY));
  ```

- [ ] **Step 6.2: Build and test**
  ```bash
  ./gradlew build && ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6.3: Commit**
  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "feat: show title screen on PREPARATION→PLACEMENT transition"
  ```

---
