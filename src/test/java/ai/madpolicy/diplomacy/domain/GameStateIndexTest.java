package ai.madpolicy.diplomacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStateIndexTest {
    @Test
    void indexesUnitsByPositionAndNation() {
        GameStateIndex state = TestFixtures.stateIndex("data/games/646EBC81-1090-4681-93E1-5688A6D49DF0/1901_spring_move.yaml");

        assertThat(state.unitAt("lon_c")).isPresent();
        assertThat(state.unitAt("lon_c").orElseThrow().order().kind()).isEqualTo(OrderKind.HOLD);
    }
}
