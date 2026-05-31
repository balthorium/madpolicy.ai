package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RetreatAdjudicator {
    private static final String ILLEGAL_ORDER = "Illegal or useless order.";
    private static final String CONTESTED_RETREAT = "Two or more units retreating to same province.";

    private final BoardIndex board;

    public RetreatAdjudicator(BoardIndex board) {
        this.board = board;
    }

    public AdjudicationResult adjudicate(GameStateDocument document) {
        List<UnitState> units = allUnits(document);
        Set<String> occupiedProvinces = occupiedProvinces(units);
        Set<String> standoffProvinces = document.standoffs() == null
            ? Set.of()
            : Set.copyOf(document.standoffs());
        Map<String, List<UnitState>> legalRetreatsByDestination = new HashMap<>();

        for (UnitState unit : units) {
            if (isLegalRetreat(unit, occupiedProvinces, standoffProvinces)) {
                String destinationProvince = board.provinceIdForPosition(unit.order().to());
                legalRetreatsByDestination.computeIfAbsent(destinationProvince, ignored -> new ArrayList<>()).add(unit);
            }
        }

        Map<String, UnitResult> results = new LinkedHashMap<>();
        for (UnitState unit : units) {
            OrderState order = unit.order();
            if (order == null) {
                continue;
            }

            if ("retreat".equals(order.type())) {
                String destinationProvince = board.provinceIdForPosition(order.to());
                boolean contested = legalRetreatsByDestination.getOrDefault(destinationProvince, List.of()).size() > 1;
                boolean legal = isLegalRetreat(unit, occupiedProvinces, standoffProvinces);
                results.put(unit.position(), new UnitResult(
                    legal && !contested ? "success" : "failure",
                    contested ? CONTESTED_RETREAT : legal ? null : ILLEGAL_ORDER,
                    unit.dislodgedBy()
                ));
                continue;
            }

            if ("disband".equals(order.type())) {
                boolean legal = unit.dislodgedBy() != null;
                results.put(unit.position(), new UnitResult(
                    legal ? "success" : "failure",
                    legal ? null : ILLEGAL_ORDER,
                    unit.dislodgedBy()
                ));
                continue;
            }

            if ("hold".equals(order.type()) && unit.dislodgedBy() == null) {
                results.put(unit.position(), new UnitResult("success", null, null));
                continue;
            }

            results.put(unit.position(), new UnitResult("failure", ILLEGAL_ORDER, unit.dislodgedBy()));
        }

        return new AdjudicationResult(results, standoffProvinces);
    }

    private boolean isLegalRetreat(UnitState unit, Set<String> occupiedProvinces, Set<String> standoffProvinces) {
        OrderState order = unit.order();
        if (unit.dislodgedBy() == null || order == null || !"retreat".equals(order.type())) {
            return false;
        }
        if (order.to() == null || Boolean.TRUE.equals(order.viaConvoy())) {
            return false;
        }

        String originProvince = board.provinceIdForPosition(unit.position());
        String destinationProvince = board.provinceIdForPosition(order.to());
        String dislodgerProvince = provinceId(unit.dislodgedBy());
        if (originProvince == null || destinationProvince == null || dislodgerProvince == null) {
            return false;
        }
        if (Objects.equals(originProvince, destinationProvince)) {
            return false;
        }
        if (Objects.equals(destinationProvince, dislodgerProvince)) {
            return false;
        }
        if (occupiedProvinces.contains(destinationProvince) || standoffProvinces.contains(destinationProvince)) {
            return false;
        }

        return board.areAdjacent(unit.position(), order.to());
    }

    private Set<String> occupiedProvinces(List<UnitState> units) {
        Set<String> provinces = new HashSet<>();
        for (UnitState unit : units) {
            if (unit.dislodgedBy() != null) {
                continue;
            }
            String province = board.provinceIdForPosition(unit.position());
            if (province != null) {
                provinces.add(province);
            }
        }
        return provinces;
    }

    private String provinceId(String positionOrProvinceId) {
        String province = board.provinceIdForPosition(positionOrProvinceId);
        return province == null ? positionOrProvinceId : province;
    }

    private List<UnitState> allUnits(GameStateDocument document) {
        List<UnitState> units = new ArrayList<>();
        for (NationState nation : document.nations().values()) {
            units.addAll(nation.units());
        }
        return units;
    }
}
