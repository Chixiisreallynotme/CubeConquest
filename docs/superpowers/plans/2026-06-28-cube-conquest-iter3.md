# CubeConquest Iteration 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three independent polish features: win/draw title screen, team-specific TrackingCompass direction, and porteur disconnect handling.

**Architecture:** Task 1 is a pure server-side change to `CubeConquestGameManagerEvents` — no new files. Task 2 adds one new S2C payload (`PlayerTeamPayload`) threaded through the main-side event handler and client-side handler/property; the client stores the local player's team so the compass points at the correct enemy cube. Task 3 hooks `ServerPlayConnectionEvents.DISCONNECT` in `CubeConquestGameManagerEvents.register()` and reuses the existing `transferCubeOnDeath` path.

**Tech Stack:** MC 26.2, Fabric API 0.153.0+26.2, Loom 1.17, Gradle 9.5.1

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
- `./gradlew test` must pass (currently 24 tests); `./gradlew build` must succeed after each task

---

## Codebase Snapshot

| File | Role |
|------|------|
| `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java` | Event hub — tick handler, death, attack, block break, UseBlock, triggerVictory, triggerDraw, startGame, stopGame |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java` | Pure-Java helpers, no MC imports |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestState.java` | Pure-Java state — getPlayers, getPorteur, setPorteur, getTeamOf, getPhase |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java` | MC SavedData wrapper, `static getServerState(MinecraftServer)` |
| `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java` | ModInitializer, payload registration |
| `src/main/java/fr/chixi/cubeconquest/Team.java` | `RED`, `BLUE`, `opponent()`, `displayName()` |
| `src/main/java/fr/chixi/cubeconquest/network/CubePositionPayload.java` | Existing S2C payload — Team + Optional<BlockPos> |
| `src/main/java/fr/chixi/cubeconquest/network/PlacementCountdownPayload.java` | Existing S2C payload — single int |
| `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java` | ClientModInitializer, registers S2C receivers |
| `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java` | Static map Team→BlockPos, placementTicksRemaining |
| `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java` | RangeSelectItemModelProperty — computes compass angle |
| `build.gradle` | JDK 21 exclusion lists |

---

### Task 1: Win/Draw Title Screen

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

No new files. No new tests (pure MC side-effects, no pure-Java logic to extract).

**Interfaces:**
- Consumes: `triggerVictory(MinecraftServer, Team, CubeConquestState)` and `triggerDraw(MinecraftServer)` — both already in `CubeConquestGameManagerEvents`; `server.getPlayerList().getPlayers()` — already used throughout this file
- Produces: `sendTitle(MinecraftServer, Component, Component)` private helper (used only inside this file)

**VERIFY before any coding in this task:**

> In MC 26.2, sending a Title + Subtitle to a `ServerPlayer` requires sending two separate packets. Candidate approach: `player.connection.send(new ClientboundSetTitleTextPacket(title))` then `player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle))` then `player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))`. All three classes are in `net.minecraft.network.protocol.game`. Confirm these class names exist and their constructors in MC 26.2 via Context7 (library: `minecraft` or `fabric-api`) or Exa before coding. Alternative: check if `ServerPlayer` exposes a higher-level helper (e.g., `player.showTitle(...)` or similar) — if it exists, prefer it.

- [ ] **Step 1.1: VERIFY the Title packet API**

  Search Context7 for `ClientboundSetTitleTextPacket` in Minecraft 26.2 / Yarn mappings. Confirm:
  - Package: `net.minecraft.network.protocol.game`
  - Constructor for `ClientboundSetTitleTextPacket`: `(Component)` 
  - Constructor for `ClientboundSetSubtitleTextPacket`: `(Component)`
  - Constructor for `ClientboundSetTitlesAnimationPacket`: `(int fadeIn, int stay, int fadeOut)`
  - `player.connection` type: `ServerGamePacketListenerImpl` which has `.send(Packet<?>)` method

  If the names differ from candidates above, update all subsequent steps accordingly before proceeding.

- [ ] **Step 1.2: Add the `sendTitle` private helper to `CubeConquestGameManagerEvents`**

  Add this method at the bottom of `CubeConquestGameManagerEvents.java`, before the closing `}`:

  ```java
  // ponytail: sends title + subtitle to every online player; 10/60/10 ticks = standard feel
  private static void sendTitle(MinecraftServer server, Component title, Component subtitle) {
      // VERIFY: confirm packet class names match MC 26.2 before using
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
          player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 10));
          player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
          player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
      }
  }
  ```

  Add the three imports at the top of the file (with the other `net.minecraft` imports):
  ```java
  import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
  import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
  import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
  ```

- [ ] **Step 1.3: Call `sendTitle` from `triggerVictory`**

  The existing `triggerVictory` method starts with a `broadcast(...)` call. Add `sendTitle` immediately after it. The full updated method body (replace existing):

  ```java
  static void triggerVictory(MinecraftServer server, Team winner, CubeConquestState state) {
      broadcast(server, Component.literal(winner.displayName() + " Team wins!").withStyle(
          winner == Team.RED ? ChatFormatting.RED : ChatFormatting.BLUE));
      sendTitle(server,
          Component.literal(winner.displayName() + " Team wins!")
              .withStyle(winner == Team.RED ? ChatFormatting.RED : ChatFormatting.BLUE),
          Component.literal("GG").withStyle(ChatFormatting.YELLOW));
      state.reset();
      tickCount = 0;
      actionBarCountdown.clear();
      timeoutDeaths.clear();
      drawVoters.clear();
      ffVoteYes.clear();
      ffVoteNo.clear();
      clearClientCubePositions(server);
  }
  ```

- [ ] **Step 1.4: Call `sendTitle` from `triggerDraw`**

  The existing `triggerDraw` method sends a `sendSystemMessage` in a loop. Add `sendTitle` after that loop. The full updated method body (replace existing):

  ```java
  static void triggerDraw(MinecraftServer server) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
          player.sendSystemMessage(Component.literal("Draw! Both teams agreed.")
              .withStyle(ChatFormatting.YELLOW));
      }
      sendTitle(server,
          Component.literal("Draw").withStyle(ChatFormatting.YELLOW),
          Component.literal("Both teams agreed").withStyle(ChatFormatting.GRAY));
      CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
      saved.getState().reset();
      tickCount = 0;
      actionBarCountdown.clear();
      drawVoters.clear();
      timeoutDeaths.clear();
      ffVoteYes.clear();
      ffVoteNo.clear();
      clearClientCubePositions(server);
  }
  ```

- [ ] **Step 1.5: Build to verify no compile errors**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`. If the packet class names were wrong, fix them now based on the VERIFY step above.

