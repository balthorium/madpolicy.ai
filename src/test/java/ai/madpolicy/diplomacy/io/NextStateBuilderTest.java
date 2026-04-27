package ai.madpolicy.diplomacy.io;

import static org.assertj.core.api.Assertions.assertThat;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.model.BoardDocument;
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
    void resetsOrdersAndAdvancesToRetreatStep() {
        var expected = TestYaml.load(Path.of("metadata/regression/test6.yaml"), GameStateDocument.class);
        var input = normalize(expected);
        BoardDocument board = TestYaml.load(Path.of("metadata/boards/board.yaml"), BoardDocument.class);
        var adjudicator = new MoveAdjudicator(new BoardIndex(board));
        var adjudicated = adjudicator.adjudicate(input);

        GameStateDocument nextState = new NextStateBuilder().build(input, adjudicated);

        assertThat(nextState.phase().step()).isEqualTo("retreat");
        assertThat(nextState.standoffs()).isEmpty();

        UnitState frenchMover = findUnit(nextState, "france", "lon_c").orElseThrow();
        assertThat(frenchMover.order().type()).isEqualTo("hold");
        assertThat(frenchMover.order().status()).isEqualTo("new");
        assertThat(frenchMover.order().comment()).isNull();
        assertThat(frenchMover.dislodgedBy()).isNull();

        UnitState dislodgedEnglishUnit = findUnit(nextState, "england", "lon_l").orElseThrow();
        assertThat(dislodgedEnglishUnit.order().type()).isEqualTo("hold");
        assertThat(dislodgedEnglishUnit.order().status()).isEqualTo("new");
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
                    new OrderState(order.type(), "new", order.to(), order.from(), order.viaConvoy(), null),
                    null
                ));
            }
            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(nation.name(), nation.homes(), nation.controls(), units));
        }
        return new GameStateDocument(expected.kind(), expected.version(), expected.phase(), nations, List.of());
    }
}
