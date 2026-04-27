package ai.madpolicy.diplomacy.model;

import java.util.List;

public record NationState(
    String name,
    List<String> homes,
    List<String> controls,
    List<UnitState> units
) {}