- [ ] **Step 1.6: Smoke-test in-game (manual)**

  Run `./gradlew runServer`, join two players on different teams, start a game with `/cubeconquest start`, destroy the enemy cube. Confirm a large centered title appears for all players. Also test `/cubeconquest draw` after both players vote yes.

- [ ] **Step 1.7: Commit**

  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "feat: show title screen on victory and draw"
  ```

**Self-review checklist:**
- [ ] `sendTitle` called in both `triggerVictory` and `triggerDraw`
- [ ] Victory title uses winner's team color; draw title uses YELLOW
- [ ] No new files, no new tests, no new payload
- [ ] Imports added for all three packet classes
- [ ] `./gradlew build` passes

---

### Task 2: Team-Specific TrackingCompass Direction

**Files:**
- Create: `src/main/java/fr/chixi/cubeconquest/network/PlayerTeamPayload.java`
- Modify: `build.gradle` — add `PlayerTeamPayload` to `compileJava.excludes`
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java` — register new payload type
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java` — send payload in `startGame`; register disconnect listener to re-send on reconnect; clear on stop/victory/draw
- Modify: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java` — add `clientTeam` field, `updateClientTeam`, `getClientTeam`; clear in `clear()`
- Modify: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java` — register S2C receiver for `PlayerTeamPayload`
- Modify: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java` — replace hardcoded BLUE fallback with `clientTeam.opponent()`

