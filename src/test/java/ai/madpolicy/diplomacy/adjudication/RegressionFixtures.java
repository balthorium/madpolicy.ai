package ai.madpolicy.diplomacy.adjudication;

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

final class RegressionFixtures {
    private RegressionFixtures() {
    }

    static GameStateDocument loadRegression(String path) {
        return TestYaml.load(Path.of(path), GameStateDocument.class);
    }

    static MoveAdjudicator adjudicator() {
        return new MoveAdjudicator(new BoardIndex(new BoardCatalog().resolve("standard")));
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
        return new GameStateDocument(
            expected.kind(),
            expected.version(),
            expected.board(),
            expected.phase(),
            nations,
            List.of()
        );
    }
}
