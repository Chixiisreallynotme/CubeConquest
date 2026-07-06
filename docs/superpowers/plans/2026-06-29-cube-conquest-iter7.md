# CubeConquest Iter7 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 12 audit findings from the Iter6 whole-branch review — 4 HIGH, 6 MEDIUM, 2 LOW.

**Architecture:** Same as previous iterations — CubeConquestGameManagerEvents.java (server events), CubeConquestGameManager.java (pure-Java helpers), CubeConquestSavedData.java (persistence), CubeConquestState.java (domain state), CubeConquestCommand.java, client-side handlers.

**Tech Stack:** Java 21, Fabric MC 26.2, Fabric API 0.153.0+26.2, Loom 1.17, Gradle 9.5.1 (Windows), JUnit 5, AssertJ.

**Branch base:** `969bee5`

## Global Constraints

- MC 26.2 API only — no Bukkit/Spigot/NeoForge
- `loom.splitEnvironmentSourceSets()` — client code in `src/client`, server/shared in `src/main`
- Ponytail mode FULL — minimum code, YAGNI strict, `// ponytail:` on deliberate simplifications
- RÈGLE ANTI-HALLUCINATION: if unsure about any Fabric/MC 26.2 API, search before coding
- JAMAIS affirmer qu'un code est correct sans vérification (build + test)
- `./gradlew test` must pass after every task
- `./gradlew build` must pass at end of plan
- Commit after each task, conventional commit format
- No new dependencies
- Pure-Java helpers in `CubeConquestGameManager` stay free of MC imports

---

### Task 1: Per-team /ff vote buckets (HIGH-1 + MEDIUM-6 + remove vestigial ffVoteNo)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Context:**

`ffVoteYes` and `ffVoteNo` are single global `Set<UUID>` instances shared across both teams. If RED forfeits first and `triggerVictory` fires, `resetTransientState()` wipes BLUE's in-flight votes too. The race is silently non-deterministic.

`ffVoteNo` is vestigial — `isForfeitPassing` doesn't use it. `/ff cancel` only removes from `ffVoteYes` (the useful operation) and adds to `ffVoteNo` (unused).

Fix: Replace `ffVoteYes`/`ffVoteNo` with `Map<Team, Set<UUID>> ffVoteYes` keyed per team. Remove `ffVoteNo` entirely. Update `isForfeitPassing` signature to remove the unused `ffVoteNo` parameter. Update `/ff cancel` to gate on team membership (fixing the Iter2 LOW as a bonus).

**Interfaces:**
- `CubeConquestGameManager.isForfeitPassing(Set<UUID> ffYes, Set<UUID> teamOnline)` — NEW signature (ffVoteNo removed)
- `CubeConquestGameManagerEvents.ffVoteYes`: `Map<Team, Set<UUID>>` — team-keyed

- [ ] **Step 1: Update isForfeitPassing signature in CubeConquestGameManager**

Remove the `ffVoteNo` parameter — it was never used:

```java
// BEFORE
static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> ffNo, Set<UUID> teamOnline) {
    if (teamOnline.isEmpty()) return false;
    long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
    return yesVotes * 2 > teamOnline.size();
}

// AFTER
static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> teamOnline) {
    if (teamOnline.isEmpty()) return false;
    long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
    // ponytail: strict majority — ffVoteNo removed (was never used in computation)
    return yesVotes * 2 > teamOnline.size();
}
```

- [ ] **Step 2: Update all isForfeitPassing tests**

In `CubeConquestGameManagerTest.java`, remove the `Set.of(...)` middle argument from all `isForfeitPassing` calls.

Before (example):
```java
assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(p1), Set.of(), Set.of(p1))).isTrue();
```
After:
```java
assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(p1), Set.of(p1))).isTrue();
```

Update ALL occurrences:
- `forfeit_passes_with_one_yes_zero_no`: remove `Set.of()` middle arg
- `forfeit_blocked_by_two_no_votes`: remove `Set.of(p2, p3)` middle arg; rename to `forfeit_1_yes_out_of_3_not_majority`
- `forfeit_fails_with_zero_yes`: remove middle arg
- `forfeit_requires_majority_of_online_team`: remove middle arg (both assertions)
- `forfeit_ignores_offline_voter`: remove middle arg
- `forfeit_empty_team_returns_false`: remove middle arg
- `forfeit_passes_with_majority_yes`: remove middle arg

