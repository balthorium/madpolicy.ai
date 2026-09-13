package ai.madpolicy.diplomacy.map;

import java.util.List;
import java.util.Map;

public record ProvinceLayout(
    Vec2 centroid,
    List<Vec2> polygon,
    String terrain,
    Map<String, Vec2> positionCentroids
) {}
