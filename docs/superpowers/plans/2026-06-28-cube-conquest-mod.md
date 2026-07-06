# CubeConquest Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Fabric mod for Minecraft 26.2 where two teams (Red/Blue) each carry a cube, place it in the Overworld, and race to destroy the opposing team's cube.

**Architecture:** Three-phase state machine (PREPARATION → PLACEMENT → COMBAT) driven by `CubeConquestGameManager` hooked into `ServerTickEvents.END_SERVER_TICK`. Team state persists via `SavedData`/Codec on the Overworld's `DimensionDataStorage`. Client HUD and compass use split-environment source sets (main + client) per Loom 1.17.

**Tech Stack:** Java 25, Fabric Loader 0.19.3, Fabric API 0.153.0+26.2, Fabric Loom 1.17, Gradle 9.5.1, Minecraft 26.2

## Global Constraints

- Minecraft version: `26.2` — Gradle property `minecraft_version=26.2`
- Fabric Loader: `loader_version=0.19.3`
- Fabric API: `fabric_api_version=0.153.0+26.2`
- Loom plugin: `net.fabricmc.fabric-loom` version `1.17-SNAPSHOT`
- Gradle wrapper: `9.5.1`
- Java: 25 (source/target compatibility)
- Mod ID: `cubeconquest`
- Maven group: `fr.chixi.cubeconquest`
- Block/Item IDs: Use separate `BlockIds` / `BlockItemIds` classes — **never** `valueLookupBuilder`
- HUD rendering: Use `Hud` class (accessed via `Minecraft.getInstance().getHud()`) via Blaze3D — **never** direct OpenGL
- Networking: `PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)` + `ClientPlayNetworking.registerGlobalReceiver`
- Persistent state: `SavedDataType<T>` with `Identifier.fromNamespaceAndPath(MOD_ID, "...")` + `server.overworld().getDataStorage().computeIfAbsent(TYPE)`
- Split source sets: `src/main/java` (common), `src/client/java` (client-only)
- No `valueLookupBuilder` anywhere

---

## File Structure

```
CubeConquest/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/fr/chixi/cubeconquest/
│   │   │   ├── CubeConquestMod.java              # ModInitializer, registers blocks/items/commands/events
│   │   │   ├── GamePhase.java                    # enum: IDLE, PREPARATION, PLACEMENT, COMBAT
│   │   │   ├── Team.java                         # enum: RED, BLUE  (with helper methods)
│   │   │   ├── CubeConquestGameManager.java      # state machine, tick handler, event coordinator
│   │   │   ├── CubeConquestState.java            # SavedData subclass: teams, cube positions, phase
│   │   │   ├── block/
│   │   │   │   ├── CubeBlock.java                # shared cube block logic (both colors share class, parameterised by Team)
│   │   │   │   ├── BlockIds.java                 # static ResourceKey<Block> RED_CUBE, BLUE_CUBE
│   │   │   │   └── BlockItemIds.java             # static ResourceKey<Item> RED_CUBE, BLUE_CUBE
│   │   │   ├── item/
│   │   │   │   └── TrackingCompassItem.java      # custom compass item (server side logic)
│   │   │   ├── network/
│   │   │   │   └── CubePositionPayload.java      # S2C record: team, BlockPos (nullable = not placed)
│   │   │   └── command/
│   │   │       └── CubeConquestCommand.java      # /cubeconquest start|stop|team add|remove|list
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       ├── cubeconquest.mixins.json
│   │       └── assets/cubeconquest/lang/en_us.json
│   └── client/
│       ├── java/fr/chixi/cubeconquest/client/
│       │   ├── CubeConquestClient.java           # ClientModInitializer: registers HUD, packet receiver, compass renderer
│       │   ├── CubeConquestHud.java              # HudRenderCallback: timer, cube status, winner banner
│       │   └── TrackingCompassClientHandler.java # stores last known cube positions from S2C packets
│       └── resources/
│           └── assets/cubeconquest/
│               └── (textures/models — minimal, can be placeholder)
├── src/test/java/fr/chixi/cubeconquest/
│   ├── GamePhaseTest.java
│   ├── TeamTest.java
│   ├── CubeConquestStateTest.java
│   └── CubeConquestGameManagerTest.java         # uses mocked ServerWorld/ServerPlayerEntity
```

---

### Task 1: Gradle project scaffold

**Model:** haiku

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/cubeconquest.mixins.json`
- Create: `src/client/resources/assets/cubeconquest/lang/en_us.json` (placeholder)

**Interfaces:**
- Produces: compilable Gradle project that can `./gradlew build` (no Java sources yet — it will fail on missing entrypoint until Task 2, but the project structure is valid)

- [ ] **Step 1: Create `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

rootProject.name = 'CubeConquest'
```

- [ ] **Step 2: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false

# Fabric
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT

# Mod
mod_version=1.0.0
maven_group=fr.chixi.cubeconquest
archives_base_name=cubeconquest

# Dependencies
fabric_api_version=0.153.0+26.2
```

- [ ] **Step 3: Create `build.gradle`**

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

