package ai.madpolicy.diplomacy.model;

import java.util.List;

public record NationState(
    String name,
    List<String> controls,
    List<UnitState> units
) {}