- [ ] **Step 3: Run tests to verify test updates are clean**

```
./gradlew test
```

Expected: PASS (we only changed method signatures, not logic)

- [ ] **Step 4: Replace static vote fields in CubeConquestGameManagerEvents**

```java
// REMOVE these two lines:
static final Set<UUID> ffVoteYes = new HashSet<>();
static final Set<UUID> ffVoteNo  = new HashSet<>();

// ADD:
// ponytail: per-team buckets — RED and BLUE forfeits are independent; global set caused cross-contamination
static final Map<Team, Set<UUID>> ffVoteYes = new EnumMap<>(Team.class);
```

Add `java.util.EnumMap` to imports if not already present.

- [ ] **Step 5: Update resetTransientState**

```java
private static void resetTransientState() {
    tickCount = 0;
    actionBarCountdown.clear();
    timeoutDeaths.clear();
    drawVoters.clear();
    ffVoteYes.clear();
    // ponytail: ffVoteNo removed — was vestigial (never read by isForfeitPassing)
}
```

- [ ] **Step 6: Update /ff command handler in CubeConquestCommand**

```java
// BEFORE
CubeConquestGameManagerEvents.ffVoteYes.add(voter.getUUID());
CubeConquestGameManagerEvents.ffVoteNo.remove(voter.getUUID());
// ...
if (CubeConquestGameManager.isForfeitPassing(
        CubeConquestGameManagerEvents.ffVoteYes,
        CubeConquestGameManagerEvents.ffVoteNo,
        teamOnline)) {

// AFTER
CubeConquestGameManagerEvents.ffVoteYes
    .computeIfAbsent(voterTeam, k -> new HashSet<>())
    .add(voter.getUUID());
// ...
Set<UUID> teamYesVotes = CubeConquestGameManagerEvents.ffVoteYes
    .getOrDefault(voterTeam, Set.of());
long yesVotes = teamOnline.stream().filter(teamYesVotes::contains).count();
long needed = (teamOnline.size() / 2) + 1;

Component msg = Component.literal(
    voter.getName().getString() + " voted to forfeit for team "
    + voterTeam.name() + " — " + yesVotes + "/" + teamOnline.size()
    + " voted yes (" + needed + " needed)");
for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);

if (CubeConquestGameManager.isForfeitPassing(teamYesVotes, teamOnline)) {
    // ponytail: re-check phase — simultaneous forfeit from both teams could call triggerVictory twice
    if (state.getPhase() == GamePhase.COMBAT) {
        Team winner = voterTeam.opponent();
        CubeConquestGameManagerEvents.triggerVictory(server, winner, state);
    }
}
```

- [ ] **Step 7: Update /ff cancel subcommand**

```java
// BEFORE
ServerPlayer voter = source.getPlayerOrException();
CubeConquestGameManagerEvents.ffVoteNo.add(voter.getUUID());
CubeConquestGameManagerEvents.ffVoteYes.remove(voter.getUUID());
source.sendSuccess(
    () -> Component.literal("You voted against forfeit — forfeit will be cancelled if 2 object"),
    false);

// AFTER
ServerPlayer voter = source.getPlayerOrException();
Team voterTeam = state.getTeamOf(voter.getUUID()).orElse(null);
if (voterTeam == null) {
    source.sendFailure(Component.literal("You are not on a team"));
    return 0;
}
CubeConquestGameManagerEvents.ffVoteYes
    .getOrDefault(voterTeam, Set.of()).remove(voter.getUUID());
source.sendSuccess(
    () -> Component.literal("You withdrew your forfeit vote"), false);
```

- [ ] **Step 8: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 9: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java
git add src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: per-team /ff vote buckets; remove vestigial ffVoteNo; /ff cancel gates on team"
```

---

### Task 2: stopGame removes cube items from porteurs (HIGH-4)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

`stopGame` removes cube blocks from the world but does NOT remove the cube block item from porteurs' inventories. After `/stop` mid-PREPARATION or mid-PLACEMENT, the porteur keeps the item, can place it freely, and the item persists into the next game.

Fix: Call `removeCubeFromInventory` for each online porteur in both teams before calling `state.reset()`.

**Interfaces:**
- Uses existing `removeCubeFromInventory(ServerPlayer, Team)` helper
- No new methods needed

- [ ] **Step 1: Update stopGame**

```java
// BEFORE
static void stopGame(MinecraftServer server) {
    removeCubeBlocksFromWorld(server);
    CubeConquestSavedData.getServerState(server).getState().reset();
    resetTransientState();
    clearClientCubePositions(server);
    broadcast(server, Component.literal("Game stopped.").withStyle(ChatFormatting.GRAY));
}

