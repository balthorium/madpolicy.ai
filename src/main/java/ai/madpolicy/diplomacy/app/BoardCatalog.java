package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.model.BoardDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class BoardCatalog {
    private static final Path DEFAULT_BOARD_DIR = Path.of("data/boards");

    private final Path boardDirectory;
    private final ObjectMapper yamlMapper;

    public BoardCatalog() {
        this(DEFAULT_BOARD_DIR);
    }

    public BoardCatalog(Path boardDirectory) {
        this.boardDirectory = boardDirectory;
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    }

    public BoardDocument resolve(String boardId, int boardRevision) {
        Path boardPath = boardDirectory.resolve(boardId).resolve(boardRevision + ".yaml");
        BoardDocument board = load(boardPath);
        String directoryName = boardPath.getParent().getFileName().toString();
        if (!directoryName.equals(board.id())) {
            throw new IllegalStateException(
                "Board directory '" + directoryName + "' does not match board id '" + board.id() + "'"
            );
        }
        String fileStem = stripYamlExtension(boardPath.getFileName().toString());
        if (!fileStem.equals(Integer.toString(board.revision()))) {
            throw new IllegalStateException(
                "Board filename '" + fileStem + "' does not match board revision '" + board.revision() + "'"
            );
        }
        return board;
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
