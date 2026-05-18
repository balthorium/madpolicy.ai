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
    void resolvesBoardByInternalIdAndRevision() {
        writeBoard("standard", 3, "standard", 3);

        BoardDocument board = new BoardCatalog(tempDir).resolve("standard", 3);

        assertThat(board.id()).isEqualTo("standard");
        assertThat(board.revision()).isEqualTo(3);
    }

    @Test
    void failsWhenBoardRevisionIsMissing() {
        writeBoard("standard", 3, "standard", 3);

        assertThatThrownBy(() -> new BoardCatalog(tempDir).resolve("standard", 4))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("standard")
            .hasMessageContaining("4");
    }

    @Test
    void failsWhenDirectoryDoesNotMatchBoardId() {
        writeBoard("wrong-name", 3, "standard", 3);

        assertThatThrownBy(() -> new BoardCatalog(tempDir).resolve("wrong-name", 3))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not match board id");
    }

    @Test
    void failsWhenFilenameDoesNotMatchBoardRevision() {
        writeBoard("standard", 7, "standard", 3);

        assertThatThrownBy(() -> new BoardCatalog(tempDir).resolve("standard", 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not match board revision");
    }

    private void writeBoard(String directoryName, int fileRevision, String boardId, int boardRevision) {
        Path boardDir = tempDir.resolve(directoryName);
        String yaml = """
            kind: board
            schema: 1
            id: %s
            revision: %s
            provinces: {}
            adjacency: []
            """.formatted(boardId, boardRevision);
        try {
            java.nio.file.Files.createDirectories(boardDir);
            java.nio.file.Files.writeString(boardDir.resolve(fileRevision + ".yaml"), yaml);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