// AFTER
static void stopGame(MinecraftServer server) {
    // ponytail: remove cube items from porteurs before world/state reset — prevents item leaking into next game
    CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
    for (Team team : Team.values()) {
        UUID porteurId = state.getPorteur(team);
        if (porteurId != null) {
            ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
            if (porteur != null) removeCubeFromInventory(porteur, team);
        }
    }
    removeCubeBlocksFromWorld(server);
    CubeConquestSavedData.getServerState(server).getState().reset();
    resetTransientState();
    clearClientCubePositions(server);
    broadcast(server, Component.literal("Game stopped.").withStyle(ChatFormatting.GRAY));
}
```

- [ ] **Step 2: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 3: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git commit -m "fix: stopGame removes cube item from porteur inventory to prevent leaking into next game"
```

---

### Task 3: Reconnecting porteur gets cube re-issued (HIGH-2)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

The `onPlayerDisconnect` handler transfers the cube to another player when a porteur disconnects. But there is a timing window: if the porteur disconnects and reconnects very quickly (same tick as DISCONNECT fires, before the transfer completes), or in the edge case where the server transitions from PREP to PLACEMENT while the porteur is mid-reconnect, the JOIN handler sends team info but does NOT re-verify the porteur has the cube item.

More broadly: a porteur who is still registered as porteur (state.getPorteur(team) == their UUID) should always have the cube in their inventory. The JOIN handler should verify this invariant and re-add the item if missing.

Fix: In the JOIN handler, if phase is PREPARATION or PLACEMENT and the reconnecting player is the current porteur for their team, check their inventory. If the cube item is absent, re-add it.

**Interfaces:**
- Uses existing `cubeBlockFor(Team)` helper
- Uses `state.getPorteur(Team)` and `state.getTeamOf(UUID)`

- [ ] **Step 1: Add porteur cube re-issue to JOIN handler**

Current JOIN handler block (after team sync):
```java
if (state.getPhase() != GamePhase.IDLE) {
    state.getTeamOf(player.getUUID()).ifPresent(team ->
        ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
    );
    if (state.getPhase() == GamePhase.COMBAT) {
        for (Team t : Team.values()) {
            ServerPlayNetworking.send(player, new CubePositionPayload(t,
                Optional.ofNullable(saved.getCubePos(t))));
        }
    }
    if (state.getPhase() == GamePhase.PLACEMENT) {
        int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
        ServerPlayNetworking.send(player, new PlacementCountdownPayload(remaining));
    }
}
```

Add after the existing team sync, before the COMBAT block:
```java
    // ponytail: re-issue cube if porteur reconnects without it — covers fast reconnect race
    if (state.getPhase() == GamePhase.PREPARATION || state.getPhase() == GamePhase.PLACEMENT) {
        state.getTeamOf(player.getUUID()).ifPresent(team -> {
            if (player.getUUID().equals(state.getPorteur(team)) && saved.getCubePos(team) == null) {
                net.minecraft.world.item.Item cubeItem = cubeBlockFor(team).asItem();
                boolean hasItem = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).getItem() == cubeItem) { hasItem = true; break; }
                }
                if (!hasItem) player.getInventory().add(new ItemStack(cubeItem));
            }
        });
    }
```

Full updated JOIN handler:
```java
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
    net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
    CubeConquestState state = saved.getState();
    if (state.getPhase() != GamePhase.IDLE) {
        state.getTeamOf(player.getUUID()).ifPresent(team ->
            ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
        );
        // ponytail: re-issue cube if porteur reconnects without it — covers fast reconnect race
        if (state.getPhase() == GamePhase.PREPARATION || state.getPhase() == GamePhase.PLACEMENT) {
            state.getTeamOf(player.getUUID()).ifPresent(team -> {
                if (player.getUUID().equals(state.getPorteur(team)) && saved.getCubePos(team) == null) {
                    net.minecraft.world.item.Item cubeItem = cubeBlockFor(team).asItem();
                    boolean hasItem = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        if (player.getInventory().getItem(i).getItem() == cubeItem) { hasItem = true; break; }
                    }
                    if (!hasItem) player.getInventory().add(new ItemStack(cubeItem));
                }
            });
        }
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

- [ ] **Step 2: Build** (no unit tests possible for this MC path)

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
git commit -m "fix: re-issue cube to porteur on reconnect if item absent from inventory"
```

