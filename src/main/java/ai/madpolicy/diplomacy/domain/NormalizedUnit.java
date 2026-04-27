package ai.madpolicy.diplomacy.domain;

public record NormalizedUnit(
    String nationId,
    String nationName,
    String position,
    NormalizedOrder order,
    String dislodgedBy
) {}
