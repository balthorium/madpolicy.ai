package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationServiceTest {
    @Test
    void returnsBothArtifacts() {
        var result = new AdjudicationService().adjudicate(Path.of("metadata/states/1901sm.yaml"));

        assertThat(result.adjudicatedState()).isNotNull();
        assertThat(result.nextState()).isNotNull();
        assertThat(result.adjudicatedState().schema()).isEqualTo(1);
        assertThat(result.adjudicatedState().revision()).isEqualTo(1);
        assertThat(result.adjudicatedState().phase().step()).isEqualTo("move");
        assertThat(result.adjudicatedState().board()).isEqualTo("standard");
        assertThat(result.adjudicatedState().boardRevision()).isEqualTo(1);
        assertThat(result.nextState().schema()).isEqualTo(1);
        assertThat(result.nextState().revision()).isEqualTo(1);
        assertThat(result.nextState().phase().step()).isEqualTo("retreat");
        assertThat(result.nextState().board()).isEqualTo("standard");
        assertThat(result.nextState().boardRevision()).isEqualTo(1);
    }
}
