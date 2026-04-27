package ai.madpolicy.diplomacy.model;

import java.util.List;
import java.util.Map;

public record GameStateDocument(
    String kind,
    int version,
    GamePhase phase,
    Map<String, NationState> nations,
    List<String> standoffs
) {}
