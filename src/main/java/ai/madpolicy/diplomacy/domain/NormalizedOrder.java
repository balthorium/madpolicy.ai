package ai.madpolicy.diplomacy.domain;

public record NormalizedOrder(
    OrderKind kind,
    String status,
    String to,
    String from,
    boolean viaConvoy,
    String comment
) {}
