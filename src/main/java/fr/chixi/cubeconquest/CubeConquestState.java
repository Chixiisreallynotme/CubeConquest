package fr.chixi.cubeconquest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure-Java game state — no Minecraft imports.
 * Minecraft persistence is handled by CubeConquestSavedData which wraps this.
 */
public class CubeConquestState {

    private GamePhase phase = GamePhase.IDLE;
    private final Set<UUID> redPlayers = new HashSet<>();
    private final Set<UUID> bluePlayers = new HashSet<>();
    private UUID redPorteur = null;
    private UUID bluePorteur = null;

    // BlockPos stored as int[3] to avoid MC dependency here; CubeConquestSavedData exposes typed accessors
    private int[] redCubePos = null;
    private int[] blueCubePos = null;
    private int preparationDurationTicks = 200; // default 10s
    private boolean overworldOnly = false; // server config — not reset per match
    private boolean waitForCountdown = true; // server config — not reset per match
    private String redCubeDimension = null;
    private String blueCubeDimension = null;

    public CubeConquestState() {}

    // ── Phase ──────────────────────────────────────────────────────────────

    public GamePhase getPhase() { return phase; }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
        onDirty();
    }

    public int getPreparationDurationTicks() { return preparationDurationTicks; }

    public void setPreparationDurationTicks(int ticks) {
        this.preparationDurationTicks = ticks;
        onDirty();
    }

    public boolean isOverworldOnly() { return overworldOnly; }

    public void setOverworldOnly(boolean overworldOnly) {
        this.overworldOnly = overworldOnly;
        onDirty();
    }

    public boolean isWaitForCountdown() { return waitForCountdown; }

    public void setWaitForCountdown(boolean waitForCountdown) {
        this.waitForCountdown = waitForCountdown;
        onDirty();
    }

    // ── Players ────────────────────────────────────────────────────────────

    public Set<UUID> getPlayers(Team team) {
        return Set.copyOf(team == Team.RED ? redPlayers : bluePlayers);
    }

    public void addPlayer(Team team, UUID id) {
        removePlayer(id); // ensure player is in at most one team
        (team == Team.RED ? redPlayers : bluePlayers).add(id);
        onDirty();
    }

    public void removePlayer(UUID id) {
        redPlayers.remove(id);
        bluePlayers.remove(id);
        onDirty();
    }

    public Optional<Team> getTeamOf(UUID id) {
        if (redPlayers.contains(id)) return Optional.of(Team.RED);
        if (bluePlayers.contains(id)) return Optional.of(Team.BLUE);
        return Optional.empty();
    }

    // ── Porteur ────────────────────────────────────────────────────────────

    public UUID getPorteur(Team team) { return team == Team.RED ? redPorteur : bluePorteur; }

    public void setPorteur(Team team, UUID id) {
        if (team == Team.RED) redPorteur = id; else bluePorteur = id;
        onDirty();
    }

    // ── Cube position (raw coords for MC-free storage) ────────────────────

    public int[] getCubePos(Team team) {
        int[] arr = team == Team.RED ? redCubePos : blueCubePos;
        return arr == null ? null : Arrays.copyOf(arr, 3);
    }

    public void setCubePos(Team team, int[] xyz) {
        // Fix 4: defensive copy so callers can't mutate stored state through the array reference
        if (team == Team.RED) {
            this.redCubePos = xyz == null ? null : Arrays.copyOf(xyz, 3);
        } else {
            this.blueCubePos = xyz == null ? null : Arrays.copyOf(xyz, 3);
        }
        onDirty();
    }

    // ── Cube dimension (for multi-dimension placement) ────────────────────

    public String getCubeDimension(Team team) {
        return team == Team.RED ? redCubeDimension : blueCubeDimension;
    }

    public void setCubeDimension(Team team, String dimension) {
        if (team == Team.RED) redCubeDimension = dimension; else blueCubeDimension = dimension;
        onDirty();
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    public void reset() {
        phase = GamePhase.IDLE;
        redPlayers.clear(); bluePlayers.clear();
        redPorteur = null; bluePorteur = null;
        redCubePos = null; blueCubePos = null;
        redCubeDimension = null; blueCubeDimension = null;
        // overworldOnly and waitForCountdown are intentionally NOT reset — they are server configs, not per-match state
        onDirty();
    }

    /**
     * Resets per-match state after a game ends, preserving team rosters for rematch.
     * Use after triggerVictory/triggerDraw. Use reset() only for full teardown (/stop).
     */
    public void resetGame() {
        phase = GamePhase.IDLE;
        redPorteur = null; bluePorteur = null;
        redCubePos = null; blueCubePos = null;
        redCubeDimension = null; blueCubeDimension = null;
        // overworldOnly and waitForCountdown are intentionally NOT reset — they are server configs, not per-match state
        onDirty();
    }

    // ── Dirty hook (overridden by CubeConquestSavedData to call setDirty) ──

    // ponytail: no-op here; CubeConquestSavedData overrides to call SavedData.setDirty()
    protected void onDirty() {}
}
