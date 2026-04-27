package ai.madpolicy.diplomacy.io;

import ai.madpolicy.diplomacy.adjudication.AdjudicationResult;
import ai.madpolicy.diplomacy.adjudication.UnitResult;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdjudicatedStateBuilder {
    public GameStateDocument build(GameStateDocument original, AdjudicationResult adjudication) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : original.nations().entrySet()) {
            List<UnitState> adjudicatedUnits = new ArrayList<>();
            for (UnitState unit : entry.getValue().units()) {
                UnitResult result = adjudication.unitResult(unit.position());
                adjudicatedUnits.add(new UnitState(
                    unit.position(),
                    buildOrder(unit.order(), result),
                    result == null ? null : result.dislodgedBy()
                ));
            }

            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(
                nation.name(),
                nation.homes(),
                nation.controls(),
                adjudicatedUnits
            ));
        }

        return new GameStateDocument(
            original.kind(),
            original.version(),
            original.phase(),
            nations,
            adjudication.standoffs().stream().sorted().toList()
        );
    }

    private OrderState buildOrder(OrderState originalOrder, UnitResult result) {
        return new OrderState(
            originalOrder.type(),
            result == null ? originalOrder.status() : result.status(),
            originalOrder.to(),
            originalOrder.from(),
            originalOrder.viaConvoy(),
            result == null ? originalOrder.comment() : result.comment()
        );
    }
}
