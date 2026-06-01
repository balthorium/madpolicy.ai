package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameOptions(
    @JsonProperty("game_id") String gameId,
    @JsonProperty("move_phase_minutes") int movePhaseMinutes,
    @JsonProperty("retreat_phase_minutes") int retreatPhaseMinutes,
    @JsonProperty("adjustment_phase_minutes") int adjustmentPhaseMinutes
) {}
