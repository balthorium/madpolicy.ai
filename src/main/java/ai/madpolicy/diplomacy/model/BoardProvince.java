package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BoardProvince(
    String name,
    List<BoardPosition> positions,
    @JsonProperty("supply_center") Boolean supplyCenter
) {}