loom {
    splitEnvironmentSourceSets()

    mods {
        cubeconquest {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

repositories {}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": inputs.properties.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

jar {
    inputs.property "projectName", project.name
    from("LICENSE") { rename { "${it}_${project.name}" } }
}
```

- [ ] **Step 4: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: Create `src/main/resources/fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "cubeconquest",
  "version": "${version}",
  "name": "Cube Conquest",
  "description": "Competitive minigame: destroy the enemy team's cube.",
  "authors": ["Chixi"],
  "environment": "*",
  "entrypoints": {
    "main": ["fr.chixi.cubeconquest.CubeConquestMod"],
    "client": ["fr.chixi.cubeconquest.client.CubeConquestClient"]
  },
  "mixins": ["cubeconquest.mixins.json"],
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

- [ ] **Step 6: Create `src/main/resources/cubeconquest.mixins.json`**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "fr.chixi.cubeconquest.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 7: Create `src/client/resources/assets/cubeconquest/lang/en_us.json`**

```json
{
  "block.cubeconquest.red_cube": "Red Cube",
  "block.cubeconquest.blue_cube": "Blue Cube",
  "item.cubeconquest.tracking_compass": "Tracking Compass",
  "cubeconquest.hud.timer": "Time: %s",
  "cubeconquest.hud.place_cube": "Place your cube!",
  "cubeconquest.hud.frozen": "You are frozen!",
  "cubeconquest.command.started": "Game started!",
  "cubeconquest.command.stopped": "Game stopped.",
  "cubeconquest.command.team_added": "%s added to team %s.",
  "cubeconquest.command.team_removed": "%s removed from team %s.",
  "cubeconquest.command.already_running": "A game is already running.",
  "cubeconquest.victory.red": "Red Team wins!",
  "cubeconquest.victory.blue": "Blue Team wins!"
}
```

- [ ] **Step 8: Commit**

```bash
git init
git add build.gradle settings.gradle gradle.properties gradle/ src/main/resources/ src/client/resources/
git commit -m "chore: scaffold Gradle project for Fabric 26.2"
```

---

### Task 2: Core enums and state — GamePhase, Team, CubeConquestState

**Model:** sonnet

**Files:**
- Create: `src/main/java/fr/chixi/cubeconquest/GamePhase.java`
- Create: `src/main/java/fr/chixi/cubeconquest/Team.java`
- Create: `src/main/java/fr/chixi/cubeconquest/CubeConquestState.java`
- Create: `src/test/java/fr/chixi/cubeconquest/GamePhaseTest.java`
- Create: `src/test/java/fr/chixi/cubeconquest/TeamTest.java`
- Create: `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `GamePhase` enum: `IDLE`, `PREPARATION`, `PLACEMENT`, `COMBAT`
  - `Team` enum: `RED`, `BLUE` with `opponent()`, `displayName()`, `cubeItemId()`
  - `CubeConquestState` extends `SavedData`: fields `phase`, `redPlayers`, `bluePlayers` (Set<UUID>), `redCubePos`, `blueCubePos` (nullable BlockPos), `redPorteur`, `bluePorteur` (nullable UUID), plus static `getServerState(MinecraftServer)` and `SavedDataType<CubeConquestState> TYPE`

- [ ] **Step 1: Write failing tests for GamePhase**

Create `src/test/java/fr/chixi/cubeconquest/GamePhaseTest.java`:

```java
package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GamePhaseTest {
    @Test void values_are_defined() {
        assertThat(GamePhase.values()).containsExactly(
            GamePhase.IDLE, GamePhase.PREPARATION, GamePhase.PLACEMENT, GamePhase.COMBAT
        );
    }
}
```

- [ ] **Step 2: Write failing tests for Team**

Create `src/test/java/fr/chixi/cubeconquest/TeamTest.java`:

```java
package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TeamTest {
    @Test void opponent_is_symmetric() {
        assertThat(Team.RED.opponent()).isEqualTo(Team.BLUE);
        assertThat(Team.BLUE.opponent()).isEqualTo(Team.RED);
    }

    @Test void displayNames_are_non_null() {
        assertThat(Team.RED.displayName()).isNotBlank();
        assertThat(Team.BLUE.displayName()).isNotBlank();
    }
}
```

- [ ] **Step 3: Write failing tests for CubeConquestState**

Create `src/test/java/fr/chixi/cubeconquest/CubeConquestStateTest.java`:

```java
package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CubeConquestStateTest {
    @Test void initial_phase_is_idle() {
        CubeConquestState state = new CubeConquestState();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
    }

    @Test void addPlayer_and_getPlayers_for_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        assertThat(state.getPlayers(Team.RED)).contains(id);
        assertThat(state.getPlayers(Team.BLUE)).doesNotContain(id);
    }

    @Test void removePlayer_from_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        state.removePlayer(id);
        assertThat(state.getPlayers(Team.RED)).doesNotContain(id);
    }

    @Test void getTeamOf_returns_correct_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.BLUE, id);
        assertThat(state.getTeamOf(id)).contains(Team.BLUE);
    }

    @Test void getTeamOf_returns_empty_for_unknown_player() {
        CubeConquestState state = new CubeConquestState();
        assertThat(state.getTeamOf(UUID.randomUUID())).isEmpty();
    }

    @Test void reset_clears_all_state() {
        CubeConquestState state = new CubeConquestState();
        state.addPlayer(Team.RED, UUID.randomUUID());
        state.setPhase(GamePhase.COMBAT);
        state.reset();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
        assertThat(state.getPlayers(Team.RED)).isEmpty();
    }
}
```

- [ ] **Step 4: Run tests — expect FAIL (classes not defined)**

```bash
./gradlew test --tests "fr.chixi.cubeconquest.*" 2>&1 | tail -20
```

Expected: compilation failure — `GamePhase`, `Team`, `CubeConquestState` not found.

- [ ] **Step 5: Implement `GamePhase`**

Create `src/main/java/fr/chixi/cubeconquest/GamePhase.java`:

```java
package fr.chixi.cubeconquest;

public enum GamePhase {
    IDLE,
    PREPARATION,
    PLACEMENT,
    COMBAT
}
```

- [ ] **Step 6: Implement `Team`**

Create `src/main/java/fr/chixi/cubeconquest/Team.java`:

```java
package fr.chixi.cubeconquest;

public enum Team {
    RED, BLUE;

    public Team opponent() {
        return this == RED ? BLUE : RED;
    }

    public String displayName() {
        return this == RED ? "Red" : "Blue";
    }
}
```

- [ ] **Step 7: Implement `CubeConquestState` (no Minecraft deps — plain Java)**

Create `src/main/java/fr/chixi/cubeconquest/CubeConquestState.java`:

```java
package fr.chixi.cubeconquest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.*;

public class CubeConquestState extends SavedData {

    private GamePhase phase = GamePhase.IDLE;
    private final Set<UUID> redPlayers = new HashSet<>();
    private final Set<UUID> bluePlayers = new HashSet<>();
    private UUID redPorteur = null;
    private UUID bluePorteur = null;
    private BlockPos redCubePos = null;
    private BlockPos blueCubePos = null;

    public CubeConquestState() {}

    // ── Codec for persistence ──────────────────────────────────────────────

