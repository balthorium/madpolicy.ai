package ai.madpolicy.diplomacy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YamlRoundTripTest {
    @Test
    void parsesBoardAndStateFixtures() {
        var board = TestYaml.load(Path.of("metadata/boards/board.yaml"), BoardDocument.class);
        var state = TestYaml.load(Path.of("metadata/states/1901sm.yaml"), GameStateDocument.class);

        assertThat(board.kind()).isEqualTo("board");
        assertThat(state.phase().step()).isEqualTo("move");
    }

    @Test
    void parsesRegressionFixturesWithDislodgedBy() {
        var state = TestYaml.load(Path.of("metadata/regression/test6.yaml"), GameStateDocument.class);

        assertThat(state.nations().get("england").units().getFirst().dislodgedBy()).isEqualTo("eng");
    }
}
