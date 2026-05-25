package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationServiceTest {
    @Test
    void returnsAdjudicatedState() {
        var result = new AdjudicationService().adjudicate(Path.of("data/games/646EBC81-1090-4681-93E1-5688A6D49DF0/1901_spring_move.yaml"));

        assertThat(result).isNotNull();
        assertThat(result.schema()).isEqualTo(1);
        assertThat(result.revision()).isEqualTo(1);
        assertThat(result.phase().step()).isEqualTo("move");
        assertThat(result.board()).isEqualTo("standard");
        assertThat(result.boardRevision()).isEqualTo(1);
    }
}