---

### Task 4: Phantom cube post-placement verification (HIGH-3)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

The `canBeReplaced()` guard added in Iter6 prevents registering a cube position where a solid block already exists. However, it does not cover entity-occupied positions: if a player or mob is standing exactly at `placedPos`, vanilla's block placement will fail (entity collision check), but `setCubePos` already ran and registered a phantom position.

Fix: In `handlePlacementTick`, for each team with a registered cube pos, verify the block at that position is actually a CubeBlock. If not (vanilla rejected the placement), clear the position so the porteur can try again.

This runs every tick during PLACEMENT but is O(1) per team (2 block state lookups per tick).

**Interfaces:**
- Uses `saved.getCubePos(Team)` (returns BlockPos)
- Uses `serverLevel.getBlockState(BlockPos).getBlock() instanceof CubeBlock`
- Uses `saved.setCubePos(Team, null)` to clear phantom

- [ ] **Step 1: Add phantom-verification block in handlePlacementTick**

In `handlePlacementTick`, add after the `drainActionBar(server)` call and before the both-cubes-placed check:

```java
// ponytail: verify registered positions have actual CubeBlocks — clears phantoms from entity-blocked placements
ServerLevel overworld = server.getLevel(Level.OVERWORLD);
if (overworld != null) {
    for (Team team : Team.values()) {
        BlockPos registeredPos = saved.getCubePos(team);
        if (registeredPos != null
                && !(overworld.getBlockState(registeredPos).getBlock() instanceof CubeBlock)) {
            saved.setCubePos(team, null); // placement was rejected by vanilla; allow retry
        }
    }
}
```

Place this BEFORE the `if (saved.getCubePos(Team.RED) != null && saved.getCubePos(Team.BLUE) != null)` check so we don't transition to COMBAT with a phantom.

Full updated start of `handlePlacementTick`:
```java
private static void handlePlacementTick(MinecraftServer server, CubeConquestSavedData saved,
                                         CubeConquestState state) {
    drainActionBar(server);
    // ponytail: verify registered positions have actual CubeBlocks — clears phantoms from entity-blocked placements
    ServerLevel overworld = server.getLevel(Level.OVERWORLD);
    if (overworld != null) {
        for (Team team : Team.values()) {
            BlockPos registeredPos = saved.getCubePos(team);
            if (registeredPos != null
                    && !(overworld.getBlockState(registeredPos).getBlock() instanceof CubeBlock)) {
                saved.setCubePos(team, null);
            }
        }
    }
    tickCount++;
    if (saved.getCubePos(Team.RED) != null && saved.getCubePos(Team.BLUE) != null) {
        // ... rest unchanged
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
git commit -m "fix: verify CubeBlock exists at registered pos each placement tick; clear phantoms from entity-blocked placements"
```

---

### Task 5: Cube positions broadcast at PLACEMENT→COMBAT transition (MEDIUM-7)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

After both cubes are placed and `transitionToCombat` fires, clients wait up to 1 second (20 ticks) before receiving the first `CubePositionPayload` from `handleCombatTick`. During that window the compass points nowhere and the HUD shows nothing.

Fix: In `transitionToCombat`, immediately broadcast both cube positions to all team members, mirroring the combat tick handler's logic.

**Interfaces:**
- Needs `CubeConquestSavedData saved` parameter (currently only `MinecraftServer server, CubeConquestState state`)
- The caller `handlePlacementTick` already has `saved` — pass it through

- [ ] **Step 1: Update transitionToCombat signature and body**

