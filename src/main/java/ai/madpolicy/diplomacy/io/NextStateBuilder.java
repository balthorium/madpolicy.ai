package ai.madpolicy.diplomacy.io;

import ai.madpolicy.diplomacy.adjudication.AdjudicationResult;
import ai.madpolicy.diplomacy.adjudication.UnitResult;
import ai.madpolicy.diplomacy.model.GamePhase;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NextStateBuilder {
    public GameStateDocument build(GameStateDocument original, AdjudicationResult adjudication) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : original.nations().entrySet()) {
            List<UnitState> nextUnits = new ArrayList<>();
            for (UnitState unit : entry.getValue().units()) {
                UnitResult result = adjudication.unitResult(unit.position());
                String nextPosition = unit.position();
                if (result != null
                    && "success".equals(result.status())
                    && "move".equals(unit.order().type())
                    && unit.order().to() != null) {
                    nextPosition = unit.order().to();
                }
                nextUnits.add(new UnitState(
                    nextPosition,
                    new OrderState("hold", "new", null, null, null, null),
                    result == null ? null : result.dislodgedBy()
                ));
            }

            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(
                nation.name(),
                nation.homes(),
                nation.controls(),
                nextUnits
            ));
        }

        GamePhase retreatPhase = new GamePhase(
            original.phase().season(),
            "retreat",
            original.phase().year(),
            original.phase().due()
        );

        return new GameStateDocument(
            original.kind(),
            original.version(),
            retreatPhase,
            nations,
            adjudication.standoffs().stream().sorted().toList()
        );
    }
}
