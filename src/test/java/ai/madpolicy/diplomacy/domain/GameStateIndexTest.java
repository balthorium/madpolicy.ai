package ai.madpolicy.diplomacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStateIndexTest {
    @Test
    void indexesUnitsByPositionAndNation() {
        GameStateIndex state = TestFixtures.stateIndex("metadata/states/1901sm.yaml");

        assertThat(state.unitAt("lon_c")).isPresent();
        assertThat(state.unitAt("lon_c").orElseThrow().order().kind()).isEqualTo(OrderKind.HOLD);
    }
}
