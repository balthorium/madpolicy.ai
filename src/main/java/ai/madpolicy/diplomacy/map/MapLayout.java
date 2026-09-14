package ai.madpolicy.diplomacy.map;

import java.util.Map;
import java.util.Set;

public record MapLayout(
    int width,
    int height,
    Map<String, ProvinceLayout> provinces,
    Map<String, Set<String>> adjacencies    // provinceId -> set of adjacent provinceIds
) {}
