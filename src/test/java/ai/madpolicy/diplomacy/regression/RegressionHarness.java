package ai.madpolicy.diplomacy.regression;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.io.AdjudicatedStateBuilder;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.TestYaml;
import ai.madpolicy.diplomacy.model.UnitState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class RegressionHarness {
    private static final Path REGRESSION_DIR = Path.of("metadata/regression");
    private static final Path BOARD_PATH = Path.of("metadata/boards/board.yaml");

    private RegressionHarness() {
    }

    public static RegressionSummary runAll() {
        BoardDocument board = TestYaml.load(BOARD_PATH, BoardDocument.class);
        MoveAdjudicator adjudicator = new MoveAdjudicator(new BoardIndex(board));
        AdjudicatedStateBuilder stateBuilder = new AdjudicatedStateBuilder();

        List<FailureDetail> failedCases = new ArrayList<>();
        try (Stream<Path> paths = Files.list(REGRESSION_DIR)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                .sorted()
                .forEach(path -> {
                    GameStateDocument expected = TestYaml.load(path, GameStateDocument.class);
                    if (!"move".equals(expected.phase().step())) {
                        return;
                    }
                    GameStateDocument input = normalize(expected);
                    GameStateDocument actual = stateBuilder.build(input, adjudicator.adjudicate(input));
                    String mismatch = firstMismatch(expected, actual);
                    if (mismatch != null) {
                        failedCases.add(new FailureDetail(path.getFileName().toString(), mismatch));
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate regression fixtures", e);
        }

        return new RegressionSummary(failedCases);
    }

    static GameStateDocument normalize(GameStateDocument expected) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : expected.nations().entrySet()) {
            List<UnitState> units = new ArrayList<>();
            for (UnitState unit : entry.getValue().units()) {
                OrderState order = unit.order();
                units.add(new UnitState(
                    unit.position(),
                    new OrderState(order.type(), "new", order.to(), order.from(), order.viaConvoy(), null),
                    null
                ));
            }
            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(nation.name(), nation.homes(), nation.controls(), units));
        }
        return new GameStateDocument(expected.kind(), expected.version(), expected.phase(), nations, List.of());
    }

    static String firstMismatch(GameStateDocument expected, GameStateDocument actual) {
        if (!Objects.equals(expected.kind(), actual.kind())) {
            return "kind";
        }
        if (expected.version() != actual.version()) {
            return "version";
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
            if (!Objects.equals(expectedNation.homes(), actualNation.homes())) {
                return nationId + ".homes";
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
