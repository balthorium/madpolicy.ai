package ai.madpolicy.diplomacy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YamlRoundTripTest {
    @Test
    void parsesBoardAndSeedFixtures() {
        var board = TestYaml.load(Path.of("data/boards/standard/1.yaml"), BoardDocument.class);
        var state = TestYaml.load(Path.of("data/seeds/standard/1.yaml"), GameStateDocument.class);

        assertThat(board.kind()).isEqualTo("board");
        assertThat(board.schema()).isEqualTo(1);
        assertThat(board.id()).isEqualTo("standard");
        assertThat(board.revision()).isEqualTo(1);
        assertThat(board.provinces().get("lon").isSupplyCenter()).isTrue();
        assertThat(board.provinces().get("lon").homeNation()).isEqualTo("england");
        assertThat(board.provinces().get("bel").isSupplyCenter()).isTrue();
        assertThat(board.provinces().get("bel").supplyCenter()).isEqualTo("neutral");
        assertThat(board.provinces().get("bel").homeNation()).isNull();
        assertThat(state.schema()).isEqualTo(1);
        assertThat(state.revision()).isEqualTo(1);
        assertThat(state.board()).isEqualTo("standard");
        assertThat(state.boardRevision()).isEqualTo(1);
        assertThat(state.phase().step()).isEqualTo("move");
        assertThat(state.nations().get("england").controls()).containsExactly("lon", "edi", "lvp");
    }

    @Test
    void parsesRegressionFixturesWithDislodgedBy() {
        var state = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test6_expected.yaml"), GameStateDocument.class);

        assertThat(state.nations().get("england").units().getFirst().dislodgedBy()).isEqualTo("eng");
    }

    @Test
    void defaultsMissingOrderStatusToPending() {
        var state = TestYaml.load("""
            kind: game_state
            schema: 1
            revision: 1
            board: standard
            board_revision: 1
            phase:
              season: spring
              step: move
              year: 1901
              started: 0
            nations:
              england:
                name: England
                controls: []
                units:
                - position: lon_l
                  order:
                    type: hold
            standoffs: []
            """, GameStateDocument.class);

        assertThat(state.nations().get("england").units().getFirst().order().status()).isEqualTo("pending");
    }
}
