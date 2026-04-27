package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.nio.file.Path;

final class TestFixtures {
    private TestFixtures() {
    }

    static OrderValidator validator() {
        BoardDocument board = TestYaml.load(Path.of("metadata/boards/board.yaml"), BoardDocument.class);
        return new OrderValidator(new BoardIndex(board));
    }
}
