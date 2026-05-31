package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;

import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationServiceTest {
    @Test
    void returnsAdjudicatedState() {
        GameStateDocument initialState = TestYaml.load(Path.of("data/seeds/standard/1.yaml"), GameStateDocument.class);
        var result = new AdjudicationService().adjudicate(initialState);

        assertThat(result).isNotNull();
        assertThat(result.schema()).isEqualTo(1);
        assertThat(result.revision()).isEqualTo(1);
        assertThat(result.phase().step()).isEqualTo("move");
        assertThat(result.board()).isEqualTo("standard");
        assertThat(result.boardRevision()).isEqualTo(1);
    }

    @Test
    void doesNotExposeFileBackedGameStateAdjudication() {
        assertThat(AdjudicationService.class.getDeclaredMethods())
            .noneMatch(AdjudicationServiceTest::isPathBasedAdjudicateMethod);
    }

    private static boolean isPathBasedAdjudicateMethod(Method method) {
        return "adjudicate".equals(method.getName())
            && method.getParameterCount() == 1
            && Path.class.equals(method.getParameterTypes()[0]);
    }
}
