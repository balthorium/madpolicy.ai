package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.domain.GameStateIndex;
import ai.madpolicy.diplomacy.domain.NormalizedUnit;
import ai.madpolicy.diplomacy.domain.OrderKind;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class MoveAdjudicator {
    private final BoardIndex board;
    private final OrderValidator validator;

    public MoveAdjudicator(BoardIndex board) {
        this.board = board;
        this.validator = new OrderValidator(board);
    }

    public AdjudicationResult adjudicate(GameStateDocument document) {
        GameStateIndex state = new GameStateIndex(document);
        Map<String, ValidationResult> validations = new LinkedHashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            validations.put(unit.position(), validator.validate(unit));
        }

        Map<String, String> dislodgedBy = new HashMap<>();
        Map<String, SupportEvaluation> supportEvaluations = Map.of();
        Map<String, ConvoyEvaluation> convoyEvaluations = Map.of();
        ResolutionSnapshot finalSnapshot = null;
        Set<String> paradoxicalConvoyMoves = Set.of();

        for (int restart = 0; restart < 3; restart++) {
            Set<String> dislodged = new HashSet<>();
            Map<String, Boolean> moveSuccess = new HashMap<>();
            Set<String> standoffs = new HashSet<>();
            Map<String, String> previousDislodgedBy = Map.of();
            Map<String, Boolean> previousConvoyRouteExists = Map.of();
            Map<IterationSignature, Integer> seenStates = new HashMap<>();
            List<ResolutionSnapshot> history = new ArrayList<>();
            boolean restarted = false;

            for (int i = 0; i < 20; i++) {
                supportEvaluations = evaluateSupports(state, validations, dislodged, previousConvoyRouteExists);
                convoyEvaluations = evaluateConvoys(state, validations, dislodged);

                ResolutionSnapshot snapshot = resolveMoves(
                    state,
                    validations,
                    supportEvaluations,
                    convoyEvaluations,
                    moveSuccess,
                    previousDislodgedBy,
                    paradoxicalConvoyMoves
                );
                finalSnapshot = snapshot;

                if (dislodged.equals(snapshot.dislodged())
                    && standoffs.equals(snapshot.standoffs())
                    && moveSuccess.equals(snapshot.moveSuccess())
                    && previousConvoyRouteExists.equals(snapshot.convoyRouteExists())) {
                    dislodgedBy = snapshot.dislodgedBy();
                    break;
                }

                IterationSignature signature = IterationSignature.from(snapshot);
                Integer cycleStart = seenStates.putIfAbsent(signature, history.size());
                history.add(snapshot);
                if (cycleStart != null) {
                    Set<String> oscillatingConvoys = findOscillatingConvoyMoves(history.subList(cycleStart, history.size()));
                    Set<String> newParadoxicalConvoys = new HashSet<>(paradoxicalConvoyMoves);
                    newParadoxicalConvoys.addAll(oscillatingConvoys);
                    if (!newParadoxicalConvoys.equals(paradoxicalConvoyMoves)) {
                        paradoxicalConvoyMoves = Set.copyOf(newParadoxicalConvoys);
                        restarted = true;
                        break;
                    }
                    dislodgedBy = snapshot.dislodgedBy();
                    break;
                }

                dislodged = snapshot.dislodged();
                standoffs = snapshot.standoffs();
                moveSuccess = snapshot.moveSuccess();
                dislodgedBy = snapshot.dislodgedBy();
                previousDislodgedBy = snapshot.dislodgedBy();
                previousConvoyRouteExists = snapshot.convoyRouteExists();
            }

            if (!restarted) {
                break;
            }
        }

        Map<String, Boolean> finalMoveSuccess = finalSnapshot == null ? Map.of() : finalSnapshot.moveSuccess();
        Set<String> finalDislodged = finalSnapshot == null ? Set.of() : finalSnapshot.dislodged();
        Set<String> finalStandoffs = finalSnapshot == null ? Set.of() : finalSnapshot.standoffs();
        Map<String, UnitResult> results = new LinkedHashMap<>();
        Set<String> successfulDestinationProvinces = new HashSet<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() == OrderKind.MOVE && finalMoveSuccess.getOrDefault(unit.position(), false)) {
                successfulDestinationProvinces.add(board.provinceIdForPosition(unit.order().to()));
            }
        }
        for (NormalizedUnit unit : state.allUnits()) {
            ValidationResult validation = validations.get(unit.position());
            boolean paradoxicalConvoyOrder = unit.order().kind() == OrderKind.CONVOY
                && paradoxicalConvoyMoves.size() > 1
                && paradoxicalConvoyMoves.contains(unit.order().from());
            String status = determineStatus(
                unit,
                validation,
                supportEvaluations.get(unit.position()),
                convoyEvaluations.get(unit.position()),
                finalMoveSuccess.getOrDefault(unit.position(), false),
                finalDislodged,
                paradoxicalConvoyOrder
            );
            FailureComment comment = determineComment(
                unit,
                validation,
                supportEvaluations.get(unit.position()),
                convoyEvaluations.get(unit.position()),
                finalMoveSuccess.getOrDefault(unit.position(), false),
                finalStandoffs,
                finalDislodged,
                dislodgedBy.get(unit.position()),
                successfulDestinationProvinces,
                finalSnapshot != null && finalSnapshot.defensiveBounces().contains(unit.position()),
                finalSnapshot != null && finalSnapshot.convoyRouteExists().getOrDefault(unit.position(), false),
                paradoxicalConvoyOrder
            );
            results.put(unit.position(), new UnitResult(
                status,
                comment == null ? null : comment.wireValue(),
                dislodgedBy.get(unit.position())
            ));
        }

        return new AdjudicationResult(results, Set.copyOf(finalStandoffs));
    }

    private Map<String, SupportEvaluation> evaluateSupports(
        GameStateIndex state,
        Map<String, ValidationResult> validations,
        Set<String> dislodged,
        Map<String, Boolean> convoyRouteExists
    ) {
        Map<String, SupportEvaluation> evaluations = new HashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() != OrderKind.SUPPORT) {
                continue;
            }

            ValidationResult validation = validations.get(unit.position());
            if (!validation.isLegal()) {
                evaluations.put(unit.position(), SupportEvaluation.forIllegal());
                continue;
            }

            Optional<NormalizedUnit> supported = state.unitAt(unit.order().from());
            if (supported.isEmpty()) {
                evaluations.put(unit.position(), SupportEvaluation.forNoUnit());
                continue;
            }

            boolean holdSupport = Objects.equals(unit.order().from(), unit.order().to());
            boolean matches = holdSupport
                ? supported.get().order().kind() == OrderKind.HOLD
                    || supported.get().order().kind() == OrderKind.SUPPORT
                    || supported.get().order().kind() == OrderKind.CONVOY
                : supported.get().order().kind() == OrderKind.MOVE
                    && Objects.equals(supported.get().position(), unit.order().from())
                    && Objects.equals(
                        board.provinceIdForPosition(supported.get().order().to()),
                        board.provinceIdForPosition(unit.order().to())
                    );

            if (!matches) {
                evaluations.put(unit.position(), SupportEvaluation.forMismatch());
                continue;
            }

            String targetProvince = board.provinceIdForPosition(unit.order().to());
            String supporterProvince = board.provinceIdForPosition(unit.position());
            List<NormalizedUnit> attacksOnSupporter = state.allUnits().stream()
                .filter(other -> other.order().kind() == OrderKind.MOVE)
                .filter(other -> validations.get(other.position()).isLegal())
                .filter(other -> !other.order().viaConvoy() || convoyRouteExists.getOrDefault(other.position(), false))
                .filter(other -> Objects.equals(board.provinceIdForPosition(other.order().to()), supporterProvince))
                .filter(other -> !Objects.equals(board.provinceIdForPosition(other.position()), targetProvince))
                .toList();
            boolean attackedFromElsewhere = attacksOnSupporter.stream()
                .anyMatch(other -> !isSingleRouteConvoySupportProtected(unit, other, state, validations));
            boolean protectedDislodgementOnly = dislodged.contains(unit.position())
                && !attackedFromElsewhere
                && attacksOnSupporter.stream().anyMatch(other -> isSingleRouteConvoySupportProtected(unit, other, state, validations));

            boolean cut = attackedFromElsewhere || dislodged.contains(unit.position()) && !protectedDislodgementOnly;
            evaluations.put(unit.position(), cut ? SupportEvaluation.forCut() : SupportEvaluation.forSuccess());
        }
        return evaluations;
    }

    private boolean isSingleRouteConvoySupportProtected(
        NormalizedUnit supportUnit,
        NormalizedUnit attacker,
        GameStateIndex state,
        Map<String, ValidationResult> validations
    ) {
        if (!attacker.order().viaConvoy()) {
            return false;
        }

        Set<String> soleRouteFleets = soleConvoyRouteFleets(attacker, state, validations);
        if (soleRouteFleets.isEmpty()) {
            return false;
        }

        boolean supportToHold = Objects.equals(supportUnit.order().from(), supportUnit.order().to());
        if (supportToHold) {
            return soleRouteFleets.contains(supportUnit.order().from());
        }

        return soleRouteFleets.stream()
            .map(board::provinceIdForPosition)
            .anyMatch(convoyProvince -> Objects.equals(convoyProvince, board.provinceIdForPosition(supportUnit.order().to())));
    }

    private Set<String> soleConvoyRouteFleets(
        NormalizedUnit attacker,
        GameStateIndex state,
        Map<String, ValidationResult> validations
    ) {
        List<String> matchingFleets = state.allUnits().stream()
            .filter(unit -> unit.order().kind() == OrderKind.CONVOY)
            .filter(unit -> validations.get(unit.position()).isLegal())
            .filter(unit -> Objects.equals(unit.order().from(), attacker.position()))
            .filter(unit -> Objects.equals(unit.order().to(), attacker.order().to()))
            .map(NormalizedUnit::position)
            .toList();
        if (matchingFleets.isEmpty()) {
            return Set.of();
        }

        String originProvince = board.provinceIdForPosition(attacker.position());
        String destinationProvince = board.provinceIdForPosition(attacker.order().to());
        Set<String> fleetSet = Set.copyOf(matchingFleets);
        List<List<String>> routes = new ArrayList<>();
        for (String fleet : matchingFleets) {
            if (!provinceTouchesFleet(originProvince, fleet)) {
                continue;
            }
            collectConvoyRoutes(fleet, destinationProvince, fleetSet, new ArrayList<>(), new HashSet<>(), routes);
            if (routes.size() > 1) {
                return Set.of();
            }
        }

        return routes.size() == 1 ? Set.copyOf(routes.getFirst()) : Set.of();
    }

    private void collectConvoyRoutes(
        String fleet,
        String destinationProvince,
        Set<String> fleetSet,
        List<String> path,
        Set<String> visited,
        List<List<String>> routes
    ) {
        path.add(fleet);
        visited.add(fleet);
        try {
            if (provinceTouchesFleet(destinationProvince, fleet)) {
                routes.add(List.copyOf(path));
                return;
            }

            for (String next : fleetSet) {
                if (visited.contains(next) || !board.areAdjacent(fleet, next)) {
                    continue;
                }
                collectConvoyRoutes(next, destinationProvince, fleetSet, path, visited, routes);
                if (routes.size() > 1) {
                    return;
                }
            }
        } finally {
            visited.remove(fleet);
            path.removeLast();
        }
    }

    private Map<String, ConvoyEvaluation> evaluateConvoys(
        GameStateIndex state,
        Map<String, ValidationResult> validations,
        Set<String> dislodged
    ) {
        Map<String, ConvoyEvaluation> evaluations = new HashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() != OrderKind.CONVOY) {
                continue;
            }

            ValidationResult validation = validations.get(unit.position());
            if (!validation.isLegal()) {
                evaluations.put(unit.position(), ConvoyEvaluation.forIllegal());
                continue;
            }

            Optional<NormalizedUnit> mover = state.unitAt(unit.order().from());
            if (mover.isEmpty()) {
                evaluations.put(unit.position(), ConvoyEvaluation.forNoArmy());
                continue;
            }

            boolean convoyingArmy = board.isLandPosition(mover.get().position())
                && mover.get().order().kind() == OrderKind.MOVE
                && mover.get().order().viaConvoy();
            if (!convoyingArmy) {
                evaluations.put(unit.position(), ConvoyEvaluation.forNoArmy());
                continue;
            }

            boolean matching = Objects.equals(mover.get().order().to(), unit.order().to());
            if (!matching) {
                evaluations.put(unit.position(), ConvoyEvaluation.forMismatch());
                continue;
            }

            evaluations.put(unit.position(), dislodged.contains(unit.position())
                ? ConvoyEvaluation.forDislodged()
                : ConvoyEvaluation.forSuccess());
        }
        return evaluations;
    }

    private ResolutionSnapshot resolveMoves(
        GameStateIndex state,
        Map<String, ValidationResult> validations,
        Map<String, SupportEvaluation> supportEvaluations,
        Map<String, ConvoyEvaluation> convoyEvaluations,
        Map<String, Boolean> previousMoveSuccess,
        Map<String, String> previousDislodgedBy,
        Set<String> paradoxicalConvoyMoves
    ) {
        Map<String, List<NormalizedUnit>> supportToMove = new HashMap<>();
        Map<String, Integer> supportToHold = new HashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() != OrderKind.SUPPORT) {
                continue;
            }
            SupportEvaluation evaluation = supportEvaluations.get(unit.position());
            if (evaluation == null || !evaluation.success()) {
                continue;
            }
            if (Objects.equals(unit.order().from(), unit.order().to())) {
                supportToHold.merge(unit.order().from(), 1, Integer::sum);
            } else {
                supportToMove.computeIfAbsent(unit.order().from() + "->" + unit.order().to(), ignored -> new ArrayList<>())
                    .add(unit);
            }
        }

        Map<String, Boolean> convoyRouteExists = new HashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() != OrderKind.MOVE || !unit.order().viaConvoy()) {
                continue;
            }
            convoyRouteExists.put(
                unit.position(),
                !paradoxicalConvoyMoves.contains(unit.position()) && hasConvoyRoute(unit, state, convoyEvaluations)
            );
        }

        Map<String, MoveCandidate> candidatesByMover = new HashMap<>();
        Map<String, List<MoveCandidate>> candidatesByDestinationProvince = new HashMap<>();
        for (NormalizedUnit unit : state.allUnits()) {
            if (unit.order().kind() != OrderKind.MOVE) {
                continue;
            }
            if (!validations.get(unit.position()).isLegal()) {
                continue;
            }
            if (unit.order().viaConvoy() && !convoyRouteExists.getOrDefault(unit.position(), false)) {
                continue;
            }
            String destinationProvince = board.provinceIdForPosition(unit.order().to());
            int attackStrength = 1 + supportToMove.getOrDefault(unit.position() + "->" + unit.order().to(), List.of()).size();
            MoveCandidate candidate = new MoveCandidate(unit, destinationProvince, attackStrength);
            candidatesByMover.put(unit.position(), candidate);
            candidatesByDestinationProvince.computeIfAbsent(destinationProvince, ignored -> new ArrayList<>()).add(candidate);
        }

        Map<String, Boolean> moveSuccess = new HashMap<>();
        Set<String> standoffs = new HashSet<>();
        Map<String, String> dislodgedBy = new HashMap<>();
        Set<String> defensiveBounces = new HashSet<>();
        Map<String, Boolean> successMemo = new HashMap<>();

        for (List<MoveCandidate> contenders : candidatesByDestinationProvince.values()) {
            List<MoveCandidate> effectiveContenders = contenders.stream()
                .filter(candidate -> canCandidateContestDestination(candidate, state, previousDislodgedBy))
                .toList();
            int topStrength = effectiveContenders.stream().mapToInt(MoveCandidate::attackStrength).max().orElse(0);
            List<MoveCandidate> strongest = effectiveContenders.stream()
                .filter(candidate -> candidate.attackStrength() == topStrength)
                .toList();

            if (strongest.size() > 1) {
                standoffs.add(strongest.getFirst().destinationProvince());
            }
        }

        for (MoveCandidate candidate : candidatesByMover.values()) {
            if (canMoveSucceed(
                candidate,
                candidatesByMover,
                candidatesByDestinationProvince,
                state,
                supportToHold,
                supportToMove,
                standoffs,
                previousDislodgedBy,
                successMemo,
                new ArrayList<>()
            )) {
                moveSuccess.put(candidate.mover().position(), true);
            }
        }

        for (MoveCandidate candidate : candidatesByMover.values()) {
            if (!moveSuccess.getOrDefault(candidate.mover().position(), false)) {
                Optional<NormalizedUnit> incumbent = state.unitInProvince(candidate.destinationProvince(), board);
                if (incumbent.isPresent()) {
                    NormalizedUnit defender = incumbent.get();
                    if (!moveSuccess.getOrDefault(defender.position(), false)
                        && !dislodgedBy.containsKey(defender.position())) {
                        int defenseStrength = defenseStrength(defender, supportToHold);
                        int attackStrength = effectiveAttackStrength(candidate, Optional.of(defender), supportToMove);
                        if (attackStrength == defenseStrength) {
                            defensiveBounces.add(candidate.mover().position());
                        }
                    }
                }
                continue;
            }

            Optional<NormalizedUnit> incumbent = state.unitInProvince(candidate.destinationProvince(), board);
            if (incumbent.isEmpty()) {
                continue;
            }

            NormalizedUnit defender = incumbent.get();
            if (moveSuccess.getOrDefault(defender.position(), false)) {
                continue;
            }

            int defenseStrength = defenseStrength(defender, supportToHold);
            int attackStrength = effectiveAttackStrength(candidate, Optional.of(defender), supportToMove);
            if (Objects.equals(defender.nationId(), candidate.mover().nationId())) {
                continue;
            }
            if (attackStrength > defenseStrength) {
                dislodgedBy.put(defender.position(), board.provinceIdForPosition(candidate.mover().position()));
            }
        }

        Set<String> dislodged = new HashSet<>(dislodgedBy.keySet());
        return new ResolutionSnapshot(moveSuccess, standoffs, dislodged, dislodgedBy, convoyRouteExists, defensiveBounces);
    }

    private Set<String> findOscillatingConvoyMoves(List<ResolutionSnapshot> cycle) {
        Set<String> convoyMoves = cycle.stream()
            .flatMap(snapshot -> snapshot.convoyRouteExists().keySet().stream())
            .collect(Collectors.toSet());

        Set<String> oscillating = new HashSet<>();
        for (String convoyMove : convoyMoves) {
            Set<Boolean> routeValues = cycle.stream()
                .map(snapshot -> snapshot.convoyRouteExists().getOrDefault(convoyMove, false))
                .collect(Collectors.toSet());
            Set<Boolean> moveValues = cycle.stream()
                .map(snapshot -> snapshot.moveSuccess().getOrDefault(convoyMove, false))
                .collect(Collectors.toSet());
            if (routeValues.size() > 1 || moveValues.size() > 1) {
                oscillating.add(convoyMove);
            }
        }
        return oscillating;
    }

    private boolean canMoveSucceed(
        MoveCandidate candidate,
        Map<String, MoveCandidate> candidatesByMover,
        Map<String, List<MoveCandidate>> candidatesByDestinationProvince,
        GameStateIndex state,
        Map<String, Integer> supportToHold,
        Map<String, List<NormalizedUnit>> supportToMove,
        Set<String> standoffs,
        Map<String, String> previousDislodgedBy,
        Map<String, Boolean> successMemo,
        List<String> path
    ) {
        String moverPosition = candidate.mover().position();
        Boolean cached = successMemo.get(moverPosition);
        if (cached != null) {
            return cached;
        }

        if (standoffs.contains(candidate.destinationProvince())) {
            successMemo.put(moverPosition, false);
            return false;
        }

        List<MoveCandidate> contenders = candidatesByDestinationProvince.getOrDefault(candidate.destinationProvince(), List.of()).stream()
            .filter(contender -> canCandidateContestDestination(contender, state, previousDislodgedBy))
            .toList();
        int topStrength = contenders.stream().mapToInt(MoveCandidate::attackStrength).max().orElse(0);
        List<MoveCandidate> strongest = contenders.stream()
            .filter(contender -> contender.attackStrength() == topStrength)
            .toList();
        if (strongest.size() != 1 || !Objects.equals(strongest.getFirst().mover().position(), moverPosition)) {
            successMemo.put(moverPosition, false);
            return false;
        }

        path.add(moverPosition);
        try {
            Optional<NormalizedUnit> incumbent = state.unitInProvince(candidate.destinationProvince(), board);
            if (incumbent.isEmpty()) {
                successMemo.put(moverPosition, true);
                return true;
            }

            NormalizedUnit defender = incumbent.get();
            MoveCandidate defenderMove = candidatesByMover.get(defender.position());
            if (defenderMove != null) {
                int existingIndex = path.indexOf(defender.position());
                if (existingIndex >= 0) {
                    boolean twoWayDirectSwap = path.size() == 2
                        && Objects.equals(defenderMove.destinationProvince(), board.provinceIdForPosition(moverPosition))
                        && !candidate.mover().order().viaConvoy()
                        && !defenderMove.mover().order().viaConvoy();
                    boolean success = !twoWayDirectSwap
                        || candidate.attackStrength() > defenderMove.attackStrength();
                    successMemo.put(moverPosition, success);
                    return success;
                }

                if (canMoveSucceed(
                    defenderMove,
                    candidatesByMover,
                    candidatesByDestinationProvince,
                    state,
                    supportToHold,
                    supportToMove,
                    standoffs,
                    previousDislodgedBy,
                    successMemo,
                    path
                )) {
                    boolean headToHead = Objects.equals(
                        defenderMove.destinationProvince(),
                        board.provinceIdForPosition(moverPosition)
                    );
                    boolean bothDirect = !candidate.mover().order().viaConvoy()
                        && !defenderMove.mover().order().viaConvoy();
                    if (headToHead && bothDirect) {
                        boolean success = candidate.attackStrength() > defenderMove.attackStrength();
                        successMemo.put(moverPosition, success);
                        return success;
                    }
                    successMemo.put(moverPosition, true);
                    return true;
                }
            }

            int defenseStrength = defenseStrength(defender, supportToHold);
            int attackStrength = effectiveAttackStrength(candidate, Optional.of(defender), supportToMove);
            boolean success = !Objects.equals(defender.nationId(), candidate.mover().nationId())
                && attackStrength > defenseStrength;
            successMemo.put(moverPosition, success);
            return success;
        } finally {
            path.removeLast();
        }
    }

    private int effectiveAttackStrength(
        MoveCandidate candidate,
        Optional<NormalizedUnit> defender,
        Map<String, List<NormalizedUnit>> supportToMove
    ) {
        List<NormalizedUnit> supporters = supportToMove.getOrDefault(
            candidate.mover().position() + "->" + candidate.mover().order().to(),
            List.of()
        );
        if (defender.isEmpty()) {
            return 1 + supporters.size();
        }

        String defenderNation = defender.get().nationId();
        long allowedSupport = supporters.stream()
            .filter(supporter -> !Objects.equals(supporter.nationId(), defenderNation))
            .count();
        return 1 + (int) allowedSupport;
    }

    private boolean canCandidateContestDestination(
        MoveCandidate candidate,
        GameStateIndex state,
        Map<String, String> previousDislodgedBy
    ) {
        String dislodgerOriginProvince = previousDislodgedBy.get(candidate.mover().position());
        if (!Objects.equals(dislodgerOriginProvince, candidate.destinationProvince())) {
            return true;
        }

        Optional<NormalizedUnit> dislodger = state.unitInProvince(dislodgerOriginProvince, board);
        if (dislodger.isEmpty() || dislodger.get().order().kind() != OrderKind.MOVE) {
            return false;
        }

        boolean attacksMoverProvince = Objects.equals(
            board.provinceIdForPosition(dislodger.get().order().to()),
            board.provinceIdForPosition(candidate.mover().position())
        );
        if (!attacksMoverProvince) {
            return false;
        }

        return dislodger.get().order().viaConvoy();
    }

    private int defenseStrength(NormalizedUnit defender, Map<String, Integer> supportToHold) {
        return 1 + (defender.order().kind() != OrderKind.MOVE
            ? supportToHold.getOrDefault(defender.position(), 0)
            : 0);
    }

    private boolean hasConvoyRoute(
        NormalizedUnit mover,
        GameStateIndex state,
        Map<String, ConvoyEvaluation> convoyEvaluations
    ) {
        String originProvince = board.provinceIdForPosition(mover.position());
        String destinationProvince = board.provinceIdForPosition(mover.order().to());

        Set<String> startingFleets = state.allUnits().stream()
            .filter(unit -> unit.order().kind() == OrderKind.CONVOY)
            .filter(unit -> Objects.equals(unit.order().from(), mover.position()))
            .filter(unit -> Objects.equals(unit.order().to(), mover.order().to()))
            .filter(unit -> convoyEvaluations.get(unit.position()) != null && convoyEvaluations.get(unit.position()).success())
            .filter(unit -> provinceTouchesFleet(originProvince, unit.position()))
            .map(NormalizedUnit::position)
            .collect(Collectors.toSet());

        if (startingFleets.isEmpty()) {
            return false;
        }

        Set<String> targetFleets = state.allUnits().stream()
            .filter(unit -> unit.order().kind() == OrderKind.CONVOY)
            .filter(unit -> Objects.equals(unit.order().from(), mover.position()))
            .filter(unit -> Objects.equals(unit.order().to(), mover.order().to()))
            .filter(unit -> convoyEvaluations.get(unit.position()) != null && convoyEvaluations.get(unit.position()).success())
            .filter(unit -> provinceTouchesFleet(destinationProvince, unit.position()))
            .map(NormalizedUnit::position)
            .collect(Collectors.toSet());

        ArrayDeque<String> queue = new ArrayDeque<>(startingFleets);
        Set<String> visited = new HashSet<>(startingFleets);
        while (!queue.isEmpty()) {
            String fleet = queue.removeFirst();
            if (targetFleets.contains(fleet)) {
                return true;
            }
            for (NormalizedUnit unit : state.allUnits()) {
                if (unit.order().kind() != OrderKind.CONVOY) {
                    continue;
                }
                if (!Objects.equals(unit.order().from(), mover.position()) || !Objects.equals(unit.order().to(), mover.order().to())) {
                    continue;
                }
                if (convoyEvaluations.get(unit.position()) == null || !convoyEvaluations.get(unit.position()).success()) {
                    continue;
                }
                if (!visited.contains(unit.position()) && board.areAdjacent(fleet, unit.position())) {
                    visited.add(unit.position());
                    queue.addLast(unit.position());
                }
            }
        }

        return false;
    }

    private boolean provinceTouchesFleet(String provinceId, String fleetPosition) {
        for (String provincePosition : board.positionsForProvince(provinceId)) {
            if (board.isCoastPosition(provincePosition) && board.areAdjacent(provincePosition, fleetPosition)) {
                return true;
            }
        }
        return false;
    }

    private FailureComment determineComment(
        NormalizedUnit unit,
        ValidationResult validation,
        SupportEvaluation supportEvaluation,
        ConvoyEvaluation convoyEvaluation,
        boolean moveSucceeded,
        Set<String> standoffs,
        Set<String> dislodged,
        String dislodgedBy,
        Set<String> successfulDestinationProvinces,
        boolean defensiveBounce,
        boolean convoyRouteExists,
        boolean paradoxicalConvoyOrder
    ) {
        if (!validation.isLegal()) {
            return FailureComment.ILLEGAL_ORDER;
        }

        return switch (unit.order().kind()) {
            case HOLD -> null;
            case SUPPORT -> supportFailureComment(supportEvaluation);
            case CONVOY -> convoyFailureComment(convoyEvaluation, dislodged.contains(unit.position()), paradoxicalConvoyOrder);
            case MOVE -> moveFailureComment(unit, moveSucceeded, standoffs, dislodgedBy, successfulDestinationProvinces, defensiveBounce, convoyRouteExists);
        };
    }

    private String determineStatus(
        NormalizedUnit unit,
        ValidationResult validation,
        SupportEvaluation supportEvaluation,
        ConvoyEvaluation convoyEvaluation,
        boolean moveSucceeded,
        Set<String> dislodged,
        boolean paradoxicalConvoyOrder
    ) {
        if (!validation.isLegal()) {
            return "failure";
        }

        return switch (unit.order().kind()) {
            case HOLD -> dislodged.contains(unit.position()) ? "failure" : "success";
            case MOVE -> moveSucceeded ? "success" : "failure";
            case SUPPORT -> supportEvaluation != null && supportEvaluation.success() ? "success" : "failure";
            case CONVOY -> paradoxicalConvoyOrder
                ? "failure"
                : convoyEvaluation != null && convoyEvaluation.success() ? "success" : "failure";
        };
    }

    private FailureComment supportFailureComment(SupportEvaluation evaluation) {
        if (evaluation == null || evaluation.success()) {
            return null;
        }
        if (evaluation.noUnit()) {
            return FailureComment.NO_UNIT_TO_SUPPORT;
        }
        if (evaluation.mismatch()) {
            return FailureComment.SUPPORTED_UNIT_DID_NOT_MAKE_CORRESPONDING_ORDER;
        }
        if (evaluation.cut()) {
            return FailureComment.SUPPORT_CUT;
        }
        return FailureComment.ILLEGAL_ORDER;
    }

    private FailureComment convoyFailureComment(ConvoyEvaluation evaluation, boolean dislodged, boolean paradoxicalConvoyOrder) {
        if (paradoxicalConvoyOrder) {
            return FailureComment.CONVOY_FAILED_DUE_TO_PARADOX_SZYKMAN_RULE;
        }
        if (evaluation == null) {
            return null;
        }
        if (evaluation.noArmy()) {
            return FailureComment.NO_ARMY_TO_CONVOY;
        }
        if (evaluation.mismatch()) {
            return FailureComment.CONVOYED_UNIT_DID_NOT_MAKE_CORRESPONDING_ORDER;
        }
        return null;
    }

    private FailureComment moveFailureComment(
        NormalizedUnit unit,
        boolean moveSucceeded,
        Set<String> standoffs,
        String dislodgedBy,
        Set<String> successfulDestinationProvinces,
        boolean defensiveBounce,
        boolean convoyRouteExists
    ) {
        if (moveSucceeded) {
            return null;
        }
        if (unit.order().viaConvoy() && !convoyRouteExists) {
            return FailureComment.CONVOY_FAILED;
        }
        String destinationProvince = board.provinceIdForPosition(unit.order().to());
        if (standoffs.contains(destinationProvince)) {
            return FailureComment.STANDOFF;
        }
        if (successfulDestinationProvinces.contains(destinationProvince)) {
            return FailureComment.OVERWHELMED;
        }
        if (defensiveBounce) {
            return FailureComment.STANDOFF;
        }
        return dislodgedBy != null ? FailureComment.OVERWHELMED : FailureComment.STANDOFF;
    }

    private record MoveCandidate(NormalizedUnit mover, String destinationProvince, int attackStrength) {
    }

    private record SupportEvaluation(boolean success, boolean noUnit, boolean mismatch, boolean cut) {
        static SupportEvaluation forIllegal() {
            return new SupportEvaluation(false, false, false, false);
        }

        static SupportEvaluation forSuccess() {
            return new SupportEvaluation(true, false, false, false);
        }

        static SupportEvaluation forNoUnit() {
            return new SupportEvaluation(false, true, false, false);
        }

        static SupportEvaluation forMismatch() {
            return new SupportEvaluation(false, false, true, false);
        }

        static SupportEvaluation forCut() {
            return new SupportEvaluation(false, false, false, true);
        }
    }

    private record ConvoyEvaluation(boolean success, boolean noArmy, boolean mismatch) {
        static ConvoyEvaluation forIllegal() {
            return new ConvoyEvaluation(false, false, false);
        }

        static ConvoyEvaluation forSuccess() {
            return new ConvoyEvaluation(true, false, false);
        }

        static ConvoyEvaluation forNoArmy() {
            return new ConvoyEvaluation(false, true, false);
        }

        static ConvoyEvaluation forMismatch() {
            return new ConvoyEvaluation(false, false, true);
        }

        static ConvoyEvaluation forDislodged() {
            return new ConvoyEvaluation(false, false, false);
        }
    }

    private record ResolutionSnapshot(
        Map<String, Boolean> moveSuccess,
        Set<String> standoffs,
        Set<String> dislodged,
        Map<String, String> dislodgedBy,
        Map<String, Boolean> convoyRouteExists,
        Set<String> defensiveBounces
    ) {
    }

    private record IterationSignature(
        Map<String, Boolean> moveSuccess,
        Set<String> standoffs,
        Set<String> dislodged,
        Map<String, Boolean> convoyRouteExists
    ) {
        static IterationSignature from(ResolutionSnapshot snapshot) {
            return new IterationSignature(
                Map.copyOf(snapshot.moveSuccess()),
                Set.copyOf(snapshot.standoffs()),
                Set.copyOf(snapshot.dislodged()),
                Map.copyOf(snapshot.convoyRouteExists())
            );
        }
    }
}
