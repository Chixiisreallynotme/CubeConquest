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
