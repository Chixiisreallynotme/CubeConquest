package fr.chixi.cubeconquest.client;

import fr.chixi.cubeconquest.Team;
import net.minecraft.core.BlockPos;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class TrackingCompassClientHandler {
    // ponytail: static state is fine — one client, one game at a time
    private static final Map<Team, BlockPos> cubePositions = new EnumMap<>(Team.class);
    private static final Map<Team, String> cubeDimensions = new EnumMap<>(Team.class);
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

    public static void updateDimension(Team team, Optional<String> dimension) {
        if (dimension.isPresent()) {
            cubeDimensions.put(team, dimension.get());
        } else {
            cubeDimensions.remove(team);
        }
    }

    public static Optional<BlockPos> getPosition(Team team) {
        return Optional.ofNullable(cubePositions.get(team));
    }

    public static Optional<String> getDimension(Team team) {
        return Optional.ofNullable(cubeDimensions.get(team));
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

    public static void clearTeam() {
        clientTeam = null;
    }

    public static void clear() {
        cubePositions.clear();
        cubeDimensions.clear();
        placementTicksRemaining = -1;
        clientTeam = null;
    }

    // ponytail: client-side decrement for smooth countdown — server corrects every 20 ticks
    public static void clientTick() {
        if (placementTicksRemaining > 0) placementTicksRemaining--;
    }
}
