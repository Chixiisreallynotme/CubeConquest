package fr.chixi.cubeconquest;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Pure-Java helpers used by CubeConquestGameManagerEvents (MC-dependent) and unit tests.
 * No Minecraft imports — compiles on JDK 21.
 */
public class CubeConquestGameManager {

    // ponytail: stub — CubeConquestMod calls CubeConquestGameManagerEvents.register() directly
    public static void register() {}

    // ── Utilities (package-private for tests) ────────────────────────────

    static UUID pickRandom(List<UUID> players) {
        if (players.isEmpty()) throw new IllegalArgumentException("Cannot pick from empty list");
        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    static UUID pickReplacement(UUID old, List<UUID> others) {
        return pickReplacementOpt(old, others)
            .orElseThrow(() -> new IllegalArgumentException("No replacement available"));
    }

    static Optional<UUID> pickReplacementOpt(UUID old, List<UUID> candidates) {
        List<UUID> filtered = candidates.stream()
            .filter(id -> !id.equals(old))
            .collect(Collectors.toList());
        if (filtered.isEmpty()) return Optional.empty();
        return Optional.of(pickRandom(filtered));
    }

    // ponytail: pure string check; avoids MC ResourceLocation dependency in tests
    public static boolean isOverworld(String dimensionKey) {
        return "minecraft:overworld".equals(dimensionKey);
    }

    /**
     * Decrements all entries by 1, calls onTick for each active entry, removes entries at 0.
     * ponytail: pure Java, no MC imports — fully unit-testable
     */
    static void drainActionBarCountdown(Map<UUID, Integer> map,
                                        java.util.function.BiConsumer<UUID, Integer> onTick) {
        map.entrySet().removeIf(e -> {
            onTick.accept(e.getKey(), e.getValue());
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }

    /**
     * Forfeit passes when team has >= 1 player AND yes votes strictly exceed 50% of team online.
     * ponytail: strict majority required; ffVoteNo removed — was never used in computation
     */
    public static boolean isForfeitPassing(Set<UUID> ffYes, Set<UUID> teamOnline) {
        if (teamOnline.isEmpty()) return false;
        long yesVotes = teamOnline.stream().filter(ffYes::contains).count();
        // ponytail: strict majority — ffVoteNo removed (was never used in computation)
        return yesVotes * 2 > teamOnline.size();
    }

    /**
     * Returns true if drawVoters contains >= 50% of each team's online members.
     * Both teams must independently meet the threshold.
     * ponytail: integer math avoids floating point; redVotes * 2 >= redOnline.size() ≡ >= 50%
     */
    public static boolean isDrawThresholdMet(Set<UUID> drawVoters,
                                       Set<UUID> redOnline,
                                       Set<UUID> blueOnline) {
        if (redOnline.isEmpty() || blueOnline.isEmpty()) return false;
        long redVotes  = redOnline.stream().filter(drawVoters::contains).count();
        long blueVotes = blueOnline.stream().filter(drawVoters::contains).count();
        return redVotes * 2 >= redOnline.size() && blueVotes * 2 >= blueOnline.size();
    }
}
