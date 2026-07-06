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