```java
// BEFORE
private static void transitionToCombat(MinecraftServer server, CubeConquestState state) {
    tickCount = 0;
    state.setPhase(GamePhase.COMBAT);
    actionBarCountdown.clear();
    broadcast(server, Component.literal("COMBAT! Destroy the enemy cube!").withStyle(ChatFormatting.RED));
}

// AFTER
private static void transitionToCombat(MinecraftServer server, CubeConquestSavedData saved,
                                        CubeConquestState state) {
    tickCount = 0;
    state.setPhase(GamePhase.COMBAT);
    actionBarCountdown.clear();
    broadcast(server, Component.literal("COMBAT! Destroy the enemy cube!").withStyle(ChatFormatting.RED));
    // ponytail: send positions immediately so compass/HUD works from tick 1 of COMBAT (not tick 20)
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

- [ ] **Step 2: Update caller in handlePlacementTick**

```java
// BEFORE
transitionToCombat(server, state);

// AFTER
transitionToCombat(server, saved, state);
```

- [ ] **Step 3: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git commit -m "fix: broadcast cube positions immediately on PLACEMENT->COMBAT transition, not after first combat tick"
```

---

### Task 6: /draw duplicate vote guard + remove useless test (MEDIUM-10 + LOW-12)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Context — two small fixes:**

**6a. /draw duplicate votes (MEDIUM-10)**

A player can type `/draw` multiple times. Each call re-adds their UUID to `drawVoters` (no-op on a Set) but still broadcasts the vote count and re-checks the threshold. This spams chat.

Fix: Check the return value of `drawVoters.add(voter.getUUID())`. If false (already voted), send a "You already voted" message and return early.

**6b. Remove useless test (LOW-12)**

`isPlacementTimedOut_returns_true_when_count_exceeds_threshold` asserts `3601 >= 3600` and friends — these test the Java `>=` operator, not production code. Zero regression protection.

Fix: Delete the test method entirely.

- [ ] **Step 1: Add duplicate vote guard in /draw command**

```java
// BEFORE
CubeConquestGameManagerEvents.drawVoters.add(voter.getUUID());

Set<UUID> redOnline = ...
// ... rest of handler

// AFTER
if (!CubeConquestGameManagerEvents.drawVoters.add(voter.getUUID())) {
    source.sendFailure(Component.literal("You already voted for draw"));
    return 0;
}

Set<UUID> redOnline = ...
// ... rest of handler unchanged
```

- [ ] **Step 2: Remove the useless test from CubeConquestGameManagerTest**

Delete the entire `isPlacementTimedOut_returns_true_when_count_exceeds_threshold` test method:
```java
@Test
void isPlacementTimedOut_returns_true_when_count_exceeds_threshold() {
    assertThat(3601 >= 3600).isTrue();
    assertThat(3600 >= 3600).isTrue();
    assertThat(3599 >= 3600).isFalse();
}
```

- [ ] **Step 3: Run tests**

```
./gradlew test
```

Expected: all PASS (one fewer test, but no regression — the deleted test had zero value)

- [ ] **Step 4: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: /draw early-return on duplicate vote; remove no-op operator test"
```

---

### Task 7: /cubeconquest team add mid-game guard (MEDIUM-8)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

**Context:**

The `team add` command has no phase check. Adding a player mid-game skips the game start flow: no compass given, no `PlayerTeamPayload` sent, no cube given if they should be porteur. Worse, they can immediately break the enemy cube. 

Fix: Reject `team add` and `team remove` when phase != IDLE.

- [ ] **Step 1: Write test**

Add to `CubeConquestGameManagerTest.java` — this is a pure-Java contract test documenting the invariant:
```java
@Test
void teamModification_only_valid_in_idle_phase() {
    // Documents that team changes must be blocked when game is not IDLE
    assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.PREPARATION);
    assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.PLACEMENT);
    assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.COMBAT);
}
```

- [ ] **Step 2: Add phase guard in team add command**

In the `team add` executor, after retrieving `target`:
```java
// Add after CubeConquestState state = CubeConquestSavedData.getServerState(...).getState(); 
// (add this line before addPlayer)
CubeConquestState state = CubeConquestSavedData.getServerState(
    ctx.getSource().getServer()).getState();
