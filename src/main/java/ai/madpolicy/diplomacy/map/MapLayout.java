package ai.madpolicy.diplomacy.map;

import java.util.Map;

public record MapLayout(
    int width,
    int height,
    Map<String, ProvinceLayout> provinces
) {}
