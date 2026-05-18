package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.model.BoardDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class BoardCatalog {
    private static final Path DEFAULT_BOARD_DIR = Path.of("metadata/boards");

    private final Path boardDirectory;
    private final ObjectMapper yamlMapper;

    public BoardCatalog() {
        this(DEFAULT_BOARD_DIR);
    }

    public BoardCatalog(Path boardDirectory) {
        this.boardDirectory = boardDirectory;
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    }

    public BoardDocument resolve(String boardId) {
        Map<String, BoardDocument> boards = loadBoards();
        BoardDocument board = boards.get(boardId);
        if (board == null) {
            throw new IllegalStateException("No board found for board id: " + boardId);
        }
        return board;
    }

    private Map<String, BoardDocument> loadBoards() {
        Map<String, BoardDocument> boards = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(boardDirectory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                .sorted()
                .forEach(path -> {
                    BoardDocument board = load(path);
                    BoardDocument previous = boards.putIfAbsent(board.id(), board);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate board id: " + board.id());
                    }
                    String fileStem = stripYamlExtension(path.getFileName().toString());
                    if (!fileStem.equals(board.id())) {
                        throw new IllegalStateException(
                            "Board filename '" + fileStem + "' does not match board id '" + board.id() + "'"
                        );
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read boards from " + boardDirectory, e);
        }
        return boards;
    }

    private BoardDocument load(Path path) {
        try {
            return yamlMapper.readValue(path.toFile(), BoardDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML: " + path, e);
        }
    }

    private String stripYamlExtension(String fileName) {
        return fileName.endsWith(".yaml") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
