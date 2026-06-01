package ai.madpolicy.diplomacy.io;

import static org.assertj.core.api.Assertions.assertThat;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.TestYaml;
import ai.madpolicy.diplomacy.model.UnitState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NextStateBuilderTest {
    @Test
    void buildsRetreatStateFromAdjudicatedMoveState() {
        var adjudicated = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test6_expected.yaml"), GameStateDocument.class);

        GameStateDocument nextState = new NextStateBuilder().build(adjudicated);

        assertThat(nextState.phase().step()).isEqualTo("retreat");
        assertThat(nextState.standoffs()).isEmpty();

        UnitState frenchMover = findUnit(nextState, "france", "lon_c").orElseThrow();
        assertThat(frenchMover.order()).isNull();
        assertThat(frenchMover.dislodgedBy()).isNull();

        UnitState frenchSupporter = findUnit(nextState, "france", "nth_s").orElseThrow();
        assertThat(frenchSupporter.order()).isNull();
        assertThat(frenchSupporter.dislodgedBy()).isNull();

        UnitState dislodgedEnglishUnit = findUnit(nextState, "england", "lon_l").orElseThrow();
        assertThat(dislodgedEnglishUnit.order().type()).isEqualTo("disband");
        assertThat(dislodgedEnglishUnit.order().status()).isEqualTo("pending");
        assertThat(dislodgedEnglishUnit.order().to()).isNull();
        assertThat(dislodgedEnglishUnit.order().from()).isNull();
        assertThat(dislodgedEnglishUnit.order().viaConvoy()).isNull();
        assertThat(dislodgedEnglishUnit.order().comment()).isNull();
        assertThat(dislodgedEnglishUnit.dislodgedBy()).isEqualTo("eng");

        UnitState nonDislodgedEnglishUnit = findUnit(nextState, "england", "wal_l").orElseThrow();
        assertThat(nonDislodgedEnglishUnit.order()).isNull();
        assertThat(nonDislodgedEnglishUnit.dislodgedBy()).isNull();
    }

    @Test
    void resetsOrdersAndAdvancesToRetreatStep() {
        var expected = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test6_expected.yaml"), GameStateDocument.class);
        var input = normalize(expected);
        var adjudicator = new MoveAdjudicator(new BoardIndex(new BoardCatalog().resolve(
            input.board(),
            input.boardRevision()
        )));
        var adjudicated = adjudicator.adjudicate(input);

        GameStateDocument nextState = new NextStateBuilder().build(input, adjudicated);

        assertThat(nextState.phase().step()).isEqualTo("retreat");
        assertThat(nextState.standoffs()).isEmpty();

        UnitState frenchMover = findUnit(nextState, "france", "lon_c").orElseThrow();
        assertThat(frenchMover.order()).isNull();
        assertThat(frenchMover.dislodgedBy()).isNull();

        UnitState dislodgedEnglishUnit = findUnit(nextState, "england", "lon_l").orElseThrow();
        assertThat(dislodgedEnglishUnit.order().type()).isEqualTo("disband");
        assertThat(dislodgedEnglishUnit.order().status()).isEqualTo("pending");
        assertThat(dislodgedEnglishUnit.order().to()).isNull();
        assertThat(dislodgedEnglishUnit.dislodgedBy()).isEqualTo("eng");
    }

    private Optional<UnitState> findUnit(GameStateDocument state, String nationId, String position) {
        return state.nations().get(nationId).units().stream()
            .filter(unit -> position.equals(unit.position()))
            .findFirst();
    }

    private GameStateDocument normalize(GameStateDocument expected) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : expected.nations().entrySet()) {
            List<UnitState> units = new ArrayList<>();
            for (UnitState unit : entry.getValue().units()) {
                OrderState order = unit.order();
                units.add(new UnitState(
                    unit.position(),
                    new OrderState(order.type(), "pending", order.to(), order.from(), order.viaConvoy(), null),
                    null
                ));
            }
            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(nation.name(), nation.controls(), units));
        }
        return new GameStateDocument(
            expected.kind(),
            expected.schema(),
            expected.revision(),
            expected.board(),
            expected.boardRevision(),
            expected.options(),
            expected.phase(),
            nations,
            List.of()
        );
    }
}
