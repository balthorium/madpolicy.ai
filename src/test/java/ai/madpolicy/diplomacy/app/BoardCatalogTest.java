package ai.madpolicy.diplomacy.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.madpolicy.diplomacy.model.BoardDocument;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesBoardByInternalId() {
        writeBoard("standard.yaml", "standard");

        BoardDocument board = new BoardCatalog(tempDir).resolve("standard");

        assertThat(board.id()).isEqualTo("standard");
    }

    @Test
    void failsWhenBoardIdIsMissing() {
        writeBoard("standard.yaml", "standard");

        assertThatThrownBy(() -> new BoardCatalog(tempDir).resolve("missing"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void failsWhenFilenameDoesNotMatchBoardId() {
        writeBoard("wrong-name.yaml", "standard");

        assertThatThrownBy(() -> new BoardCatalog(tempDir).resolve("standard"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not match board id");
    }

    private void writeBoard(String fileName, String boardId) {
        String yaml = """
            kind: board
            version: 1
            id: %s
            provinces: {}
            adjacency: []
            """.formatted(boardId);
        try {
            java.nio.file.Files.writeString(tempDir.resolve(fileName), yaml);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
