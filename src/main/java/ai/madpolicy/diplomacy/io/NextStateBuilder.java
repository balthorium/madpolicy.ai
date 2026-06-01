package ai.madpolicy.diplomacy.io;

import ai.madpolicy.diplomacy.adjudication.AdjudicationResult;
import ai.madpolicy.diplomacy.adjudication.UnitResult;
import ai.madpolicy.diplomacy.domain.BoardIndex;
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
    private final BoardIndex board;

    public NextStateBuilder() {
        this(null);
    }

    public NextStateBuilder(BoardIndex board) {
        this.board = board;
    }

    public GameStateDocument build(GameStateDocument original, AdjudicationResult adjudication) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : original.nations().entrySet()) {
            List<UnitState> nextUnits = moveToRetreatUnitStates(adjudication, entry);

            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(
                nation.name(),
                nation.controls(),
                nextUnits
            ));
        }

        return new GameStateDocument(
            original.kind(),
            original.schema(),
            original.revision(),
            original.board(),
            original.boardRevision(),
            retreatPhase(original.phase()),
            nations,
            adjudication.standoffs().stream().sorted().toList()
        );
    }

    public GameStateDocument build(GameStateDocument adjudicated) {
        if ("retreat".equals(adjudicated.phase().step())) {
            return buildAfterRetreat(adjudicated);
        }
        if ("adjustment".equals(adjudicated.phase().step())) {
            return buildAfterAdjustment(adjudicated);
        }

        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : adjudicated.nations().entrySet()) {
            List<UnitState> nextUnits = moveToRetreatUnitStates(entry);

            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(
                nation.name(),
                nation.controls(),
                nextUnits
            ));
        }

        return new GameStateDocument(
            adjudicated.kind(),
            adjudicated.schema(),
            adjudicated.revision(),
            adjudicated.board(),
            adjudicated.boardRevision(),
            retreatPhase(adjudicated.phase()),
            nations,
            adjudicated.standoffs().stream().sorted().toList()
        );
    }

    private GameStateDocument buildAfterAdjustment(GameStateDocument adjudicated) {
        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : adjudicated.nations().entrySet()) {
            NationState nation = entry.getValue();
            nations.put(entry.getKey(), new NationState(
                nation.name(),
                nation.controls(),
                adjustmentToNextUnitStates(nation.units())
            ));
        }

        return new GameStateDocument(
            adjudicated.kind(),
            adjudicated.schema(),
            adjudicated.revision(),
            adjudicated.board(),
            adjudicated.boardRevision(),
            phaseAfterAdjustment(adjudicated.phase()),
            nations,
            List.of()
        );
    }

    private GameStateDocument buildAfterRetreat(GameStateDocument adjudicated) {
        Map<String, List<UnitState>> unitsByNation = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : adjudicated.nations().entrySet()) {
            unitsByNation.put(entry.getKey(), retreatToNextUnitStates(entry.getValue().units()));
        }

        Map<String, List<String>> controlsByNation = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : adjudicated.nations().entrySet()) {
            controlsByNation.put(entry.getKey(), new ArrayList<>(entry.getValue().controls()));
        }
        if ("fall".equals(adjudicated.phase().season()) && board != null) {
            updateControls(controlsByNation, unitsByNation);
        }

        Map<String, NationState> nations = new LinkedHashMap<>();
        for (Map.Entry<String, NationState> entry : adjudicated.nations().entrySet()) {
            nations.put(entry.getKey(), new NationState(
                entry.getValue().name(),
                controlsByNation.get(entry.getKey()),
                unitsByNation.get(entry.getKey())
            ));
        }

        return new GameStateDocument(
            adjudicated.kind(),
            adjudicated.schema(),
            adjudicated.revision(),
            adjudicated.board(),
            adjudicated.boardRevision(),
            phaseAfterRetreat(adjudicated.phase()),
            nations,
            List.of()
        );
    }

    private static GamePhase retreatPhase(GamePhase originalPhase) {
        return new GamePhase(
            originalPhase.season(),
            "retreat",
            originalPhase.year(),
            originalPhase.due()
        );
    }

    private static GamePhase phaseAfterRetreat(GamePhase originalPhase) {
        String nextStep = "fall".equals(originalPhase.season()) ? "adjustment" : "move";
        return new GamePhase(
            "fall",
            nextStep,
            originalPhase.year(),
            originalPhase.due()
        );
    }

    private static GamePhase phaseAfterAdjustment(GamePhase originalPhase) {
        return new GamePhase(
            "spring",
            "move",
            originalPhase.year() + 1,
            originalPhase.due()
        );
    }

    private static List<UnitState> moveToRetreatUnitStates(AdjudicationResult adjudication, Map.Entry<String, NationState> entry) {
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
                result != null && result.dislodgedBy() != null ? pendingDisbandOrder() : null,
                result == null ? null : result.dislodgedBy()
            ));
        }
        return nextUnits;
    }

    private static List<UnitState> moveToRetreatUnitStates(Map.Entry<String, NationState> entry) {
        List<UnitState> nextUnits = new ArrayList<>();
        for (UnitState unit : entry.getValue().units()) {
            String nextPosition = unit.position();
            if ("success".equals(unit.order().status())
                && "move".equals(unit.order().type())
                && unit.order().to() != null) {
                nextPosition = unit.order().to();
            }
            nextUnits.add(new UnitState(
                nextPosition,
                unit.dislodgedBy() == null ? null : pendingDisbandOrder(),
                unit.dislodgedBy()
            ));
        }
        return nextUnits;
    }

    private static List<UnitState> retreatToNextUnitStates(List<UnitState> adjudicatedUnits) {
        List<UnitState> nextUnits = new ArrayList<>();
        for (UnitState unit : adjudicatedUnits) {
            if (unit.dislodgedBy() != null) {
                if (unit.order() != null
                    && "retreat".equals(unit.order().type())
                    && "success".equals(unit.order().status())
                    && unit.order().to() != null) {
                    nextUnits.add(new UnitState(unit.order().to(), pendingHoldOrder(), null));
                }
                continue;
            }

            nextUnits.add(new UnitState(unit.position(), pendingHoldOrder(), null));
        }
        return nextUnits;
    }

    private static List<UnitState> adjustmentToNextUnitStates(List<UnitState> adjudicatedUnits) {
        List<UnitState> nextUnits = new ArrayList<>();
        for (UnitState unit : adjudicatedUnits) {
            OrderState order = unit.order();
            if (order == null) {
                nextUnits.add(new UnitState(unit.position(), pendingHoldOrder(), null));
                continue;
            }

            if ("build".equals(order.type())) {
                if ("success".equals(order.status())) {
                    nextUnits.add(new UnitState(unit.position(), pendingHoldOrder(), null));
                }
                continue;
            }

            if ("disband".equals(order.type()) && "success".equals(order.status())) {
                continue;
            }

            if ("failure".equals(order.status()) && "Civil disorder.".equals(order.comment())) {
                continue;
            }

            nextUnits.add(new UnitState(unit.position(), pendingHoldOrder(), null));
        }
        return nextUnits;
    }

    private void updateControls(Map<String, List<String>> controlsByNation, Map<String, List<UnitState>> unitsByNation) {
        for (Map.Entry<String, List<UnitState>> entry : unitsByNation.entrySet()) {
            String nationId = entry.getKey();
            for (UnitState unit : entry.getValue()) {
                String province = board.provinceIdForPosition(unit.position());
                if (province == null || !board.isSupplyCenter(province)) {
                    continue;
                }
                String currentController = controllerOf(controlsByNation, province);
                if (nationId.equals(currentController)) {
                    continue;
                }
                if (currentController != null) {
                    controlsByNation.get(currentController).remove(province);
                }
                controlsByNation.get(nationId).add(province);
            }
        }
    }

    private static String controllerOf(Map<String, List<String>> controlsByNation, String province) {
        for (Map.Entry<String, List<String>> entry : controlsByNation.entrySet()) {
            if (entry.getValue().contains(province)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static OrderState pendingHoldOrder() {
        return new OrderState("hold", "pending", null, null, null, null);
    }

    private static OrderState pendingDisbandOrder() {
        return new OrderState("disband", "pending", null, null, null, null);
    }
}
