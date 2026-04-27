package ai.madpolicy.diplomacy.domain;

import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameStateIndex {
    private final Map<String, NormalizedUnit> unitsByPosition;
    private final List<NormalizedUnit> units;

    public GameStateIndex(GameStateDocument state) {
        this.unitsByPosition = new LinkedHashMap<>();
        this.units = new ArrayList<>();

        for (Map.Entry<String, NationState> entry : state.nations().entrySet()) {
            String nationId = entry.getKey();
            NationState nation = entry.getValue();
            for (UnitState unit : nation.units()) {
                NormalizedUnit normalized = new NormalizedUnit(
                    nationId,
                    nation.name(),
                    unit.position(),
                    new NormalizedOrder(
                        OrderKind.fromWireValue(unit.order().type()),
                        unit.order().status(),
                        unit.order().to(),
                        unit.order().from(),
                        Boolean.TRUE.equals(unit.order().viaConvoy()),
                        unit.order().comment()
                    ),
                    unit.dislodgedBy()
                );
                units.add(normalized);
                unitsByPosition.put(unit.position(), normalized);
            }
        }
    }

    public Optional<NormalizedUnit> unitAt(String positionId) {
        return Optional.ofNullable(unitsByPosition.get(positionId));
    }

    public Optional<NormalizedUnit> unitInProvince(String provinceId, BoardIndex board) {
        return units.stream()
            .filter(unit -> provinceId.equals(board.provinceIdForPosition(unit.position())))
            .findFirst();
    }

    public List<NormalizedUnit> allUnits() {
        return List.copyOf(units);
    }
}
