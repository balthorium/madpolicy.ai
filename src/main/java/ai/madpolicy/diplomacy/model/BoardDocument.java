package ai.madpolicy.diplomacy.model;

import java.util.List;
import java.util.Map;

public record BoardDocument(
    String kind,
    int version,
    String id,
    Map<String, BoardProvince> provinces,
    List<List<String>> adjacency
) {}
