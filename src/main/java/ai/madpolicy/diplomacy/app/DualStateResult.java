package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.model.GameStateDocument;

public record DualStateResult(
    GameStateDocument adjudicatedState,
    GameStateDocument nextState
) {
}