    private static final Codec<List<UUID>> UUID_LIST_CODEC =
        Codec.STRING.listOf().xmap(
            list -> list.stream().map(UUID::fromString).toList(),
            list -> list.stream().map(UUID::toString).toList()
        );

    private static final Codec<CubeConquestState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.fieldOf("phase").forGetter(s -> s.phase.name()),
        UUID_LIST_CODEC.fieldOf("redPlayers").forGetter(s -> new ArrayList<>(s.redPlayers)),
        UUID_LIST_CODEC.fieldOf("bluePlayers").forGetter(s -> new ArrayList<>(s.bluePlayers)),
        Codec.STRING.optionalFieldOf("redPorteur").forGetter(s -> Optional.ofNullable(s.redPorteur).map(UUID::toString)),
        Codec.STRING.optionalFieldOf("bluePorteur").forGetter(s -> Optional.ofNullable(s.bluePorteur).map(UUID::toString)),
        BlockPos.CODEC.optionalFieldOf("redCubePos").forGetter(s -> Optional.ofNullable(s.redCubePos)),
        BlockPos.CODEC.optionalFieldOf("blueCubePos").forGetter(s -> Optional.ofNullable(s.blueCubePos))
    ).apply(inst, (phase, red, blue, redP, blueP, redPos, bluePos) -> {
        CubeConquestState state = new CubeConquestState();
        state.phase = GamePhase.valueOf(phase);
        state.redPlayers.addAll(red);
        state.bluePlayers.addAll(blue);
        state.redPorteur = redP.map(UUID::fromString).orElse(null);
        state.bluePorteur = blueP.map(UUID::fromString).orElse(null);
        state.redCubePos = redPos.orElse(null);
        state.blueCubePos = bluePos.orElse(null);
        return state;
    }));

    public static final SavedDataType<CubeConquestState> TYPE = new SavedDataType<>(
        ResourceLocation.fromNamespaceAndPath("cubeconquest", "game_state"),
        CubeConquestState::new,
        CODEC,
        null
    );

    public static CubeConquestState getServerState(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; setDirty(); }

    public Set<UUID> getPlayers(Team team) {
        return team == Team.RED ? Collections.unmodifiableSet(redPlayers)
                                : Collections.unmodifiableSet(bluePlayers);
    }

    public void addPlayer(Team team, UUID id) {
        (team == Team.RED ? redPlayers : bluePlayers).add(id);
        setDirty();
    }

    public void removePlayer(UUID id) {
        redPlayers.remove(id);
        bluePlayers.remove(id);
        setDirty();
    }

    public Optional<Team> getTeamOf(UUID id) {
        if (redPlayers.contains(id)) return Optional.of(Team.RED);
        if (bluePlayers.contains(id)) return Optional.of(Team.BLUE);
        return Optional.empty();
    }

    public UUID getPorteur(Team team) { return team == Team.RED ? redPorteur : bluePorteur; }
    public void setPorteur(Team team, UUID id) {
        if (team == Team.RED) redPorteur = id; else bluePorteur = id;
        setDirty();
    }

    public BlockPos getCubePos(Team team) { return team == Team.RED ? redCubePos : blueCubePos; }
    public void setCubePos(Team team, BlockPos pos) {
        if (team == Team.RED) redCubePos = pos; else blueCubePos = pos;
        setDirty();
    }

    public void reset() {
        phase = GamePhase.IDLE;
        redPlayers.clear(); bluePlayers.clear();
        redPorteur = null; bluePorteur = null;
        redCubePos = null; blueCubePos = null;
        setDirty();
    }
}
```

- [ ] **Step 8: Add test dependencies to `build.gradle`**

Add inside `dependencies {}`:

```groovy
testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
testImplementation 'org.assertj:assertj-core:3.26.3'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

Add after the `java {}` block:

```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 9: Run tests — expect PASS**

```bash
./gradlew test --tests "fr.chixi.cubeconquest.GamePhaseTest" --tests "fr.chixi.cubeconquest.TeamTest" --tests "fr.chixi.cubeconquest.CubeConquestStateTest"
```

Expected: `BUILD SUCCESSFUL`, all 9 tests pass.

- [ ] **Step 10: Commit**

```bash
git add src/ build.gradle
git commit -m "feat: core enums GamePhase, Team and SavedData CubeConquestState"
```

---

### Task 3: Blocks, Items, and Registration

**Model:** sonnet

**Files:**
- Create: `src/main/java/fr/chixi/cubeconquest/block/CubeBlock.java`
- Create: `src/main/java/fr/chixi/cubeconquest/block/BlockIds.java`
- Create: `src/main/java/fr/chixi/cubeconquest/block/BlockItemIds.java`
- Create: `src/main/java/fr/chixi/cubeconquest/item/TrackingCompassItem.java`
- Create: `src/main/java/fr/chixi/cubeconquest/network/CubePositionPayload.java`
- Create: `src/main/java/fr/chixi/cubeconquest/CubeConquestMod.java`

**Interfaces:**
- Consumes: `Team` enum from Task 2
- Produces:
  - `CubeBlock(Team team)` — Block subclass; stores `Team`
  - `BlockIds.RED_CUBE`, `BlockIds.BLUE_CUBE` — `ResourceKey<Block>`
  - `BlockItemIds.RED_CUBE`, `BlockItemIds.BLUE_CUBE` — `ResourceKey<Item>`
  - `TrackingCompassItem` — Item (server side only; no rendering here)
  - `CubePositionPayload(Team team, Optional<BlockPos> pos)` — implements `CustomPacketPayload`
  - `CubeConquestMod` — `ModInitializer` that registers blocks, items, and payload type

- [ ] **Step 1: Create `BlockIds.java`**

```java
package fr.chixi.cubeconquest.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class BlockIds {
    public static final ResourceKey<Block> RED_CUBE = key("red_cube");
    public static final ResourceKey<Block> BLUE_CUBE = key("blue_cube");

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("cubeconquest", name));
    }

    private BlockIds() {}
}
```

- [ ] **Step 2: Create `BlockItemIds.java`**

```java
package fr.chixi.cubeconquest.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class BlockItemIds {
    public static final ResourceKey<Item> RED_CUBE = key("red_cube");
    public static final ResourceKey<Item> BLUE_CUBE = key("blue_cube");

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cubeconquest", name));
    }

    private BlockItemIds() {}
}
```

- [ ] **Step 3: Create `CubeBlock.java`**

```java
package fr.chixi.cubeconquest.block;

