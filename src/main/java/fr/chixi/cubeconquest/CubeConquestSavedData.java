package fr.chixi.cubeconquest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/**
 * Minecraft persistence wrapper around CubeConquestState.
 * Extends SavedData and calls setDirty() via onDirty().
 */
public class CubeConquestSavedData extends SavedData {

    private final CubeConquestState state;
    private int tickCount = 0;

    public CubeConquestSavedData() {
        this.state = new CubeConquestState() {
            @Override protected void onDirty() { CubeConquestSavedData.this.setDirty(); }
        };
    }

    // ── Codec for persistence ──────────────────────────────────────────────

    private static final Codec<List<UUID>> UUID_LIST_CODEC =
        Codec.STRING.listOf().xmap(
            list -> list.stream().map(UUID::fromString).toList(),
            list -> list.stream().map(UUID::toString).toList()
        );

    private static final Codec<int[]> INT3_CODEC = Codec.INT.listOf().xmap(
        l -> l.size() >= 3 ? new int[]{l.get(0), l.get(1), l.get(2)} : new int[]{0, 0, 0}, // ponytail: corrupted save guard — default to origin rather than crash
        a -> List.of(a[0], a[1], a[2])
    );

    private static final Codec<CubeConquestSavedData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.fieldOf("phase").forGetter(s -> s.state.getPhase().name()),
        UUID_LIST_CODEC.fieldOf("redPlayers").forGetter(s -> new ArrayList<>(s.state.getPlayers(Team.RED))),
        UUID_LIST_CODEC.fieldOf("bluePlayers").forGetter(s -> new ArrayList<>(s.state.getPlayers(Team.BLUE))),
        Codec.STRING.optionalFieldOf("redPorteur").forGetter(s -> Optional.ofNullable(s.state.getPorteur(Team.RED)).map(UUID::toString)),
        Codec.STRING.optionalFieldOf("bluePorteur").forGetter(s -> Optional.ofNullable(s.state.getPorteur(Team.BLUE)).map(UUID::toString)),
        INT3_CODEC.optionalFieldOf("redCubePos").forGetter(s -> Optional.ofNullable(s.state.getCubePos(Team.RED))),
        INT3_CODEC.optionalFieldOf("blueCubePos").forGetter(s -> Optional.ofNullable(s.state.getCubePos(Team.BLUE))),
        Codec.INT.optionalFieldOf("tickCount", 0).forGetter(s -> s.tickCount),
        Codec.INT.optionalFieldOf("prepTicks", 200).forGetter(s -> s.state.getPreparationDurationTicks()),
        Codec.BOOL.optionalFieldOf("overworldOnly", false).forGetter(s -> s.state.isOverworldOnly()),
        Codec.BOOL.optionalFieldOf("waitForCountdown", true).forGetter(s -> s.state.isWaitForCountdown()),
        Codec.STRING.optionalFieldOf("redCubeDim").forGetter(s -> Optional.ofNullable(s.state.getCubeDimension(Team.RED))),
        Codec.STRING.optionalFieldOf("blueCubeDim").forGetter(s -> Optional.ofNullable(s.state.getCubeDimension(Team.BLUE)))
    ).apply(inst, (phase, red, blue, redP, blueP, redPos, bluePos, tc, prepTicks, owOnly, waitCD, redDim, blueDim) -> {
        CubeConquestSavedData d = new CubeConquestSavedData();
        try { d.state.setPhase(GamePhase.valueOf(phase)); }
        catch (IllegalArgumentException e) { d.state.setPhase(GamePhase.IDLE); }
        red.forEach(id -> d.state.addPlayer(Team.RED, id));
        blue.forEach(id -> d.state.addPlayer(Team.BLUE, id));
        d.state.setPorteur(Team.RED, redP.map(UUID::fromString).orElse(null));
        d.state.setPorteur(Team.BLUE, blueP.map(UUID::fromString).orElse(null));
        redPos.ifPresent(xyz -> d.state.setCubePos(Team.RED, xyz));
        bluePos.ifPresent(xyz -> d.state.setCubePos(Team.BLUE, xyz));
        d.tickCount = tc;
        d.state.setPreparationDurationTicks(prepTicks);
        d.state.setOverworldOnly(owOnly);
        d.state.setWaitForCountdown(waitCD);
        redDim.ifPresent(dim -> d.state.setCubeDimension(Team.RED, dim));
        blueDim.ifPresent(dim -> d.state.setCubeDimension(Team.BLUE, dim));
        return d;
    }));

    public static final SavedDataType<CubeConquestSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("cubeconquest", "game_state"),
        CubeConquestSavedData::new,
        CODEC,
        null // ponytail: null = no DataFixer migration — this mod has no legacy save format to upgrade
    );

    public static CubeConquestSavedData getServerState(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── Delegate to state ──────────────────────────────────────────────────

    public CubeConquestState getState() { return state; }

    // Convenience BlockPos accessors (MC-typed, not in plain CubeConquestState)
    public BlockPos getCubePos(Team team) {
        int[] xyz = state.getCubePos(team);
        return xyz == null ? null : new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    public void setCubePos(Team team, BlockPos pos) {
        state.setCubePos(team, pos == null ? null : new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    public int getTickCount() { return tickCount; }

    public void setTickCount(int value) {
        tickCount = value;
        setDirty();
    }
}
