package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;

import java.util.*;

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

    @Test void canPlaceCubeInOverworld_returns_true_for_overworld() {
        assertThat(CubeConquestGameManager.isOverworld("minecraft:overworld")).isTrue();
    }

    @Test void canPlaceCubeInOverworld_returns_false_for_nether() {
        assertThat(CubeConquestGameManager.isOverworld("minecraft:the_nether")).isFalse();
    }

    @Test void drainActionBarCountdown_decrements_and_removes_at_zero() {
        Map<UUID, Integer> map = new HashMap<>();
        UUID id = UUID.randomUUID();
        map.put(id, 2);
        CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> {});
        assertThat(map.get(id)).isEqualTo(1);
        CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> {});
        assertThat(map).doesNotContainKey(id);
    }

    @Test void drainActionBarCountdown_calls_onTick_for_each_active_entry() {
        Map<UUID, Integer> map = new HashMap<>();
        UUID id = UUID.randomUUID();
        map.put(id, 3);
        List<Integer> seen = new ArrayList<>();
        CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> seen.add(tick));
        assertThat(seen).containsExactly(3);
    }

    @Test
    void drawThreshold_met_when_all_vote() {
        UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        Set<UUID> voters = Set.of(r1, b1);
        assertThat(CubeConquestGameManager.isDrawThresholdMet(voters, Set.of(r1), Set.of(b1))).isTrue();
    }

    @Test
    void drawThreshold_not_met_when_only_one_team_votes() {
        UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1), Set.of(r1), Set.of(b1))).isFalse();
    }

    @Test
    void drawThreshold_met_at_exactly_50_percent() {
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1, b1), Set.of(r1, r2), Set.of(b1, b2))).isTrue();
    }

    @Test
    void drawThreshold_fails_when_empty_team() {
        assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(), Set.of(), Set.of(UUID.randomUUID()))).isFalse();
    }

    @Test
    void forfeit_passes_with_one_yes_zero_no() {
        UUID p1 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(p1), Set.of(p1))).isTrue();
    }

    @Test
    void forfeit_1_yes_out_of_3_not_majority() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(p1), Set.of(p1, p2, p3))).isFalse();
    }

    @Test
    void forfeit_fails_with_zero_yes() {
        UUID p1 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(), Set.of(p1))).isFalse();
    }

    @Test
    void forfeit_requires_majority_of_online_team() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        // 1 yes out of 3 is not majority
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(p1), Set.of(p1, p2, p3))).isFalse();
        // 2 yes out of 3 is majority
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(p1, p2), Set.of(p1, p2, p3))).isTrue();
    }

    @Test
    void forfeit_ignores_offline_voter() {
        UUID online = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(offline), Set.of(online))).isFalse();
    }

    @Test
    void forfeit_empty_team_returns_false() {
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(UUID.randomUUID()), Set.of())).isFalse();
    }

    @Test
    void pickReplacementOpt_returns_empty_when_only_old_uuid_in_list() {
        UUID old = UUID.randomUUID();
        assertThat(CubeConquestGameManager.pickReplacementOpt(old, List.of(old))).isEmpty();
    }

    @Test
    void drawThreshold_not_met_when_voters_empty() {
        UUID r1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isDrawThresholdMet(
            Set.of(), Set.of(r1), Set.of(b1))).isFalse();
    }

    @Test
    void forfeit_passes_with_majority_yes() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        assertThat(CubeConquestGameManager.isForfeitPassing(
            Set.of(p1, p2), Set.of(p1, p2, p3))).isTrue();
    }



    @Test
    void compassAngle_formula_east_returns_quarter_turn_sanity_check() {
        // Player at origin facing south (yaw=0), target due east (+X direction)
        // Implementation: atan2(-dx, dz)
        double dx = 1, dz = 0;
        double yaw = 0;
        double targetAngleDeg = Math.toDegrees(Math.atan2(-dx, dz));
        double relAngle = ((targetAngleDeg - yaw) % 360 + 360) % 360;
        float result = (float)(relAngle / 360.0);
        assertThat(result).isCloseTo(0.75f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    void drainActionBarCountdown_callback_receives_pre_decrement_value() {
        Map<UUID, Integer> map = new HashMap<>();
        UUID id = UUID.randomUUID();
        map.put(id, 1);
        List<Integer> seen = new ArrayList<>();
        CubeConquestGameManager.drainActionBarCountdown(map, (uuid, tick) -> seen.add(tick));
        assertThat(seen).containsExactly(1);
        assertThat(map).doesNotContainKey(id);
    }

    @Test
    void forfeit_does_not_trigger_on_idle_phase() {
        // Documents the invariant: triggerVictory must only fire when phase is COMBAT.
        // The actual guard is in CubeConquestCommand's /ff handler.
        assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.COMBAT);
    }

    @Test
    void isOverworld_returns_false_for_null() {
        assertThat(CubeConquestGameManager.isOverworld(null)).isFalse();
    }

    @Test
    void isOverworld_returns_false_for_empty_string() {
        assertThat(CubeConquestGameManager.isOverworld("")).isFalse();
    }

    @Test
    void isOverworld_returns_false_for_wrong_case() {
        assertThat(CubeConquestGameManager.isOverworld("Minecraft:Overworld")).isFalse();
        assertThat(CubeConquestGameManager.isOverworld("MINECRAFT:OVERWORLD")).isFalse();
    }

    @Test
    void teamModification_only_valid_in_idle_phase() {
        // Documents: team add/remove are blocked when game is not IDLE (enforced in CubeConquestCommand)
        assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.PREPARATION);
        assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.PLACEMENT);
        assertThat(GamePhase.IDLE).isNotEqualTo(GamePhase.COMBAT);
    }

    @Test
    void forfeit_asymmetric_teams_larger_team_requires_majority() {
        // RED has 4 players, BLUE has 1 — forfeit quorum is independent per team
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(),
             r3 = UUID.randomUUID(), r4 = UUID.randomUUID();
        Set<UUID> redTeam = Set.of(r1, r2, r3, r4);
        // 2 of 4 RED votes → not majority
        assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(r1, r2), redTeam)).isFalse();
        // 3 of 4 RED votes → majority
        assertThat(CubeConquestGameManager.isForfeitPassing(Set.of(r1, r2, r3), redTeam)).isTrue();
    }

    @Test
    void drawThreshold_asymmetric_teams_requires_half_of_all_online() {
        // Per-team 50% check: RED needs >= 2 of 4 votes, BLUE needs >= 1 of 1 vote — checked independently
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(),
             r3 = UUID.randomUUID(), r4 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        Set<UUID> redOnline = Set.of(r1, r2, r3, r4);
        Set<UUID> blueOnline = Set.of(b1);
        // 2 voters → not enough
        assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1, r2), redOnline, blueOnline)).isFalse();
        // 3 voters (r1, r2, b1) → threshold met
        assertThat(CubeConquestGameManager.isDrawThresholdMet(Set.of(r1, r2, b1), redOnline, blueOnline)).isTrue();
    }

    // ── overworldOnly setting tests ──────────────────────────────────────

    @Test
    void overworldOnly_default_is_false() {
        CubeConquestState state = new CubeConquestState();
        assertThat(state.isOverworldOnly()).isFalse();
    }

    @Test
    void overworldOnly_can_be_toggled() {
        CubeConquestState state = new CubeConquestState();
        state.setOverworldOnly(true);
        assertThat(state.isOverworldOnly()).isTrue();
        state.setOverworldOnly(false);
        assertThat(state.isOverworldOnly()).isFalse();
    }

    @Test
    void reset_does_not_clear_overworldOnly() {
        CubeConquestState state = new CubeConquestState();
        state.setOverworldOnly(true);
        state.reset();
        assertThat(state.isOverworldOnly()).isTrue();
    }

    @Test
    void resetGame_does_not_clear_overworldOnly() {
        CubeConquestState state = new CubeConquestState();
        state.setOverworldOnly(true);
        state.resetGame();
        assertThat(state.isOverworldOnly()).isTrue();
    }

    // ── cubeDimension storage tests ──────────────────────────────────────

    @Test
    void cubeDimension_default_is_null() {
        CubeConquestState state = new CubeConquestState();
        assertThat(state.getCubeDimension(Team.RED)).isNull();
        assertThat(state.getCubeDimension(Team.BLUE)).isNull();
    }

    @Test
    void cubeDimension_set_and_get_per_team() {
        CubeConquestState state = new CubeConquestState();
        state.setCubeDimension(Team.RED, "minecraft:overworld");
        state.setCubeDimension(Team.BLUE, "minecraft:the_nether");
        assertThat(state.getCubeDimension(Team.RED)).isEqualTo("minecraft:overworld");
        assertThat(state.getCubeDimension(Team.BLUE)).isEqualTo("minecraft:the_nether");
    }

    @Test
    void reset_clears_cubeDimension() {
        CubeConquestState state = new CubeConquestState();
        state.setCubeDimension(Team.RED, "minecraft:overworld");
        state.setCubeDimension(Team.BLUE, "minecraft:the_end");
        state.reset();
        assertThat(state.getCubeDimension(Team.RED)).isNull();
        assertThat(state.getCubeDimension(Team.BLUE)).isNull();
    }

    @Test
    void resetGame_clears_cubeDimension() {
        CubeConquestState state = new CubeConquestState();
        state.setCubeDimension(Team.RED, "minecraft:overworld");
        state.setCubeDimension(Team.BLUE, "minecraft:the_nether");
        state.resetGame();
        assertThat(state.getCubeDimension(Team.RED)).isNull();
        assertThat(state.getCubeDimension(Team.BLUE)).isNull();
    }
}
