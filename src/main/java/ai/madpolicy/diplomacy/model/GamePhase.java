package ai.madpolicy.diplomacy.model;

public record GamePhase(
    String season,
    String step,
    int year,
    int due
) {}