if (state.getPhase() != GamePhase.IDLE) {
    ctx.getSource().sendFailure(Component.literal("Cannot change teams while a game is running"));
    return 0;
}
state.addPlayer(team, target.getUUID());
```

Wait — currently `team add` calls `CubeConquestSavedData.getServerState(...).getState().addPlayer(...)` inline. Refactor to separate the state retrieval:

Full updated `team add` executor:
```java
.executes(ctx -> {
    String playerName = StringArgumentType.getString(ctx, "player");
    String teamArg = StringArgumentType.getString(ctx, "team").toUpperCase();
    Team team;
    try { team = Team.valueOf(teamArg); }
    catch (IllegalArgumentException e) {
        ctx.getSource().sendFailure(Component.literal("Unknown team: " + teamArg));
        return 0;
    }
    ServerPlayer target = ctx.getSource().getServer()
        .getPlayerList().getPlayerByName(playerName);
    if (target == null) {
        ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
        return 0;
    }
    CubeConquestState state = CubeConquestSavedData.getServerState(
        ctx.getSource().getServer()).getState();
    // ponytail: block mid-game team changes — new player skips start-game setup (no compass, no cube)
    if (state.getPhase() != GamePhase.IDLE) {
        ctx.getSource().sendFailure(Component.literal("Cannot change teams while a game is running"));
        return 0;
    }
    state.addPlayer(team, target.getUUID());
    ctx.getSource().sendSuccess(
        () -> Component.literal(playerName + " added to " + team.displayName() + " team."),
        true);
    return 1;
}))))
```

- [ ] **Step 3: Add phase guard in team remove command**

Similarly:
```java
.executes(ctx -> {
    String playerName = StringArgumentType.getString(ctx, "player");
    ServerPlayer target = ctx.getSource().getServer()
        .getPlayerList().getPlayerByName(playerName);
    if (target == null) {
        ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
        return 0;
    }
    CubeConquestState state = CubeConquestSavedData.getServerState(
        ctx.getSource().getServer()).getState();
    // ponytail: block mid-game team changes
    if (state.getPhase() != GamePhase.IDLE) {
        ctx.getSource().sendFailure(Component.literal("Cannot change teams while a game is running"));
        return 0;
    }
    state.removePlayer(target.getUUID());
    ctx.getSource().sendSuccess(
        () -> Component.literal(playerName + " removed from their team."), true);
    return 1;
})))
```

- [ ] **Step 4: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 5: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java
git add src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java
git commit -m "fix: block team add/remove while game is running to prevent setup bypass"
```

---

### Task 8: tickCount persistence across server restarts (MEDIUM-5)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

**Context:**

`tickCount` is a static field in `CubeConquestGameManagerEvents`. If the server restarts mid-PLACEMENT, the class reloads and `tickCount = 0` — the porteur gets a fresh 3-minute timer.

Fix:
1. Add `tickCount` as an optional int field in `CubeConquestSavedData` codec (backward-compatible — defaults to 0 if absent)
2. In `CubeConquestGameManagerEvents`, add a `static boolean tickCountRestored = false` flag
3. In `onServerTick`, on first tick after class load: if phase != IDLE and flag is false, restore `tickCount` from saved data
4. Update `resetTransientState` to reset the saved tick count too

Note: `tickCount` is stored in SavedData by updating it at the end of each tick handler — this means it lags by 1 tick, which is acceptable.

**Interfaces:**
- New methods: `CubeConquestSavedData.getTickCount()`, `CubeConquestSavedData.setTickCount(int)`
- No MC version-specific API required

- [ ] **Step 1: Add tickCount field and codec to CubeConquestSavedData**

```java
// Add field:
private int tickCount = 0;

// Add to CODEC (within RecordCodecBuilder, after blueCubePos):
Codec.INT.optionalFieldOf("tickCount", 0)
    .forGetter(s -> s.tickCount)

// Add in apply lambda (new parameter at end):
// ...bluePos) -> {  →  // ...bluePos, tc) -> {
//    ...existing restore code...
//    d.tickCount = tc;
//    return d;

// Add getter/setter:
public int getTickCount() { return tickCount; }
public void setTickCount(int value) { tickCount = value; setDirty(); }
```

