package ai.madpolicy.diplomacy.regression;

import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.TestYaml;
import ai.madpolicy.diplomacy.model.UnitState;
import ai.madpolicy.diplomacy.web.YamlAdjudicationHttpClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class RegressionHarness {
    private static final Path MOVE_REGRESSION_DIR = Path.of("src/test/resources/regression/adjudicate/move");
    private static final Path RETREAT_REGRESSION_DIR = Path.of("src/test/resources/regression/adjudicate/retreat");

    private RegressionHarness() {
    }

    public static RegressionSummary runAll() {
        return run(MOVE_REGRESSION_DIR, "move");
    }

    public static RegressionSummary runRetreat() {
        return run(RETREAT_REGRESSION_DIR, "retreat");
    }

    private static RegressionSummary run(Path regressionDir, String step) {
        List<FailureDetail> failedCases = new ArrayList<>();
        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        try (Stream<Path> paths = Files.list(regressionDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith("_expected.yaml"))
                .sorted()
                .forEach(expectedPath -> {
                    GameStateDocument expected = TestYaml.load(expectedPath, GameStateDocument.class);
                    if (!step.equals(expected.phase().step())) {
                        return;
                    }
                    Path requestedPath = requestedPathFor(expectedPath);
                    GameStateDocument input = TestYaml.load(requestedPath, GameStateDocument.class);
                    GameStateDocument actual = client.adjudicate(input);
                    String mismatch = firstMismatch(expected, actual);
                    if (mismatch != null) {
                        failedCases.add(new FailureDetail(expectedPath.getFileName().toString(), mismatch));
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate regression fixtures", e);
        }

        return new RegressionSummary(failedCases);
    }

    private static Path requestedPathFor(Path expectedPath) {
        String fileName = expectedPath.getFileName().toString();
        if (!fileName.endsWith("_expected.yaml")) {
            throw new IllegalArgumentException("Expected regression fixture name to end with _expected.yaml: " + fileName);
        }
        String requestedFileName = fileName.replace("_expected.yaml", "_requested.yaml");
        return expectedPath.resolveSibling(requestedFileName);
    }

    static String firstMismatch(GameStateDocument expected, GameStateDocument actual) {
        if (!Objects.equals(expected.kind(), actual.kind())) {
            return "kind";
        }
        if (expected.schema() != actual.schema()) {
            return "schema";
        }
        if (expected.revision() != actual.revision()) {
            return "revision";
        }
        if (!Objects.equals(expected.board(), actual.board())) {
            return "board";
        }
        if (expected.boardRevision() != actual.boardRevision()) {
            return "board_revision";
        }
        if (!Objects.equals(expected.phase(), actual.phase())) {
            return "phase";
        }
        if (!Objects.equals(expected.standoffs(), actual.standoffs())) {
            return "standoffs expected=" + expected.standoffs() + " actual=" + actual.standoffs();
        }
        if (!Objects.equals(expected.nations().keySet(), actual.nations().keySet())) {
            return "nation ids";
        }
        for (String nationId : expected.nations().keySet()) {
            NationState expectedNation = expected.nations().get(nationId);
            NationState actualNation = actual.nations().get(nationId);
            if (!Objects.equals(expectedNation.name(), actualNation.name())) {
                return nationId + ".name";
            }
            if (!Objects.equals(expectedNation.controls(), actualNation.controls())) {
                return nationId + ".controls";
            }
            if (expectedNation.units().size() != actualNation.units().size()) {
                return nationId + ".unit count";
            }
            for (int i = 0; i < expectedNation.units().size(); i++) {
                UnitState expectedUnit = expectedNation.units().get(i);
                UnitState actualUnit = actualNation.units().get(i);
                String prefix = nationId + ".units[" + i + "]";
                if (!Objects.equals(expectedUnit.position(), actualUnit.position())) {
                    return prefix + ".position expected=" + expectedUnit.position() + " actual=" + actualUnit.position();
                }
                if (!Objects.equals(expectedUnit.dislodgedBy(), actualUnit.dislodgedBy())) {
                    return prefix + ".dislodgedBy expected=" + expectedUnit.dislodgedBy() + " actual=" + actualUnit.dislodgedBy();
                }
                String orderMismatch = firstOrderMismatch(prefix + ".order", expectedUnit.order(), actualUnit.order());
                if (orderMismatch != null) {
                    return orderMismatch;
                }
            }
        }
        return null;
    }

    private static String firstOrderMismatch(String prefix, OrderState expected, OrderState actual) {
        if (expected == null || actual == null) {
            return Objects.equals(expected, actual)
                ? null
                : prefix + " expected=" + expected + " actual=" + actual;
        }
        if (!Objects.equals(expected.type(), actual.type())) {
            return prefix + ".type expected=" + expected.type() + " actual=" + actual.type();
        }
        if (!Objects.equals(expected.status(), actual.status())) {
            return prefix + ".status expected=" + expected.status() + " actual=" + actual.status();
        }
        if (!Objects.equals(expected.to(), actual.to())) {
            return prefix + ".to expected=" + expected.to() + " actual=" + actual.to();
        }
        if (!Objects.equals(expected.from(), actual.from())) {
            return prefix + ".from expected=" + expected.from() + " actual=" + actual.from();
        }
        if (!Objects.equals(expected.viaConvoy(), actual.viaConvoy())) {
            return prefix + ".viaConvoy expected=" + expected.viaConvoy() + " actual=" + actual.viaConvoy();
        }
        if (!Objects.equals(expected.comment(), actual.comment())) {
            return prefix + ".comment expected=" + expected.comment() + " actual=" + actual.comment();
        }
        return null;
    }

    public record FailureDetail(String fileName, String mismatch) {
    }

    public record RegressionSummary(List<FailureDetail> failedCases) {
        public List<String> failedCaseNames() {
            return failedCases.stream().map(FailureDetail::fileName).toList();
        }

        public Map<String, Long> mismatchHistogram() {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (FailureDetail failure : failedCases) {
                counts.merge(failure.mismatch(), 1L, Long::sum);
            }
            return counts;
        }
    }
}
