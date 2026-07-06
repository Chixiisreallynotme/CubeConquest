package fr.chixi.cubeconquest;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CubeConquestStateTest {

    @Test void addPlayer_moves_player_between_teams() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        state.addPlayer(Team.BLUE, id);
        assertThat(state.getPlayers(Team.RED)).doesNotContain(id);
        assertThat(state.getPlayers(Team.BLUE)).contains(id);
    }

    @Test void getTeamOf_returns_correct_team() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        assertThat(state.getTeamOf(id)).contains(Team.RED);
    }

    @Test void getTeamOf_returns_empty_for_unknown_uuid() {
        CubeConquestState state = new CubeConquestState();
        assertThat(state.getTeamOf(UUID.randomUUID())).isEmpty();
    }

    @Test void resetGame_preserves_rosters() {
        CubeConquestState state = new CubeConquestState();
        UUID red = UUID.randomUUID();
        UUID blue = UUID.randomUUID();
        state.addPlayer(Team.RED, red);
        state.addPlayer(Team.BLUE, blue);
        state.setPorteur(Team.RED, red);
        state.setCubePos(Team.RED, new int[]{1, 2, 3});
        state.resetGame();
        assertThat(state.getPlayers(Team.RED)).contains(red);
        assertThat(state.getPlayers(Team.BLUE)).contains(blue);
        assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
        assertThat(state.getPorteur(Team.RED)).isNull();
        assertThat(state.getPorteur(Team.BLUE)).isNull();
        assertThat(state.getCubePos(Team.RED)).isNull();
        assertThat(state.getCubePos(Team.BLUE)).isNull();
    }

    @Test void reset_clears_rosters() {
        CubeConquestState state = new CubeConquestState();
        state.addPlayer(Team.RED, UUID.randomUUID());
        state.reset();
        assertThat(state.getPlayers(Team.RED)).isEmpty();
    }

    @Test void setCubePos_stores_and_retrieves() {
        CubeConquestState state = new CubeConquestState();
        state.setCubePos(Team.RED, new int[]{10, 64, 20});
        assertThat(state.getCubePos(Team.RED)).containsExactly(10, 64, 20);
    }

    @Test void getCubePos_returns_defensive_copy() {
        CubeConquestState state = new CubeConquestState();
        state.setCubePos(Team.RED, new int[]{10, 64, 20});
        int[] copy = state.getCubePos(Team.RED);
        copy[0] = 999;
        assertThat(state.getCubePos(Team.RED)).containsExactly(10, 64, 20);
    }

    @Test void setCubePos_and_getCubePos_round_trip() {
        CubeConquestState state = new CubeConquestState();
        state.setCubePos(Team.RED, new int[]{10, 64, -5});
        assertThat(state.getCubePos(Team.RED)).containsExactly(10, 64, -5);
        assertThat(state.getCubePos(Team.BLUE)).isNull();
    }

    @Test void setCubePos_null_clears_position() {
        CubeConquestState state = new CubeConquestState();
        state.setCubePos(Team.RED, new int[]{1, 2, 3});
        state.setCubePos(Team.RED, null);
        assertThat(state.getCubePos(Team.RED)).isNull();
    }

    @Test void setPorteur_and_getPorteur_round_trip() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.setPorteur(Team.BLUE, id);
        assertThat(state.getPorteur(Team.BLUE)).isEqualTo(id);
        assertThat(state.getPorteur(Team.RED)).isNull();
    }

    @Test void setPhase_and_getPhase_round_trip() {
        CubeConquestState state = new CubeConquestState();
        for (GamePhase phase : GamePhase.values()) {
            state.setPhase(phase);
            assertThat(state.getPhase()).isEqualTo(phase);
        }
    }

    @Test void resetGame_clears_porteur_and_cubePos_preserves_rosters() {
        CubeConquestState state = new CubeConquestState();
        UUID id = UUID.randomUUID();
        state.addPlayer(Team.RED, id);
        state.setPorteur(Team.RED, id);
        state.setCubePos(Team.RED, new int[]{1, 2, 3});
        state.setPhase(GamePhase.COMBAT);
        state.resetGame();
        assertThat(state.getPorteur(Team.RED)).isNull();
        assertThat(state.getCubePos(Team.RED)).isNull();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IDLE);
        assertThat(state.getPlayers(Team.RED)).contains(id); // rosters preserved
    }
}
