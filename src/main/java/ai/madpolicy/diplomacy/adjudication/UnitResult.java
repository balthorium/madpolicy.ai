package ai.madpolicy.diplomacy.adjudication;

public record UnitResult(
    String status,
    String comment,
    String dislodgedBy
) {}