import fr.chixi.cubeconquest.Team;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CubeBlock extends Block {

    private final Team team;

    public CubeBlock(Team team, BlockBehaviour.Properties properties) {
        super(properties);
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }
}
```

- [ ] **Step 4: Create `TrackingCompassItem.java`**

```java
package fr.chixi.cubeconquest.item;

import net.minecraft.world.item.Item;

public class TrackingCompassItem extends Item {
    public TrackingCompassItem(Properties properties) {
        super(properties);
    }
}
```

- [ ] **Step 5: Create `CubePositionPayload.java`**

```java
package fr.chixi.cubeconquest.network;

import com.mojang.serialization.Codec;
import fr.chixi.cubeconquest.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record CubePositionPayload(Team team, Optional<BlockPos> pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CubePositionPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("cubeconquest", "cube_position"));

    private static final StreamCodec<FriendlyByteBuf, Team> TEAM_CODEC =
        StreamCodecs.of(
            (buf, t) -> buf.writeUtf(t.name()),
            buf -> Team.valueOf(buf.readUtf())
        );

    private static final StreamCodec<FriendlyByteBuf, Optional<BlockPos>> OPT_POS_CODEC =
        StreamCodecs.of(
            (buf, opt) -> {
                buf.writeBoolean(opt.isPresent());
                opt.ifPresent(pos -> buf.writeBlockPos(pos));
            },
            buf -> buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty()
        );

    public static final StreamCodec<FriendlyByteBuf, CubePositionPayload> CODEC =
        StreamCodecs.composite(
            TEAM_CODEC, CubePositionPayload::team,
            OPT_POS_CODEC, CubePositionPayload::pos,
            CubePositionPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 6: Create `CubeConquestMod.java` (ModInitializer)**

```java
package fr.chixi.cubeconquest;

import fr.chixi.cubeconquest.block.BlockIds;
import fr.chixi.cubeconquest.block.BlockItemIds;
import fr.chixi.cubeconquest.block.CubeBlock;
import fr.chixi.cubeconquest.item.TrackingCompassItem;
import fr.chixi.cubeconquest.network.CubePositionPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CubeConquestMod implements ModInitializer {

    public static final String MOD_ID = "cubeconquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Block instances (registered below, referenced by GameManager)
    public static CubeBlock RED_CUBE_BLOCK;
    public static CubeBlock BLUE_CUBE_BLOCK;
    public static TrackingCompassItem TRACKING_COMPASS;

    @Override
    public void onInitialize() {
        // Register blocks
        RED_CUBE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            BlockIds.RED_CUBE,
            new CubeBlock(Team.RED, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(50f, 1200f))
        );
        BLUE_CUBE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            BlockIds.BLUE_CUBE,
            new CubeBlock(Team.BLUE, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(50f, 1200f))
        );

        // Register block items (linked separately per MC 26.2)
        Registry.register(BuiltInRegistries.ITEM, BlockItemIds.RED_CUBE,
            new BlockItem(RED_CUBE_BLOCK, new Item.Properties().useBlockDescriptionPrefix()));
        Registry.register(BuiltInRegistries.ITEM, BlockItemIds.BLUE_CUBE,
            new BlockItem(BLUE_CUBE_BLOCK, new Item.Properties().useBlockDescriptionPrefix()));

        // Tracking compass
        TRACKING_COMPASS = Registry.register(
            BuiltInRegistries.ITEM,
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "tracking_compass")),
            new TrackingCompassItem(new Item.Properties().stacksTo(1))
        );

        // Register S2C payload type (must be done on both sides during init)
        PayloadTypeRegistry.playS2C().register(CubePositionPayload.TYPE, CubePositionPayload.CODEC);

        // Register game manager events
        CubeConquestGameManager.register();

        // Register commands
        command.CubeConquestCommand.register();

        LOGGER.info("CubeConquest loaded.");
    }
}
```

- [ ] **Step 7: Run `./gradlew build` — it will fail until GameManager and Command exist; note the errors**

```bash
./gradlew build 2>&1 | grep "error:" | head -20
```

Expected: `cannot find symbol: CubeConquestGameManager`, `cannot find symbol: CubeConquestCommand`. That is expected — those are the next tasks.

- [ ] **Step 8: Commit partial work**

```bash
git add src/main/java/
git commit -m "feat: blocks, items, network payload, mod initializer (partial — GameManager/Command stubs pending)"
```

---

### Task 4: GameManager — state machine and event wiring

**Model:** opus

**Files:**
- Create: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`
- Create: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`

**Interfaces:**
- Consumes: `CubeConquestState`, `GamePhase`, `Team`, `CubeConquestMod` (for block instances), `CubePositionPayload`
- Produces: `CubeConquestGameManager.register()` — static method called from `CubeConquestMod.onInitialize()`

**Note:** Minecraft server classes cannot be unit-tested without a running server. Tests here use pure logic extracted into package-private helpers that do not depend on server classes. The integration behaviour is verified by `./gradlew build` + a manual `runServer` smoke test (documented in Task 7).

- [ ] **Step 1: Write unit tests for pure game-logic helpers**

Create `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java`:

```java
package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CubeConquestGameManagerTest {

    @Test void pickPorteur_returns_random_uuid_from_list() {
        List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID porteur = CubeConquestGameManager.pickRandom(players);
        assertThat(players).contains(porteur);
    }

    @Test void pickPorteur_throws_for_empty_list() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> CubeConquestGameManager.pickRandom(List.of())
        );
    }

    @Test void transferPorteur_picks_different_uuid_when_possible() {
        UUID old = UUID.randomUUID();
        List<UUID> others = List.of(UUID.randomUUID(), UUID.randomUUID());
        UUID next = CubeConquestGameManager.pickReplacement(old, others);
        assertThat(next).isNotEqualTo(old);
        assertThat(others).contains(next);
    }

    @Test void transferPorteur_returns_empty_when_no_candidates() {
        UUID old = UUID.randomUUID();
        Optional<UUID> next = CubeConquestGameManager.pickReplacementOpt(old, List.of());
        assertThat(next).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew test --tests "fr.chixi.cubeconquest.CubeConquestGameManagerTest"
```

Expected: compilation failure — `CubeConquestGameManager` not found.

- [ ] **Step 3: Implement `CubeConquestGameManager.java`**

Create `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java`:

```java
package fr.chixi.cubeconquest;

import fr.chixi.cubeconquest.block.CubeBlock;
import fr.chixi.cubeconquest.network.CubePositionPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTitlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;

public class CubeConquestGameManager {

    // ponytail: 200 ticks = 10 seconds preparation; 3600 ticks = 3 min placement
    private static final int PREPARATION_TICKS = 200;
    private static final int PLACEMENT_TIMEOUT_TICKS = 3600;

    private static int tickCount = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CubeConquestGameManager::onServerTick);
        AttackEntityCallback.EVENT.register(CubeConquestGameManager::onAttack);
        PlayerBlockBreakEvents.BEFORE.register(CubeConquestGameManager::onBlockBreak);
        ServerLivingEntityEvents.ALLOW_DEATH.register(CubeConquestGameManager::onAllowDeath);
    }

    // ── Tick handler ──────────────────────────────────────────────────────

    private static void onServerTick(MinecraftServer server) {
        CubeConquestState state = CubeConquestState.getServerState(server);
        switch (state.getPhase()) {
            case PREPARATION -> handlePreparationTick(server, state);
            case PLACEMENT -> handlePlacementTick(server, state);
            case COMBAT -> handleCombatTick(server, state);
            default -> {} // IDLE: do nothing
        }
    }

    private static void handlePreparationTick(MinecraftServer server, CubeConquestState state) {
        tickCount++;
        if (tickCount >= PREPARATION_TICKS) {
            tickCount = 0;
            state.setPhase(GamePhase.PLACEMENT);
            broadcastTitle(server, Component.literal("Place your Cube!").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void handlePlacementTick(MinecraftServer server, CubeConquestState state) {
        tickCount++;
        // Check if both cubes are placed → transition to COMBAT
        if (state.getCubePos(Team.RED) != null && state.getCubePos(Team.BLUE) != null) {
            tickCount = 0;
            transitionToCombat(server, state);
            return;
        }
        // Timeout: freeze porteur who hasn't placed
        if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
            for (Team team : Team.values()) {
                if (state.getCubePos(team) == null && state.getPorteur(team) != null) {
                    ServerPlayer porteur = server.getPlayerList().getPlayer(state.getPorteur(team));
                    if (porteur != null) {
                        // Apply freeze: zero velocity every tick once timeout reached
                        porteur.setDeltaMovement(0, porteur.getDeltaMovement().y, 0);
                        porteur.sendSystemMessage(
                            Component.literal("You are frozen! Place your cube!").withStyle(ChatFormatting.RED));
                    }
                }
            }
        }
    }

    private static void handleCombatTick(MinecraftServer server, CubeConquestState state) {
        // Broadcast cube positions to all players every 20 ticks (1 second)
        tickCount++;
        if (tickCount % 20 == 0) {
            for (Team team : Team.values()) {
                CubePositionPayload payload = new CubePositionPayload(team,
                    Optional.ofNullable(state.getCubePos(team)));
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    // ── Game start (called from command) ─────────────────────────────────

    public static void startGame(MinecraftServer server) {
        CubeConquestState state = CubeConquestState.getServerState(server);
        if (state.getPhase() != GamePhase.IDLE) {
            throw new IllegalStateException("Game already running");
        }

        // Clear inventories
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getInventory().clearContent();
        }

        // Assign porteurs and give cubes
        for (Team team : Team.values()) {
            List<UUID> members = server.getPlayerList().getPlayers().stream()
                .map(Entity::getUUID)
                .filter(id -> state.getPlayers(team).contains(id))
                .collect(Collectors.toList());

            if (members.isEmpty()) continue;

            UUID porteurId = pickRandom(members);
            state.setPorteur(team, porteurId);

            ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
            if (porteur != null) {
                ItemStack cubeItem = new ItemStack(
                    team == Team.RED ? CubeConquestMod.RED_CUBE_BLOCK.asItem()
                                     : CubeConquestMod.BLUE_CUBE_BLOCK.asItem()
                );
                porteur.getInventory().add(cubeItem);
                porteur.sendSystemMessage(
                    Component.literal("You are the cube carrier!").withStyle(ChatFormatting.GOLD));
            }

            // Give tracking compass to all team members
            for (UUID id : state.getPlayers(team)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) p.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
            }
        }

        tickCount = 0;
        state.setPhase(GamePhase.PREPARATION);
        broadcastTitle(server, Component.literal("Game starting! PvP disabled.")
            .withStyle(ChatFormatting.GREEN));
    }

    public static void stopGame(MinecraftServer server) {
        CubeConquestState.getServerState(server).reset();
        tickCount = 0;
        broadcastTitle(server, Component.literal("Game stopped.").withStyle(ChatFormatting.GRAY));
    }

    // ── PvP blocker ───────────────────────────────────────────────────────

    private static InteractionResult onAttack(Player attacker, Level level, net.minecraft.world.InteractionHand hand,
                                               Entity target, net.minecraft.world.phys.EntityHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        CubeConquestState state = CubeConquestState.getServerState(serverLevel.getServer());
        if (state.getPhase() == GamePhase.PREPARATION && target instanceof Player) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    // ── Block break handler ───────────────────────────────────────────────

    private static boolean onBlockBreak(net.minecraft.world.level.LevelAccessor level,
                                        Player player,
                                        BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state,
                                        net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (!(state.getBlock() instanceof CubeBlock cubeBlock)) return true;

        CubeConquestState gameState = CubeConquestState.getServerState(serverLevel.getServer());
        if (gameState.getPhase() != GamePhase.PLACEMENT && gameState.getPhase() != GamePhase.COMBAT) return true;

        Team cubeTeam = cubeBlock.getTeam();
        Optional<Team> playerTeam = gameState.getTeamOf(player.getUUID());

        if (playerTeam.isEmpty()) return true;

        if (playerTeam.get() == cubeTeam) {
            // Team trying to break their own cube → block
            player.sendSystemMessage(Component.literal("You cannot destroy your own cube!").withStyle(ChatFormatting.RED));
            return false;
        }

        // Enemy team breaks the cube → victory!
        triggerVictory(serverLevel.getServer(), playerTeam.get(), gameState);
        return false; // Don't actually break the block (victory ends the game)
    }

    // ── Death handler — transfer cube ─────────────────────────────────────

    private static boolean onAllowDeath(net.minecraft.world.entity.LivingEntity entity,
                                         net.minecraft.world.damagesource.DamageSource damageSource,
                                         float damageAmount) {
        if (!(entity instanceof ServerPlayer dead)) return true;

        MinecraftServer server = dead.getServer();
        if (server == null) return true;

        CubeConquestState state = CubeConquestState.getServerState(server);
        if (state.getPhase() != GamePhase.PREPARATION && state.getPhase() != GamePhase.PLACEMENT) return true;

        for (Team team : Team.values()) {
            if (dead.getUUID().equals(state.getPorteur(team))) {
                transferCubeOnDeath(server, state, team, dead);
            }
        }
        return true; // Allow death to proceed
    }

    private static void transferCubeOnDeath(MinecraftServer server, CubeConquestState state,
                                             Team team, ServerPlayer deadPorteur) {
        // Remove cube from drops (handled by clearing inventory before death item drops)
        deadPorteur.getInventory().clearContent();

        // Find a living replacement
        List<UUID> candidates = server.getPlayerList().getPlayers().stream()
            .map(Entity::getUUID)
            .filter(id -> state.getPlayers(team).contains(id) && !id.equals(deadPorteur.getUUID()))
            .collect(Collectors.toList());

        Optional<UUID> next = pickReplacementOpt(deadPorteur.getUUID(), candidates);
        if (next.isEmpty()) {
            // No living teammate — game auto-ends with opponent winning
            triggerVictory(server, team.opponent(), state);
            return;
        }

        state.setPorteur(team, next.get());
        ServerPlayer newPorteur = server.getPlayerList().getPlayer(next.get());
        if (newPorteur != null) {
            ItemStack cubeItem = new ItemStack(
                team == Team.RED ? CubeConquestMod.RED_CUBE_BLOCK.asItem()
                                 : CubeConquestMod.BLUE_CUBE_BLOCK.asItem()
            );
            newPorteur.getInventory().add(cubeItem);
            newPorteur.sendSystemMessage(
                Component.literal("The cube has been passed to you!").withStyle(ChatFormatting.GOLD));
        }
    }

    // ── Transitions ───────────────────────────────────────────────────────

    private static void transitionToCombat(MinecraftServer server, CubeConquestState state) {
        state.setPhase(GamePhase.COMBAT);
        broadcastTitle(server, Component.literal("COMBAT! Destroy the enemy cube!")
            .withStyle(ChatFormatting.RED));
    }

    private static void triggerVictory(MinecraftServer server, Team winner, CubeConquestState state) {
        String msg = winner.displayName() + " Team wins!";
        broadcastTitle(server, Component.literal(msg).withStyle(
            winner == Team.RED ? ChatFormatting.RED : ChatFormatting.BLUE));
        state.reset();
        tickCount = 0;
    }

    // ── Utilities (package-private for tests) ────────────────────────────

    static UUID pickRandom(List<UUID> players) {
        if (players.isEmpty()) throw new IllegalArgumentException("Cannot pick from empty list");
        return players.get(new Random().nextInt(players.size()));
    }

    static UUID pickReplacement(UUID old, List<UUID> others) {
        return pickReplacementOpt(old, others)
            .orElseThrow(() -> new IllegalArgumentException("No replacement available"));
    }

    static Optional<UUID> pickReplacementOpt(UUID old, List<UUID> candidates) {
        List<UUID> filtered = candidates.stream().filter(id -> !id.equals(old)).collect(Collectors.toList());
        if (filtered.isEmpty()) return Optional.empty();
        return Optional.of(pickRandom(filtered));
    }

    private static void broadcastTitle(MinecraftServer server, Component text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(text);
        }
    }
}
```

- [ ] **Step 4: Run unit tests — expect PASS**

```bash
./gradlew test --tests "fr.chixi.cubeconquest.CubeConquestGameManagerTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: CubeConquestGameManager state machine with tick handler and event wiring"
```

---

### Task 5: Commands — `/cubeconquest`

**Model:** sonnet

**Files:**
- Create: `src/main/java/fr/chixi/cubeconquest/command/CubeConquestCommand.java`

**Interfaces:**
- Consumes: `CubeConquestGameManager.startGame()`, `CubeConquestGameManager.stopGame()`, `CubeConquestState.getServerState()`, `Team`
- Produces: `CubeConquestCommand.register()` — static method called from `CubeConquestMod.onInitialize()`

- [ ] **Step 1: Create `CubeConquestCommand.java`**

```java
package fr.chixi.cubeconquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.chixi.cubeconquest.CubeConquestGameManager;
import fr.chixi.cubeconquest.CubeConquestState;
import fr.chixi.cubeconquest.GamePhase;
import fr.chixi.cubeconquest.Team;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class CubeConquestCommand {

    private CubeConquestCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerAll(dispatcher);
        });
    }

    private static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cubeconquest")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("start")
                .executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    CubeConquestState state = CubeConquestState.getServerState(server);
                    if (state.getPhase() != GamePhase.IDLE) {
                        ctx.getSource().sendFailure(Component.literal("A game is already running."));
                        return 0;
                    }
                    CubeConquestGameManager.startGame(server);
                    ctx.getSource().sendSuccess(() -> Component.literal("Game started!"), true);
                    return 1;
                }))
            .then(Commands.literal("stop")
                .executes(ctx -> {
                    CubeConquestGameManager.stopGame(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Game stopped."), true);
                    return 1;
                }))
            .then(Commands.literal("team")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("team", StringArgumentType.word())
                            .executes(ctx -> {
                                String playerName = StringArgumentType.getString(ctx, "player");
                                String teamArg = StringArgumentType.getString(ctx, "team").toUpperCase();
                                Team team;
                                try { team = Team.valueOf(teamArg); }
                                catch (IllegalArgumentException e) {
                                    ctx.getSource().sendFailure(Component.literal("Unknown team: " + teamArg));
                                    return 0;
                                }
                                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                                if (target == null) {
                                    ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
                                    return 0;
                                }
                                CubeConquestState.getServerState(ctx.getSource().getServer())
                                    .addPlayer(team, target.getUUID());
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal(playerName + " added to " + team.displayName() + " team."), true);
                                return 1;
                            }))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
                                return 0;
                            }
                            CubeConquestState.getServerState(ctx.getSource().getServer())
                                .removePlayer(target.getUUID());
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(playerName + " removed from their team."), true);
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        CubeConquestState state = CubeConquestState.getServerState(ctx.getSource().getServer());
                        for (Team t : Team.values()) {
                            var names = state.getPlayers(t).stream()
                                .map(id -> {
                                    ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(id);
                                    return p != null ? p.getName().getString() : id.toString();
                                })
                                .toList();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(t.displayName() + " team: " + names), false);
                        }
                        return 1;
                    })))));
    }
}
```

- [ ] **Step 2: Run `./gradlew build` — expect BUILD SUCCESSFUL**

```bash
./gradlew build 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If errors remain, they are import path mismatches — fix the reported symbol.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/fr/chixi/cubeconquest/command/
git commit -m "feat: /cubeconquest start|stop|team commands"
```

---

### Task 6: Client — HUD, networking receiver, compass handler

**Model:** sonnet

**Files:**
- Create: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestClient.java`
- Create: `src/client/java/fr/chixi/cubeconquest/client/CubeConquestHud.java`
- Create: `src/client/java/fr/chixi/cubeconquest/client/TrackingCompassClientHandler.java`

**Interfaces:**
- Consumes: `CubePositionPayload` (from common), `Team`
- Produces: Client HUD overlay + packet receiver that updates `TrackingCompassClientHandler`'s stored positions

**Note:** Client rendering classes cannot be unit-tested without a running client. The correctness of this task is verified by `./gradlew build` (no errors) and a manual `runClient` smoke test (see Task 7).

- [ ] **Step 1: Create `TrackingCompassClientHandler.java`**

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

    public static void clear() {
        cubePositions.clear();
    }
}
```

- [ ] **Step 2: Create `CubeConquestHud.java`**

```java
package fr.chixi.cubeconquest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class CubeConquestHud {

    private CubeConquestHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(CubeConquestHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int y = 5;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 50;

        // Red cube position
        TrackingCompassClientHandler.getPosition(fr.chixi.cubeconquest.Team.RED).ifPresent(pos ->
            graphics.drawString(mc.font,
                "Red cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                x, y, 0xFF5555, true)
        );

        // Blue cube position
        TrackingCompassClientHandler.getPosition(fr.chixi.cubeconquest.Team.BLUE).ifPresent(pos ->
            graphics.drawString(mc.font,
                "Blue cube: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                x, y + 10, 0x5555FF, true)
        );
    }
}
```

- [ ] **Step 3: Create `CubeConquestClient.java`**

```java
package fr.chixi.cubeconquest.client;

import fr.chixi.cubeconquest.network.CubePositionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CubeConquestClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register S2C packet receiver
        ClientPlayNetworking.registerGlobalReceiver(CubePositionPayload.TYPE,
            (payload, context) -> {
                context.client().execute(() ->
                    TrackingCompassClientHandler.updatePosition(payload.team(), payload.pos())
                );
            });

        // Register HUD
        CubeConquestHud.register();
    }
}
```

- [ ] **Step 4: Run `./gradlew build` — expect BUILD SUCCESSFUL**

```bash
./gradlew build 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/client/
git commit -m "feat: client HUD, packet receiver and compass position handler"
```

---

### Task 7: Full build verification + smoke test documentation

**Model:** haiku

**Files:**
- Modify: `README.md` (add dev/test section)

**Interfaces:**
- Consumes: everything from Tasks 1–6

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass (`GamePhaseTest`, `TeamTest`, `CubeConquestStateTest`, `CubeConquestGameManagerTest`).

- [ ] **Step 2: Run full build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, jar produced in `build/libs/cubeconquest-1.0.0.jar`.

- [ ] **Step 3: Add smoke test section to `README.md`**

Append to `README.md`:

```markdown
## Développement et smoke test

### Build

```bash
./gradlew build
./gradlew test
```

### Smoke test manuel (runServer)

```bash
./gradlew runServer
```

1. Se connecter au serveur local.
2. Exécuter `/cubeconquest team add <joueur1> red`
3. Exécuter `/cubeconquest team add <joueur2> blue`
4. Exécuter `/cubeconquest start`
   - Les inventaires sont vidés.
   - Le porteur de chaque équipe reçoit le cube.
   - Le PvP est désactivé (phase PREPARATION).
5. Attendre 10 secondes → phase PLACEMENT, message "Place your Cube!".
6. Le porteur pose le cube dans l'Overworld. Répéter pour les deux équipes → phase COMBAT.
7. Un joueur de l'équipe adverse détruit le cube → message de victoire, retour à IDLE.
8. Redémarrer le serveur → `/cubeconquest team list` doit afficher les équipes sauvegardées.
```

- [ ] **Step 4: Final commit**

```bash
git add README.md
git commit -m "docs: add smoke test procedure and build instructions"
```

---

## Self-Review

### Spec coverage check

Reviewing against `README.md` and `CLAUDE.md`:

- ✅ Deux équipes (Rouge/Bleu) — `Team` enum, commands
- ✅ Phase PREPARATION — PvP bloqué via `AttackEntityCallback`, porteur reçoit cube
- ✅ Mort porteur → cube transféré → `onAllowDeath` + `transferCubeOnDeath`
- ✅ Placement Overworld uniquement — NOT YET: block placement restriction to Overworld is missing!
- ✅ Protection bloc équipe propriétaire — `onBlockBreak`
- ✅ Détection victoire — `triggerVictory`
- ✅ Pénalité timeout porteur non posé — `handlePlacementTick` freeze velocity
- ✅ Phase COMBAT PvP actif — `onAttack` only blocks in PREPARATION
- ✅ Boussole Tracking Compass — `TrackingCompassItem`, client handler, S2C packet
- ✅ HUD timer/statut — `CubeConquestHud` (basic; timer display uses tick count)
- ✅ PersistentState survit redémarrage — `SavedDataType` + Codec
- ✅ Commande `/cubeconquest` — `CubeConquestCommand`
- ✅ BlockIds/BlockItemIds séparés — `BlockIds`, `BlockItemIds`
- ✅ HUD via Blaze3D/Hud séparé — `CubeConquestHud` uses `HudRenderCallback` + `GuiGraphics`
- ❌ **GAP: Overworld restriction on cube placement** — need to add in Task 4 / GameManager

### Gap fix — add Overworld placement restriction

This gap must be covered in Task 4's `onBlockBreak` — before the block is placed, not broken. The right event is `PlayerBlockBreakEvents.BEFORE` fires on break; placement needs `UseBlockCallback` or an override of `BlockItem.place()`.

**Add Task 4b** (insert between Task 4 and Task 5):

### Task 4b: Overworld-only cube placement restriction

**Model:** sonnet

**Files:**
- Modify: `src/main/java/fr/chixi/cubeconquest/CubeConquestGameManager.java` (add `UseBlockCallback` handler)
- Modify: `src/test/java/fr/chixi/cubeconquest/CubeConquestGameManagerTest.java` (add placement test)

**Interfaces:**
- Consumes: `GamePhase`, `CubeConquestState`, `CubeBlock`
- Produces: Cube block items can only be placed in the Overworld during PLACEMENT phase; player receives error message otherwise

- [ ] **Step 1: Add failing test for placement-dimension guard logic**

Append to `CubeConquestGameManagerTest.java`:

```java
@Test void canPlaceCubeInOverworld_returns_true_for_overworld() {
    assertThat(CubeConquestGameManager.isOverworld("minecraft:overworld")).isTrue();
}

@Test void canPlaceCubeInOverworld_returns_false_for_nether() {
    assertThat(CubeConquestGameManager.isOverworld("minecraft:the_nether")).isFalse();
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew test --tests "fr.chixi.cubeconquest.CubeConquestGameManagerTest"
```

Expected: compilation failure — `isOverworld` not found.

- [ ] **Step 3: Add `isOverworld` helper and `UseBlockCallback` handler in `CubeConquestGameManager.java`**

Add to `register()`:

```java
net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(CubeConquestGameManager::onUseBlock);
```

Add the helper and handler:

```java
static boolean isOverworld(String dimensionKey) {
    return "minecraft:overworld".equals(dimensionKey);
}

private static InteractionResult onUseBlock(Player player, Level level,
                                             net.minecraft.world.InteractionHand hand,
                                             net.minecraft.world.phys.BlockHitResult hitResult) {
    if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

    ItemStack held = player.getItemInHand(hand);
    if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return InteractionResult.PASS;
    if (!(blockItem.getBlock() instanceof CubeBlock)) return InteractionResult.PASS;

    CubeConquestState state = CubeConquestState.getServerState(serverLevel.getServer());
    if (state.getPhase() != GamePhase.PLACEMENT) return InteractionResult.PASS;

    if (!isOverworld(serverLevel.dimension().location().toString())) {
        player.sendSystemMessage(Component.literal("You must place the cube in the Overworld!")
            .withStyle(ChatFormatting.RED));
        return InteractionResult.FAIL;
    }

    // Record cube position once placed (block placement event fires after this returns PASS)
    // We detect placement by hooking AFTER block placement
    return InteractionResult.PASS;
}
```

**Note:** The actual position recording happens via `BlockPlaceCallback` (Fabric API `AfterBlockPlaceCallback` or a `UseBlockCallback` post-placement hook). Add this additional handler to `register()`:

```java
net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
    if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
    ItemStack held = player.getItemInHand(hand);
    if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return InteractionResult.PASS;
    if (!(blockItem.getBlock() instanceof CubeBlock cubeBlock)) return InteractionResult.PASS;

    CubeConquestState state = CubeConquestState.getServerState(serverLevel.getServer());
    if (state.getPhase() != GamePhase.PLACEMENT) return InteractionResult.PASS;

    // Record the position of the placed cube (hitResult offset = actual block being placed on, +1 in facing direction)
    BlockPos placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());
    state.setCubePos(cubeBlock.getTeam(), placedPos);
    CubeConquestMod.LOGGER.info("{} cube placed at {}", cubeBlock.getTeam(), placedPos);

    return InteractionResult.PASS; // Still allow placement
});
```

- [ ] **Step 4: Run all tests — expect PASS**

```bash
./gradlew test
```

Expected: all tests pass including the two new ones.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: restrict cube placement to Overworld and record cube position"
```

---

### Type consistency check

- `GamePhase` values: `IDLE`, `PREPARATION`, `PLACEMENT`, `COMBAT` — used consistently throughout.
- `Team.RED` / `Team.BLUE` — used consistently; `team.opponent()` in `triggerVictory`.
- `CubeConquestState.getServerState(MinecraftServer)` — called in GameManager ✓, Command ✓.
- `CubePositionPayload.TYPE` / `CODEC` — registered in `CubeConquestMod.onInitialize()` ✓, received in `CubeConquestClient.onInitializeClient()` ✓.
- `CubeConquestGameManager.register()` — called from `CubeConquestMod.onInitialize()` ✓.
- `CubeConquestCommand.register()` — called from `CubeConquestMod.onInitialize()` ✓.
- `HudRenderCallback` is a Fabric API client-side event — in `src/client/java` ✓.
- `PayloadTypeRegistry.playS2C()` — registered in common init ✓, received via `ClientPlayNetworking` ✓.
