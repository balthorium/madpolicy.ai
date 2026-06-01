package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.adjudication.RetreatAdjudicator;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.io.AdjudicatedStateBuilder;
import ai.madpolicy.diplomacy.io.NextStateBuilder;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class AdjudicationService {
    private static final Path INITIAL_STATE_PATH = Path.of("data/seeds/standard/1.yaml");

    private final BoardCatalog boardCatalog;
    private final ObjectMapper yamlMapper;
    private final AdjudicatedStateBuilder adjudicatedStateBuilder;

    public AdjudicationService() {
        this.boardCatalog = new BoardCatalog();
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        this.adjudicatedStateBuilder = new AdjudicatedStateBuilder();
    }

    public GameStateDocument adjudicate(GameStateDocument state) {
        BoardDocument board = boardCatalog.resolve(state.board(), state.boardRevision());
        BoardIndex boardIndex = new BoardIndex(board);
        var adjudication = "retreat".equals(state.phase().step())
            ? new RetreatAdjudicator(boardIndex).adjudicate(state)
            : new MoveAdjudicator(boardIndex).adjudicate(state);
        return adjudicatedStateBuilder.build(state, adjudication);
    }

    public GameStateDocument initialState() {
        return load(INITIAL_STATE_PATH, GameStateDocument.class);
    }

    public GameStateDocument officiate(GameStateDocument adjudicatedState) {
        BoardDocument board = boardCatalog.resolve(adjudicatedState.board(), adjudicatedState.boardRevision());
        return new NextStateBuilder(new BoardIndex(board)).build(adjudicatedState);
    }

    private <T> T load(Path path, Class<T> type) {
        try {
            return yamlMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML: " + path, e);
        }
    }
}
