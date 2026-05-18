package ai.madpolicy.diplomacy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YamlRoundTripTest {
    @Test
    void parsesBoardAndStateFixtures() {
        var board = TestYaml.load(Path.of("data/boards/standard/1.yaml"), BoardDocument.class);
        var state = TestYaml.load(Path.of("data/games/646EBC81-1090-4681-93E1-5688A6D49DF0/1901_spring_move.yaml"), GameStateDocument.class);

        assertThat(board.kind()).isEqualTo("board");
        assertThat(board.schema()).isEqualTo(1);
        assertThat(board.id()).isEqualTo("standard");
        assertThat(board.revision()).isEqualTo(1);
        assertThat(state.schema()).isEqualTo(1);
        assertThat(state.revision()).isEqualTo(1);
        assertThat(state.board()).isEqualTo("standard");
        assertThat(state.boardRevision()).isEqualTo(1);
        assertThat(state.phase().step()).isEqualTo("move");
    }

    @Test
    void parsesRegressionFixturesWithDislodgedBy() {
        var state = TestYaml.load(Path.of("src/test/resources/regression/test6.yaml"), GameStateDocument.class);

        assertThat(state.nations().get("england").units().getFirst().dislodgedBy()).isEqualTo("eng");
    }
}
