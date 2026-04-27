package ai.madpolicy.diplomacy.domain;

public enum OrderKind {
    HOLD,
    MOVE,
    SUPPORT,
    CONVOY;

    public static OrderKind fromWireValue(String value) {
        return switch (value) {
            case "hold" -> HOLD;
            case "move" -> MOVE;
            case "support" -> SUPPORT;
            case "convoy" -> CONVOY;
            default -> throw new IllegalArgumentException("Unknown order type: " + value);
        };
    }
}
