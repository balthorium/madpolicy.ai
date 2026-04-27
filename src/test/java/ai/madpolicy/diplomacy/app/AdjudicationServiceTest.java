package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationServiceTest {
    @Test
    void returnsBothArtifacts() {
        var result = new AdjudicationService().adjudicate(
            Path.of("metadata/boards/board.yaml"),
            Path.of("metadata/states/1901sm.yaml")
        );

        assertThat(result.adjudicatedState()).isNotNull();
        assertThat(result.nextState()).isNotNull();
        assertThat(result.adjudicatedState().phase().step()).isEqualTo("move");
        assertThat(result.nextState().phase().step()).isEqualTo("retreat");
    }
}
