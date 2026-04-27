package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnitState(
    String position,
    OrderState order,
    @JsonProperty("dislodged_by")
    String dislodgedBy
) {}