No new unit tests needed (all changes are MC/client-dependent, no pure-Java logic to extract).

**VERIFY before coding steps 2.4 and 2.5:**

> In Fabric API 0.153.0+26.2, `ServerPlayConnectionEvents.JOIN` fires when a player joins a world with the server already running. The candidate callback signature is:
> `ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { ... })`
> where `handler` is `ServerGamePacketListenerImpl` and `server` is `MinecraftServer`.
> Confirm: (1) event exists in `net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents`; (2) `handler.getPlayer()` returns `ServerPlayer`; (3) this fires after world join (not just TCP connect). Confirm via Context7 or Exa before coding.

**Interfaces:**
- Consumes: `CubeConquestSavedData.getServerState(MinecraftServer)`, `state.getTeamOf(UUID)`, `state.getPhase()`, `ServerPlayNetworking.send(ServerPlayer, CustomPacketPayload)` — all used elsewhere in this file
- Produces:
  - `PlayerTeamPayload` record with `Team team` field, `TYPE`, `CODEC` — used by `CubeConquestMod` and `CubeConquestGameManagerEvents`
  - `TrackingCompassClientHandler.updateClientTeam(Team)` — `void`, called from S2C receiver
  - `TrackingCompassClientHandler.getClientTeam()` — returns `Team` (nullable)

- [ ] **Step 2.1: Create `PlayerTeamPayload.java`**

  Create `src/main/java/fr/chixi/cubeconquest/network/PlayerTeamPayload.java`:

  ```java
  package fr.chixi.cubeconquest.network;

  import fr.chixi.cubeconquest.Team;
  import net.minecraft.network.FriendlyByteBuf;
  import net.minecraft.network.codec.StreamCodec;
  import net.minecraft.network.codec.StreamCodecs;
  import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
  import net.minecraft.resources.ResourceLocation;

  public record PlayerTeamPayload(Team team) implements CustomPacketPayload {
      public static final Type<PlayerTeamPayload> TYPE =
          new Type<>(ResourceLocation.fromNamespaceAndPath("cubeconquest", "player_team"));
      public static final StreamCodec<FriendlyByteBuf, PlayerTeamPayload> CODEC =
          StreamCodecs.of(
              (buf, p) -> buf.writeUtf(p.team().name()),
              buf -> new PlayerTeamPayload(Team.valueOf(buf.readUtf()))
          );
      @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
  }
  ```

- [ ] **Step 2.2: Add `PlayerTeamPayload` to `compileJava.excludes` in `build.gradle`**

  In `build.gradle`, find the `compileJava.excludes +=` block. Add the new entry:

  ```groovy
  compileJava.excludes += [
      '**/CubeConquestSavedData.java',
      '**/CubeConquestMod.java',
      '**/CubeConquestGameManagerEvents.java',
      '**/block/BlockIds.java',
      '**/block/BlockItemIds.java',
      '**/block/CubeBlock.java',
      '**/item/TrackingCompassItem.java',
      '**/network/CubePositionPayload.java',
      '**/network/PlacementCountdownPayload.java',
      '**/network/PlayerTeamPayload.java',
      '**/command/CubeConquestCommand.java',
  ]
  ```

- [ ] **Step 2.3: Register the new payload type in `CubeConquestMod.onInitialize()`**

  In `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java`:

  Add import at top:
  ```java
  import fr.chixi.cubeconquest.network.PlayerTeamPayload;
  ```

  In `onInitialize()`, after the two existing `PayloadTypeRegistry` lines, add:
  ```java
  PayloadTypeRegistry.playS2C().register(PlayerTeamPayload.TYPE, PlayerTeamPayload.CODEC);
  ```

