package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.domain.NormalizedUnit;
import ai.madpolicy.diplomacy.domain.OrderKind;

public final class OrderValidator {
    private final BoardIndex board;

    public OrderValidator(BoardIndex board) {
        this.board = board;
    }

    public ValidationResult validateMove(String fromPosition, String toPosition, boolean viaConvoy) {
        if (fromPosition == null || toPosition == null) {
            return ValidationResult.illegal();
        }

        String fromProvince = board.provinceIdForPosition(fromPosition);
        String toProvince = board.provinceIdForPosition(toPosition);
        if (fromProvince == null || toProvince == null) {
            return ValidationResult.illegal();
        }

        if (!viaConvoy) {
            return board.areAdjacent(fromPosition, toPosition)
                ? ValidationResult.legal()
                : ValidationResult.illegal();
        }

        if (!board.isLandPosition(fromPosition) || !board.isLandPosition(toPosition)) {
            return ValidationResult.illegal();
        }

        if (!board.provinceHasCoast(fromProvince) || !board.provinceHasCoast(toProvince)) {
            return ValidationResult.illegal();
        }

        if (!board.hasPossibleConvoyRoute(fromPosition, toPosition)) {
            return ValidationResult.illegal();
        }

        return ValidationResult.legal();
    }

    public ValidationResult validateSupport(String supportPosition, String fromPosition, String toPosition) {
        if (supportPosition == null || fromPosition == null || toPosition == null) {
            return ValidationResult.illegal();
        }

        String supportProvince = board.provinceIdForPosition(supportPosition);
        String fromProvince = board.provinceIdForPosition(fromPosition);
        String toProvince = board.provinceIdForPosition(toPosition);
        if (supportProvince == null || fromProvince == null || toProvince == null) {
            return ValidationResult.illegal();
        }

        return board.canReachProvince(supportPosition, toProvince)
            ? ValidationResult.legal()
            : ValidationResult.illegal();
    }

    public ValidationResult validateConvoy(String fleetPosition, String fromPosition, String toPosition) {
        if (fleetPosition == null || fromPosition == null || toPosition == null) {
            return ValidationResult.illegal();
        }

        String fromProvince = board.provinceIdForPosition(fromPosition);
        String toProvince = board.provinceIdForPosition(toPosition);
        if (fromProvince == null || toProvince == null) {
            return ValidationResult.illegal();
        }

        if (!board.isSeaPosition(fleetPosition) || !board.isLandPosition(fromPosition) || !board.isLandPosition(toPosition)) {
            return ValidationResult.illegal();
        }

        if (!board.provinceHasCoast(fromProvince) || !board.provinceHasCoast(toProvince)) {
            return ValidationResult.illegal();
        }

        if (!board.convoyFleetCanParticipate(fleetPosition, fromPosition, toPosition)) {
            return ValidationResult.illegal();
        }

        return ValidationResult.legal();
    }

    public ValidationResult validateHold(String position) {
        return board.provinceIdForPosition(position) == null ? ValidationResult.illegal() : ValidationResult.legal();
    }

    public ValidationResult validate(NormalizedUnit unit) {
        return switch (unit.order().kind()) {
            case HOLD -> validateHold(unit.position());
            case MOVE -> validateMove(unit.position(), unit.order().to(), unit.order().viaConvoy());
            case SUPPORT -> validateSupport(unit.position(), unit.order().from(), unit.order().to());
            case CONVOY -> validateConvoy(unit.position(), unit.order().from(), unit.order().to());
        };
    }
}
