package ai.madpolicy.diplomacy.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import ai.madpolicy.diplomacy.web.YamlAdjudicationHttpClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class OfficiateRegressionTest {
    private static final Path MOVE_DIR = Path.of("src/test/resources/regression/officiate/move");
    private static final Path RETREAT_DIR = Path.of("src/test/resources/regression/officiate/retreat");
    private static final Path ADJUSTMENT_DIR = Path.of("src/test/resources/regression/officiate/adjustment");

    @Test
    void moveCorpusMatchesExpectedPreAdjudicatedRetreatState() throws IOException {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        assertCorpusMatchesExpectedNextState(MOVE_DIR);
    }

    @Test
    void retreatCorpusMatchesExpectedNextState() throws IOException {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        assertCorpusMatchesExpectedNextState(RETREAT_DIR);
    }

    @Test
    void adjustmentCorpusMatchesExpectedNextMoveState() throws IOException {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        assertCorpusMatchesExpectedNextState(ADJUSTMENT_DIR);
    }

    private static void assertCorpusMatchesExpectedNextState(Path directory) throws IOException {
        var requestedPaths = requestedPaths(directory);

        assertThat(requestedPaths).isNotEmpty();

        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        for (Path requestedPath : requestedPaths) {
            GameStateDocument requested = TestYaml.load(requestedPath, GameStateDocument.class);
            GameStateDocument expected = TestYaml.load(expectedPathFor(requestedPath), GameStateDocument.class);

            assertThat(client.officiate(requested))
                .describedAs("officiating %s", requestedPath.getFileName())
                .isEqualTo(expected);
        }
    }

    private static java.util.List<Path> requestedPaths(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return java.util.List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths
                .filter(path -> path.getFileName().toString().endsWith("_requested.yaml"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
    }

    private static Path expectedPathFor(Path requestedPath) {
        String fileName = requestedPath.getFileName().toString();
        if (!fileName.endsWith("_requested.yaml")) {
            throw new IllegalArgumentException("Expected fixture name to end with _requested.yaml: " + fileName);
        }
        return requestedPath.resolveSibling(fileName.replace("_requested.yaml", "_expected.yaml"));
    }
}
