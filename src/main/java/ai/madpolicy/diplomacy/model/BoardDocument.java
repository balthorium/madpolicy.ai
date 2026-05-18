package ai.madpolicy.diplomacy.model;

import java.util.List;
import java.util.Map;

public record BoardDocument(
    String kind,
    int schema,
    String id,
    int revision,
    Map<String, BoardProvince> provinces,
    List<List<String>> adjacency
) {}
