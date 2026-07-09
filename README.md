# Cube Conquest

Fabric mod for Minecraft. Two teams, two cubes, one rule: destroy theirs, protect yours. Red vs Blue, three-phase rounds.

## How a Round Works

**Preparation** — PvP off. Each team gets a random **Carrier** who holds the cube. Carrier dies? Cube transfers to a living teammate. Timer ticks down; if it expires, the Carrier is frozen until they place.

**Placement** — Carriers place their cube in the world. Overworld-only by default (`/cubeconquest overworldOnly <true|false>`). You can't break your own team's cube.

**Combat** — PvP on. Everyone gets a tracking compass pointing at the enemy cube. Same dimension → HUD shows distance in blocks. Different dimension → HUD shows dimension name. Break the enemy cube to win.

`/ff` starts a team-private forfeit vote. `/draw` sends a global draw offer. Both take `accept`/`refuse`.

## Architecture

- **Stack**: Minecraft 26.2 · Fabric Loader 0.19.3+ · Fabric API 0.153.0+26.2 · Loom 1.17 · Gradle 9.5.1
- **Game loop**: `CubeConquestGameManager` on `ServerTickEvents.END_SERVER_TICK`
- **Persistence**: `PersistentState` → `ServerStateManager`
- **HUD**: `Hud` class (Vulkan/Blaze3D compat)
- **Compass**: `S2C` payload + `ItemProperties.register`

## Commands

| Command | What it does |
|---|---|
| `/cubeconquest team add <player> <red\|blue>` | Assign to team |
| `/cubeconquest team remove <player>` | Remove from team |
| `/cubeconquest team list` | Show teams |
| `/cubeconquest start` | Start round |
| `/cubeconquest stop` | Force-stop |
| `/cubeconquest setPreparationTime <minutes>` | Set preparation phase duration |
| `/cubeconquest setPlacementTime <minutes>` | Set placement phase duration |
| `/cubeconquest overworldOnly <true\|false>` | Toggle dimension restriction |
| `/ff` | Forfeit vote (team-only) |
| `/ff accept` · `/ff refuse` | Respond |
| `/draw` | Draw offer (global) |
| `/draw accept` · `/draw refuse` | Respond |

## Dev Setup

```bash
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runServer
```

## Smoke Test

1. `./gradlew runServer` → connect locally
2. `/cubeconquest team add <p1> red` then `<p2> blue`
3. Configure preferences using the commands
4. `/cubeconquest start` — inventories cleared, carriers get cubes, PvP off
5. PREPARATION → PLACEMENT. Carriers place cubes.
7. Enemy breaks a cube → victory → IDLE
8. Restart server → `/cubeconquest team list` shows saved teams
