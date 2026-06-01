package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record GameStateDocument(
    String kind,
    int schema,
    int revision,
    String board,
    @JsonProperty("board_revision") int boardRevision,
    GameOptions options,
    GamePhase phase,
    Map<String, NationState> nations,
    List<String> standoffs
) {}
