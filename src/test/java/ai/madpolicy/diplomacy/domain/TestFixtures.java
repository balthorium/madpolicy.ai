package ai.madpolicy.diplomacy.domain;

import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.nio.file.Path;

final class TestFixtures {
    private TestFixtures() {
    }

    static BoardIndex boardIndex() {
        BoardDocument board = new BoardCatalog().resolve("standard", 1);
        return new BoardIndex(board);
    }

    static GameStateIndex stateIndex(String path) {
        GameStateDocument state = TestYaml.load(Path.of(path), GameStateDocument.class);
        return new GameStateIndex(state);
    }
}
