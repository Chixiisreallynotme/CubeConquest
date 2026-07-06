# Iter16 Implementation Plan — 2026-07-01

Base commit: 9e23dc1

## Findings approved (7)

| # | Sev | Title |
|---|-----|-------|
| 1 | MEDIUM | stopGame uses state.reset() destroying rosters — inconsistent with triggerVictory/triggerDraw |
| 2 | MEDIUM | Client placement countdown jumps in 1-second steps — no client-side interpolation |
| 3 | MEDIUM | Draw/FF voters who disconnect during COMBAT retain their vote; "already voted" on reconnect |
| 4 | MEDIUM | SavedDataType fourth arg null — fragile against MC API changes |
| 5 | LOW | No codec round-trip test for CubeConquestSavedData persistence |
| 6 | LOW | HUD shows no indicator during COMBAT phase |
| 7 | LOW | "Already voted" message gives no hint the vote is still active |

## Tasks

### Task 1 — MEDIUM: Clarify stopGame roster-clearing behavior
**File**: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

`stopGame` calls `state.reset()` which clears team rosters. This is intentional (admin force-stop
resets everything), but the success message just says "Game stopped." which doesn't warn about
roster loss.

Change the stop success message to:
```java
ctx.getSource().sendSuccess(() -> Component.literal("Game stopped. Team rosters cleared."), true);
```

This makes the behavior explicit without changing semantics.

### Task 2 — MEDIUM: Client-side countdown tick interpolation
**Files**: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java`,
          `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`

Add a `clientTick()` method to `TrackingCompassClientHandler` that decrements `placementTicksRemaining`
by 1 each client tick (clamped at -1):
```java
// ponytail: client-side decrement for smooth countdown — server corrects every 20 ticks
public static void clientTick() {
    if (placementTicksRemaining > 0) placementTicksRemaining--;
}
```

In `CubeConquestClient.java`, register a `ClientTickEvents.END_CLIENT_TICK` listener that calls
`TrackingCompassClientHandler.clientTick()`:
```java
net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
    client -> TrackingCompassClientHandler.clientTick()
);
```

This gives a smooth decrement between server packets, corrected each second.

### Task 3 — MEDIUM: Clear vote state on COMBAT disconnect
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManagerEvents.java`

In `onPlayerDisconnect`, extend the handler to also clean up votes for COMBAT phase disconnects.
The current method returns early for non-PREP/PLACEMENT phases. After the existing porteur-handling
block, add:

```java
// ponytail: remove disconnecting player's votes — prevents stale "already voted" on reconnect
if (state.getPhase() == GamePhase.COMBAT) {
    CubeConquestGameManagerEvents.removeDrawVote(player.getUUID());
    state.getTeamOf(player.getUUID()).ifPresent(team ->
        CubeConquestGameManagerEvents.removeFfVote(team, player.getUUID())
    );
}
```