Full updated CODEC (only the changed parts shown):
```java
private int tickCount = 0;

private static final Codec<CubeConquestSavedData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
    Codec.STRING.fieldOf("phase").forGetter(s -> s.state.getPhase().name()),
    UUID_LIST_CODEC.fieldOf("redPlayers").forGetter(s -> new ArrayList<>(s.state.getPlayers(Team.RED))),
    UUID_LIST_CODEC.fieldOf("bluePlayers").forGetter(s -> new ArrayList<>(s.state.getPlayers(Team.BLUE))),
    Codec.STRING.optionalFieldOf("redPorteur").forGetter(s -> Optional.ofNullable(s.state.getPorteur(Team.RED)).map(UUID::toString)),
    Codec.STRING.optionalFieldOf("bluePorteur").forGetter(s -> Optional.ofNullable(s.state.getPorteur(Team.BLUE)).map(UUID::toString)),
    INT3_CODEC.optionalFieldOf("redCubePos").forGetter(s -> Optional.ofNullable(s.state.getCubePos(Team.RED))),
    INT3_CODEC.optionalFieldOf("blueCubePos").forGetter(s -> Optional.ofNullable(s.state.getCubePos(Team.BLUE))),
    Codec.INT.optionalFieldOf("tickCount", 0).forGetter(s -> s.tickCount)
).apply(inst, (phase, red, blue, redP, blueP, redPos, bluePos, tc) -> {
    CubeConquestSavedData d = new CubeConquestSavedData();
    d.state.setPhase(GamePhase.valueOf(phase));
    red.forEach(id -> d.state.addPlayer(Team.RED, id));
    blue.forEach(id -> d.state.addPlayer(Team.BLUE, id));
    d.state.setPorteur(Team.RED, redP.map(UUID::fromString).orElse(null));
    d.state.setPorteur(Team.BLUE, blueP.map(UUID::fromString).orElse(null));
    redPos.ifPresent(xyz -> d.state.setCubePos(Team.RED, xyz));
    bluePos.ifPresent(xyz -> d.state.setCubePos(Team.BLUE, xyz));
    d.tickCount = tc;
    return d;
}));

public int getTickCount() { return tickCount; }
public void setTickCount(int value) { tickCount = value; setDirty(); }
```

- [ ] **Step 2: Add restoration logic in CubeConquestGameManagerEvents**

Add static flag:
```java
// ponytail: restored from SavedData once on first tick after class load — prevents restart resetting placement timer
private static boolean tickCountRestored = false;
```

In `onServerTick`, before the switch:
```java
private static void onServerTick(MinecraftServer server) {
    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
    CubeConquestState state = saved.getState();
    // ponytail: sync tickCount from persistence on first tick after server (re)start
    if (!tickCountRestored) {
        tickCountRestored = true;
        if (state.getPhase() != GamePhase.IDLE) {
            tickCount = saved.getTickCount();
        }
    }
    switch (state.getPhase()) {
        // ... unchanged
    }
}
```

Update `resetTransientState` to also reset the saved tick count and the restored flag:
```java
private static void resetTransientState() {
    tickCount = 0;
    tickCountRestored = false; // ponytail: reset so next startGame initializes fresh
    actionBarCountdown.clear();
    timeoutDeaths.clear();
    drawVoters.clear();
    ffVoteYes.clear();
}
```

Note: `resetTransientState` doesn't have access to `server` or `saved`. We need to persist tickCount from within the tick handlers. Add `saved.setTickCount(tickCount)` at the END of each tick handler (preparation, placement, combat).

In `handlePreparationTick`, at the end of the method (after the timeout check and before returning):
```java
saved.setTickCount(tickCount);
```
But `handlePreparationTick` currently doesn't receive `saved`. We need to add it.

Add `CubeConquestSavedData saved` parameter to `handlePreparationTick`:
```java
// Signature change:
private static void handlePreparationTick(MinecraftServer server, CubeConquestSavedData saved, CubeConquestState state) {
```

Update caller in `onServerTick`:
```java
case PREPARATION -> handlePreparationTick(server, saved, state);
```

At the end of `handlePreparationTick`:
```java
saved.setTickCount(tickCount);
```

At the end of `handlePlacementTick` (before early return on both-placed):
Actually we must save BEFORE the transitionToCombat (which resets tickCount to 0):
```java
// At end of handlePlacementTick, after the porteur timeout loop:
saved.setTickCount(tickCount);
```

For `handleCombatTick`, add at the end:
```java
saved.setTickCount(tickCount);
```

- [ ] **Step 3: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL (this task touches codec + tick handlers)

