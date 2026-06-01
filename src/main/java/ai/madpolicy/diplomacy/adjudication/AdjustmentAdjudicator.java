package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdjustmentAdjudicator {
    private static final String ILLEGAL_ORDER = "Illegal or useless order.";
    private static final String TOO_MANY_BUILD_ORDERS = "Too many build orders.";
    private static final String TOO_MANY_DISBAND_ORDERS = "Too many disband orders.";
    private static final String CIVIL_DISORDER = "Civil disorder.";

    private final BoardIndex board;

    public AdjustmentAdjudicator(BoardIndex board) {
        this.board = board;
    }

    public AdjudicationResult adjudicate(GameStateDocument document) {
        Map<String, UnitResult> results = new LinkedHashMap<>();
        Set<String> occupiedExistingProvinces = occupiedExistingProvinces(document);

        for (Map.Entry<String, NationState> entry : document.nations().entrySet()) {
            adjudicateNation(entry.getKey(), entry.getValue(), occupiedExistingProvinces, results);
        }

        return new AdjudicationResult(results, Set.of());
    }

    private void adjudicateNation(
        String nationId,
        NationState nation,
        Set<String> occupiedExistingProvinces,
        Map<String, UnitResult> results
    ) {
        List<AdjustmentCandidate> existingUnits = new ArrayList<>();
        List<AdjustmentCandidate> civilDisorderCandidates = new ArrayList<>();
        List<AdjustmentCandidate> legalBuilds = new ArrayList<>();
        List<AdjustmentCandidate> legalDisbands = new ArrayList<>();

        for (UnitState unit : nation.units()) {
            if (!isBuildOrder(unit)) {
                existingUnits.add(candidate(nationId, unit));
            }
        }

        int adjustment = nation.controls().size() - existingUnits.size();
        int buildAllowance = Math.max(adjustment, 0);
        int requiredDisbands = Math.max(-adjustment, 0);

        for (UnitState unit : nation.units()) {
            OrderState order = unit.order();
            if (order == null) {
                continue;
            }

            AdjustmentCandidate candidate = candidate(nationId, unit);
            boolean existingUnit = !isBuildOrder(unit);
            if (existingUnit) {
                civilDisorderCandidates.add(candidate);
            }

            switch (order.type()) {
                case "build" -> {
                    if (isLegalBuild(nationId, nation, unit, occupiedExistingProvinces)) {
                        legalBuilds.add(candidate);
                    } else {
                        results.put(unit.position(), failure(ILLEGAL_ORDER));
                    }
                }
                case "disband" -> {
                    if (existingUnit) {
                        legalDisbands.add(candidate);
                    } else {
                        results.put(unit.position(), failure(ILLEGAL_ORDER));
                    }
                }
                case "hold" -> results.put(unit.position(), success());
                default -> results.put(unit.position(), failure(ILLEGAL_ORDER));
            }
        }

        Set<AdjustmentCandidate> successfulBuilds = selectByAdjustmentPriority(legalBuilds, buildAllowance);
        for (AdjustmentCandidate build : legalBuilds) {
            results.put(build.position(), successfulBuilds.contains(build)
                ? success()
                : failure(TOO_MANY_BUILD_ORDERS));
        }

        Set<AdjustmentCandidate> successfulDisbands = selectByAdjustmentPriority(legalDisbands, requiredDisbands);
        for (AdjustmentCandidate disband : legalDisbands) {
            results.put(disband.position(), successfulDisbands.contains(disband)
                ? success()
                : failure(TOO_MANY_DISBAND_ORDERS));
        }

        int missingDisbands = requiredDisbands - successfulDisbands.size();
        if (missingDisbands > 0) {
            List<AdjustmentCandidate> candidates = civilDisorderCandidates.stream()
                .filter(candidate -> !successfulDisbands.contains(candidate))
                .sorted(ADJUSTMENT_PRIORITY)
                .toList();
            for (int i = 0; i < Math.min(missingDisbands, candidates.size()); i++) {
                results.put(candidates.get(i).position(), failure(CIVIL_DISORDER));
            }
        }
    }

    private Set<AdjustmentCandidate> selectByAdjustmentPriority(List<AdjustmentCandidate> candidates, int count) {
        if (count <= 0 || candidates.isEmpty()) {
            return Set.of();
        }

        List<AdjustmentCandidate> sorted = candidates.stream()
            .sorted(ADJUSTMENT_PRIORITY)
            .limit(count)
            .toList();
        return new HashSet<>(sorted);
    }

    private boolean isLegalBuild(
        String nationId,
        NationState nation,
        UnitState unit,
        Set<String> occupiedExistingProvinces
    ) {
        String provinceId = board.provinceIdForPosition(unit.position());
        if (provinceId == null) {
            return false;
        }
        if (!board.isLandPosition(unit.position()) && !board.isCoastPosition(unit.position())) {
            return false;
        }
        if (!nation.controls().contains(provinceId)) {
            return false;
        }
        if (!board.isHomeSupplyCenterForNation(provinceId, nationId)) {
            return false;
        }
        return !occupiedExistingProvinces.contains(provinceId);
    }

    private Set<String> occupiedExistingProvinces(GameStateDocument document) {
        Set<String> occupied = new HashSet<>();
        for (NationState nation : document.nations().values()) {
            for (UnitState unit : nation.units()) {
                if (isBuildOrder(unit)) {
                    continue;
                }
                String provinceId = board.provinceIdForPosition(unit.position());
                if (provinceId != null) {
                    occupied.add(provinceId);
                }
            }
        }
        return occupied;
    }

    private AdjustmentCandidate candidate(String nationId, UnitState unit) {
        String provinceId = board.provinceIdForPosition(unit.position());
        return new AdjustmentCandidate(
            unit.position(),
            provinceId,
            provinceId == null ? unit.position() : board.provinceName(provinceId),
            provinceId == null ? Integer.MAX_VALUE : board.distanceToNearestHomeSupplyCenter(provinceId, nationId),
            board.isSeaPosition(unit.position()) || board.isCoastPosition(unit.position())
        );
    }

    private static boolean isBuildOrder(UnitState unit) {
        return unit.order() != null && "build".equals(unit.order().type());
    }

    private static UnitResult success() {
        return new UnitResult("success", null, null);
    }

    private static UnitResult failure(String comment) {
        return new UnitResult("failure", comment, null);
    }

    private static final Comparator<AdjustmentCandidate> ADJUSTMENT_PRIORITY = Comparator
        .comparingInt(AdjustmentCandidate::distanceToNearestHomeSupplyCenter).reversed()
        .thenComparing(candidate -> candidate.fleet() ? 0 : 1)
        .thenComparing(AdjustmentCandidate::provinceName)
        .thenComparing(AdjustmentCandidate::position);

    private record AdjustmentCandidate(
        String position,
        String provinceId,
        String provinceName,
        int distanceToNearestHomeSupplyCenter,
        boolean fleet
    ) {
    }
}
