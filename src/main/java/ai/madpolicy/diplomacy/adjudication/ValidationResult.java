package ai.madpolicy.diplomacy.adjudication;

public record ValidationResult(
    boolean isLegal,
    FailureComment failureComment
) {
    public static ValidationResult legal() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult illegal() {
        return new ValidationResult(false, FailureComment.ILLEGAL_ORDER);
    }
}
