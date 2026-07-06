# Iter14 Implementation Plan — 2026-07-01

Base commit: ae42d40

## Findings approved (2)

| # | Sev | Title |
|---|-----|-------|
| 1 | MEDIUM | /draw and /ff missing .requires(isPlayer) guard |
| 2 | MEDIUM | clientTeam never cleared on game end — stale for non-rostered players |

## Tasks

### Task 1 — MEDIUM: Add .requires(isPlayer) to /draw and /ff
**File**: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

Add `.requires(CommandSourceStack::isPlayer)` to the `/draw` and `/ff` command literals,
so non-player sources (RCON, command blocks) receive a clear rejection instead of a generic
Brigadier exception.

Pattern (same as adding `.requires` to a Brigadier literal):
```java
Commands.literal("draw")
    .requires(CommandSourceStack::isPlayer)
    .executes(...)
    .then(...)
```

Same for `ff`. Check how other commands in this file handle this and match the style.

### Task 2 — MEDIUM: Clear clientTeam when game-end signal received on client
**File**: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java`
and possibly `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`

`PlacementCountdownPayload(-1)` is already used as the game-end sentinel (server sends -1
to signal no active countdown). When the client receives `ticksRemaining == -1`, clear
`clientTeam` in addition to the countdown:

In the receiver for `PlacementCountdownPayload` in `CubeConquestClient.java`:
```java
ClientPlayNetworking.registerGlobalReceiver(PlacementCountdownPayload.TYPE,
    (payload, context) -> context.client().execute(() -> {
        TrackingCompassClientHandler.updatePlacementCountdown(payload.ticksRemaining());
        if (payload.ticksRemaining() == -1) {
            TrackingCompassClientHandler.clearTeam(); // ponytail: game ended — clear stale team
        }
    }));
```

Add `clearTeam()` to `TrackingCompassClientHandler` that sets `clientTeam = null`.

## Order of execution

1 → 2 (different files — can run in parallel)

Each task: implementer subagent → reviewer subagent → commit on NICE.