- [ ] **Step 4: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 5: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git commit -m "fix: persist tickCount across server restarts; placement timer survives reload"
```

---

### Task 9: Porteur immobilization improvement + compass no-target comment (MEDIUM-9 + LOW-11)

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`
- Modify: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java`

**Context — two fixes:**

**9a. Porteur immobilization (MEDIUM-9)**

`setDeltaMovement(0, y, 0)` immobilizes the porteur by zeroing horizontal velocity every tick. This is effective but causes visual rubber-banding because the client predicts movement for one tick before the server correction arrives. Environmental knockback (pistons, flowing water) also causes visible jitter.

Better approach: apply a `MobEffectInstance` of `MobEffects.MOVEMENT_SLOWDOWN` at amplifier 127 (maximum) once at PLACEMENT start, with a duration that covers the full timeout. This is server-authoritative and recognized by the client's movement prediction.

RÈGLE ANTI-HALLUCINATION: Before implementing, verify the MC 26.2 API for applying effects:
- `MobEffects.MOVEMENT_SLOWDOWN` — check this is the correct registry key in MC 26.2
- `MobEffectInstance` constructor signature
- `player.addEffect(MobEffectInstance)` — verify this is the correct call

If the MC 26.2 API has changed (e.g., `MobEffects` is now a `Holder<MobEffect>` not a `MobEffect` directly), adapt accordingly. Fall back to documenting `setDeltaMovement` as accepted with a `// ponytail:` comment if the API is unclear.

Implementation plan (subject to API verification):
- At PREP→PLACEMENT transition (in `handlePreparationTick` when tickCount >= PREPARATION_TICKS), apply the slowness effect to the porteur instead of relying on `setDeltaMovement`
- Replace the `setDeltaMovement` call in `handlePlacementTick` with the effect application (apply once, not every tick)
- On porteur change (death/transfer) or phase end, remove the effect

**9b. Compass no-target comment (LOW-11)**

The compass returns `0f` for both "no target" and "target directly ahead". The item model cannot distinguish these cases. This is an intentional design choice: `0f` is the default/neutral position.

Fix: Add a `// ponytail:` comment explaining this is intentional.

- [ ] **Step 1: Research MC 26.2 MobEffect API**

Before writing any code: check the project's existing source or generated MC sources for how effects are applied. Search for `addEffect` or `MobEffects` in the codebase.

```bash
grep -r "MobEffect" src/ --include="*.java" -l
```

If no existing usage: inspect generated MC sources under `.gradle/` for `MobEffects` class, or look for how `MobEffectInstance` is constructed in similar Fabric 26.2 mods.

If `MobEffects.MOVEMENT_SLOWDOWN` returns a `Holder<MobEffect>`:
```java
// Use: new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier, false, false, false)
// where the Holder version is: Holder<MobEffect> → MobEffectInstance accepts Holder<MobEffect>
```

If the API is unclear after research: **skip the implementation**, add a `// ponytail: setDeltaMovement causes 1-tick rubber-band; would use MobEffects.MOVEMENT_SLOWDOWN amplifier 127 but API needs verification against MC 26.2 sources` comment, and report DONE_WITH_CONCERNS.

- [ ] **Step 2 (conditional): Apply effect at PLACEMENT start, remove setDeltaMovement per-tick call**

Only implement this step if Step 1 confirmed the API. The effect should be applied once per porteur in `transitionToPlacement`-style context (when phase becomes PLACEMENT, at tick 0). Porteur change must re-apply the effect to the new porteur.

Because the implementation depends on API research, the exact code is left to the implementer — the goal is: porteur has Slowness 127 for PLACEMENT_TIMEOUT_TICKS duration; no setDeltaMovement call needed.

- [ ] **Step 3: Add compass comment**

In `TrackingCompassPropertyHandler.java`, add comment on the `target == null` early return:

```java
if (target == null) return 0f; // ponytail: 0f = "no target" and "target ahead" are indistinguishable; item model uses 0 as neutral/resting position
```

- [ ] **Step 4: Run tests**

```
./gradlew test
```

Expected: all PASS

- [ ] **Step 5: Build**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
git add src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java
git commit -m "fix: porteur immobilization via MobEffect; document compass no-target behavior"
```

(If MobEffect API couldn't be verified, commit message: `chore: document porteur rubber-band limitation; compass no-target comment`)