Wait — `onPlayerDisconnect` is a static method in the same class, so it accesses `removeDrawVote`
and `removeFfVote` directly (no prefix needed). Place the block at the end of `onPlayerDisconnect`,
after the porteur loop (but only when phase is COMBAT — the existing PREP/PLACEMENT path returns
early and doesn't reach this code).

Actually the current structure is:
```
if (phase != PREP && phase != PLACEMENT) return;
for (Team team : Team.values()) { ... return; }
```
The early return exits for IDLE and COMBAT. Restructure: remove the early-return for COMBAT
so vote cleanup can run. New structure:
```java
// PREP/PLACEMENT porteur handling
if (state.getPhase() == GamePhase.PREPARATION || state.getPhase() == GamePhase.PLACEMENT) {
    for (Team team : Team.values()) {
        if (player.getUUID().equals(state.getPorteur(team))) {
            // ... existing porteur logic ...
            return;
        }
    }
}
// COMBAT vote cleanup
if (state.getPhase() == GamePhase.COMBAT) {
    removeDrawVote(player.getUUID());
    state.getTeamOf(player.getUUID()).ifPresent(team -> removeFfVote(team, player.getUUID()));
}
```

### Task 4 — MEDIUM: Verify and document SavedDataType null fourth arg
**File**: `src/main/java/fr/chixi/cubeconquest/CubeConquestSavedData.java`

Inspect the MC 26.2 jar to verify the `SavedDataType` constructor signature and whether `null`
is a safe value for the fourth argument. Run:
```bash
javap -p "path/to/minecraft-26.2-deobf.jar" net.minecraft.world.level.saveddata.SavedDataType
```
or check `.gradle/loom-cache` for decompiled sources.

If null is documented/expected for "no DataFixer", add a comment:
```java
// ponytail: null = no DataFixer migration needed — safe per SavedDataType constructor contract
null
```
If it's not safe, replace with the appropriate no-op value from the API.

### Task 5 — LOW: Codec round-trip test
**File**: `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`
(or new `CubeConquestSavedDataCodecTest.java`)

The codec lives in `CubeConquestSavedData` and uses `RecordCodecBuilder`. Testing it requires
a DynamicOps instance. Since this class directly uses Mojang's serialization library,
test it with `JsonOps.INSTANCE`:

```java
@Test
void codec_round_trip_preserves_all_fields() {
    // Build source state
    CubeConquestSavedData original = new CubeConquestSavedData();
    UUID redId = UUID.randomUUID(), blueId = UUID.randomUUID();
    original.getState().addPlayer(Team.RED, redId);
    original.getState().addPlayer(Team.BLUE, blueId);
    original.getState().setPorteur(Team.RED, redId);
    original.getState().setPorteur(Team.BLUE, blueId);
    original.getState().setPhase(GamePhase.PLACEMENT);
    original.getState().setCubePos(Team.RED, new int[]{10, 64, -5});
    original.setTickCount(1234);

    // Encode then decode
    com.mojang.serialization.DataResult<com.google.gson.JsonElement> encoded =
        CubeConquestSavedData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, original);
    CubeConquestSavedData decoded = CubeConquestSavedData.CODEC
        .parse(com.mojang.serialization.JsonOps.INSTANCE, encoded.getOrThrow())
        .getOrThrow();

    assertThat(decoded.getState().getPhase()).isEqualTo(GamePhase.PLACEMENT);
    assertThat(decoded.getState().getPlayers(Team.RED)).containsExactly(redId);
    assertThat(decoded.getState().getPorteur(Team.RED)).isEqualTo(redId);
    assertThat(decoded.getState().getCubePos(Team.RED)).isEqualTo(new int[]{10, 64, -5});
    assertThat(decoded.getState().getCubePos(Team.BLUE)).isNull();
    assertThat(decoded.getTickCount()).isEqualTo(1234);
}
```

Note: `CODEC` must be package-accessible or made package-private. Currently it is `private static final`.
Change `private static final Codec<CubeConquestSavedData> CODEC` to `static final` (package-private)
to allow test access.

### Task 6 — LOW: COMBAT phase HUD indicator
**File**: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java`

Add a simple team indicator when `clientTeam != null` and no placement countdown is active.
In `render`, after the countdown block:

```java
// ponytail: show team indicator during COMBAT (clientTeam set by PlayerTeamPayload, cleared on game end)
fr.chixi.cubeconquest.Team team = TrackingCompassClientHandler.getClientTeam();
if (team != null && countdown < 0) {
    int color = team == fr.chixi.cubeconquest.Team.RED ? 0xFF5555 : 0x5555FF;
    graphics.drawString(mc.font, "Team: " + team.displayName(), x, y + 10, color, true);
}
```

This shows "Team: Red" or "Team: Blue" during COMBAT (and briefly at game start before countdown
appears). It reuses the same y-offset as the countdown (they are mutually exclusive: countdown
shows during PLACEMENT, team shows outside PLACEMENT).

### Task 7 — LOW: Improve "already voted" message
**File**: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

In `/draw`, change:
```java
source.sendFailure(Component.literal("You already voted for draw"));
```
to:
```java
source.sendFailure(Component.literal("Your draw vote is still active — use /draw cancel to withdraw"));
```

In `/ff`, change:
```java
source.sendFailure(Component.literal("You have already voted to forfeit"));
```
to:
```java
source.sendFailure(Component.literal("Your forfeit vote is still active — use /ff cancel to withdraw"));
```

## Order of execution

Tasks 1, 7: CubeConquestCommand.java — run in parallel (same file, different lines)
Task 2: TrackingCompassClientHandler.java + CubeConquestClient.java
Task 3: CubeConquestGameManagerEvents.java
Task 4: CubeConquestSavedData.java (verify only, comment if safe)
Task 5: test file (needs CODEC package-private change in CubeConquestSavedData.java — after Task 4)
Task 6: CubeConquestHud.java

Each task: implementer subagent → reviewer subagent → commit on NICE.