- [ ] **Step 2.4: Send `PlayerTeamPayload` to each player in `startGame`**

  In `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`:

  Add import at top:
  ```java
  import fr.chixi.cubeconquest.network.PlayerTeamPayload;
  ```

  In `startGame`, after the line `state.setPhase(GamePhase.PREPARATION)` and the final `broadcast(...)` call, add:

  ```java
  // Send each player their own team so the compass points at the enemy
  for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      state.getTeamOf(player.getUUID()).ifPresent(team ->
          ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
      );
  }
  ```

- [ ] **Step 2.5: Re-send `PlayerTeamPayload` on player join if game is already running**

  **VERIFY first** (see VERIFY block above this task's steps).

  In `CubeConquestGameManagerEvents.register()`, add after the existing event registrations:

  ```java
  // ponytail: re-send team to reconnecting player mid-game
  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
      (handler, sender, server) -> {
          ServerPlayer player = handler.getPlayer(); // VERIFY: confirm getPlayer() method name
          CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
          if (state.getPhase() != GamePhase.IDLE) {
              state.getTeamOf(player.getUUID()).ifPresent(team ->
                  ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
              );
          }
      }
  );
  ```

  Add import at top:
  ```java
  import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
  ```

- [ ] **Step 2.6: Add `clientTeam` to `TrackingCompassClientHandler`**

  Replace the contents of `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java` with:

  ```java
  package fr.chixi.cubeconquest.client;

  import fr.chixi.cubeconquest.Team;
  import net.minecraft.core.BlockPos;

  import java.util.EnumMap;
  import java.util.Map;
  import java.util.Optional;

  public final class TrackingCompassClientHandler {
      // ponytail: static state is fine — one client, one game at a time
      private static final Map<Team, BlockPos> cubePositions = new EnumMap<>(Team.class);
      private static int placementTicksRemaining = -1;
      // ponytail: null = no active game or team unknown
      private static Team clientTeam = null;

      private TrackingCompassClientHandler() {}

      public static void updatePosition(Team team, Optional<BlockPos> pos) {
          if (pos.isPresent()) {
              cubePositions.put(team, pos.get());
          } else {
              cubePositions.remove(team);
          }
      }

      public static Optional<BlockPos> getPosition(Team team) {
          return Optional.ofNullable(cubePositions.get(team));
      }

      public static void updatePlacementCountdown(int ticks) {
          placementTicksRemaining = ticks;
      }

      public static int getPlacementTicksRemaining() {
          return placementTicksRemaining;
      }

      public static void updateClientTeam(Team team) {
          clientTeam = team;
      }

      public static Team getClientTeam() {
          return clientTeam;
      }

      public static void clear() {
          cubePositions.clear();
          placementTicksRemaining = -1;
          clientTeam = null;
      }
  }
  ```

- [ ] **Step 2.7: Register S2C receiver for `PlayerTeamPayload` in `CubeConquestClient`**

  In `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`:

  Add import at top:
  ```java
  import fr.chixi.cubeconquest.network.PlayerTeamPayload;
  ```

  In `onInitializeClient()`, after the two existing `registerGlobalReceiver` calls, add:

  ```java
  ClientPlayNetworking.registerGlobalReceiver(PlayerTeamPayload.TYPE,
      (payload, context) -> context.client().execute(
          () -> TrackingCompassClientHandler.updateClientTeam(payload.team())
      ));
  ```

- [ ] **Step 2.8: Update `TrackingCompassPropertyHandler.get()` to use the stored team**

  In `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java`, replace the target-resolution block (lines 47-49):

  Old code:
  ```java
  // ponytail: points at BLUE cube first, fallback to RED — both teams see same angle;
  // team-specific direction deferred until PlayerTeamPayload exists
  BlockPos target = TrackingCompassClientHandler.getPosition(Team.BLUE)
      .or(() -> TrackingCompassClientHandler.getPosition(Team.RED))
      .orElse(null);
  ```

  New code:
  ```java
  // ponytail: use stored clientTeam to point at the enemy; fallback if team unknown
  Team myTeam = TrackingCompassClientHandler.getClientTeam();
  Team enemyTeam = myTeam != null ? myTeam.opponent() : Team.BLUE;
  BlockPos target = TrackingCompassClientHandler.getPosition(enemyTeam).orElse(null);
  ```

- [ ] **Step 2.9: Build to verify no compile errors**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2.10: Smoke-test in-game (manual)**

  Run `./gradlew runServer`, join two players on different teams, start a game. After COMBAT phase begins, verify that the RED team player's compass points at the BLUE team's cube and vice versa — not both pointing at the same cube.

- [ ] **Step 2.11: Commit**

  ```bash
  git add src/main/java/fr/chixi/cubeconquest/network/PlayerTeamPayload.java \
          build.gradle \
          src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java \
          src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java \
          src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java \
          src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java \
          src/client/java/fr/chixi/cubeconquest/client/TrackingCompassPropertyHandler.java
  git commit -m "feat: team-specific TrackingCompass direction via PlayerTeamPayload"
  ```

**Self-review checklist:**
- [ ] `PlayerTeamPayload.java` in `src/main/java/.../network/` (not in `src/client/`)
- [ ] `PlayerTeamPayload` added to `compileJava.excludes` (not `compileClientJava.excludes`)
- [ ] `TrackingCompassClientHandler.java` is in `src/client/` and stays in `compileClientJava.excludes`
- [ ] `clear()` in `TrackingCompassClientHandler` now also sets `clientTeam = null`
- [ ] `stopGame`, `triggerVictory`, `triggerDraw` all call `clearClientCubePositions` (already true) — note: `clientTeam` is cleared client-side when `clear()` is called by the S2C empty-position broadcast handler indirectly through `updatePosition` — actually `clear()` is not called from the S2C receiver, only from a direct call. Confirm that `TrackingCompassClientHandler.clear()` is called somewhere on game end. Looking at the existing code: `clearClientCubePositions` only sends S2C packets to clear positions; `clear()` is not called directly. The `clientTeam` field persists across games. To fix: the `PlayerTeamPayload` is only sent at `startGame` and on join. When the game ends, no "clear team" packet is sent. This is acceptable: the compass just points at the last known enemy position (already empty after `clearClientCubePositions`), and the next `startGame` overwrites `clientTeam`. No extra cleanup needed — `clientTeam = null` in `clear()` is there for completeness if `clear()` is ever called directly.
- [ ] `./gradlew test` still passes 24 tests (no tests changed)
- [ ] `./gradlew build` passes

---

### Task 3: Porteur Disconnect Handling

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

No new files. No new tests (MC-dependent event handler, no pure-Java logic to extract).

**Interfaces:**
- Consumes: `transferCubeOnDeath(MinecraftServer, CubeConquestState, Team, ServerPlayer)` — already defined in `CubeConquestGameManagerEvents` (private static)
- Consumes: `ServerPlayConnectionEvents.DISCONNECT` — Fabric API event, **must be VERIFYed** (see below)
- Produces: `onPlayerDisconnect` — private static method in `CubeConquestGameManagerEvents`

**VERIFY before coding:**

> In Fabric API 0.153.0+26.2, `ServerPlayConnectionEvents.DISCONNECT` fires when a connected player's session ends (including disconnection, not just clean logout). Candidate callback signature:
> `ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { ... })`
> where `handler` is `ServerGamePacketListenerImpl` and `server` is `MinecraftServer`.
> Confirm: (1) `ServerPlayConnectionEvents.DISCONNECT` exists in `net.fabricmc.fabric.api.networking.v1`; (2) the exact lambda parameter types; (3) whether `handler.getPlayer()` (or equivalent) returns the disconnecting `ServerPlayer`, and whether it returns `null` if the player hadn't fully joined. Use Context7 or Exa before coding. If the parameter accessor method name differs, update step 3.2 accordingly.

- [ ] **Step 3.1: VERIFY `ServerPlayConnectionEvents.DISCONNECT` signature**

  Search Context7 (Fabric API) for `ServerPlayConnectionEvents`. Confirm:
  - Class is in `net.fabricmc.fabric.api.networking.v1`
  - `DISCONNECT` field exists and its event type
  - Lambda signature: `(ServerGamePacketListenerImpl handler, MinecraftServer server)`
  - How to get the `ServerPlayer` from `handler` — candidate: `handler.getPlayer()`

  If the accessor is named differently (e.g., `handler.player` field), update step 3.2 accordingly.

- [ ] **Step 3.2: Add `onPlayerDisconnect` method to `CubeConquestGameManagerEvents`**

  Add import at top of `CubeConquestGameManagerEvents.java` (if not already present from Task 2):
  ```java
  import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
  import net.minecraft.server.network.ServerGamePacketListenerImpl;
  ```

  Add this private method at the bottom of `CubeConquestGameManagerEvents`, before the closing `}`:

  ```java
  // ponytail: treat disconnect like death during PREP/PLACEMENT — reuses existing transfer path
  private static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
      ServerPlayer player = handler.getPlayer(); // VERIFY: confirm method name in MC 26.2
      if (player == null) return;

      CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
      if (state.getPhase() != GamePhase.PREPARATION && state.getPhase() != GamePhase.PLACEMENT) return;

      for (Team team : Team.values()) {
          if (player.getUUID().equals(state.getPorteur(team))) {
              transferCubeOnDeath(server, state, team, player);
              return; // a player is porteur for at most one team
          }
      }
  }
  ```

- [ ] **Step 3.3: Register the disconnect listener in `register()`**

  In the `register()` method of `CubeConquestGameManagerEvents`, add after the last existing registration line:

  ```java
  ServerPlayConnectionEvents.DISCONNECT.register(CubeConquestGameManagerEvents::onPlayerDisconnect);
  ```

  The full updated `register()` method:
  ```java
  static void register() {
      ServerTickEvents.END_SERVER_TICK.register(CubeConquestGameManagerEvents::onServerTick);
      AttackEntityCallback.EVENT.register(CubeConquestGameManagerEvents::onAttack);
      PlayerBlockBreakEvents.BEFORE.register(CubeConquestGameManagerEvents::onBlockBreak);
      ServerLivingEntityEvents.ALLOW_DEATH.register(CubeConquestGameManagerEvents::onAllowDeath);
      UseBlockCallback.EVENT.register(CubeConquestGameManagerEvents::onUseBlock);
      ServerPlayConnectionEvents.DISCONNECT.register(CubeConquestGameManagerEvents::onPlayerDisconnect);
  }
  ```

- [ ] **Step 3.4: Build to verify no compile errors**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`. If `handler.getPlayer()` does not compile, fix the accessor based on VERIFY findings.

- [ ] **Step 3.5: Run all tests**

  ```bash
  ./gradlew test
  ```

  Expected: 24 tests pass, no failures.

- [ ] **Step 3.6: Smoke-test in-game (manual)**

  Run `./gradlew runServer`, start a game with at least 2 players per team. During PREPARATION phase, disconnect the porteur (close the game client). Verify that the cube transfers to another player on that team (the new porteur should receive the ActionBar notification). Also test during PLACEMENT phase.

- [ ] **Step 3.7: Commit**

  ```bash
  git add src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java
  git commit -m "feat: transfer cube on porteur disconnect during PREP/PLACEMENT"
  ```

**Self-review checklist:**
- [ ] `onPlayerDisconnect` is `private static`, not `static` — matches the lambda reference `::onPlayerDisconnect`
- [ ] Null guard on `player` — handles case where handler had no fully-joined player
- [ ] Phase guard limits execution to PREPARATION and PLACEMENT only
- [ ] `return` after first matching team found — a porteur can only be on one team
- [ ] Reuses `transferCubeOnDeath` exactly — no duplicated logic
- [ ] No new files, no new tests
- [ ] `./gradlew test` passes 24 tests
