package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
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
    private final ObjectMapper yamlMapper;
    private final AdjudicatedStateBuilder adjudicatedStateBuilder;
    private final NextStateBuilder nextStateBuilder;

    public AdjudicationService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        this.adjudicatedStateBuilder = new AdjudicatedStateBuilder();
        this.nextStateBuilder = new NextStateBuilder();
    }

    public DualStateResult adjudicate(Path boardPath, Path statePath) {
        BoardDocument board = load(boardPath, BoardDocument.class);
        GameStateDocument state = load(statePath, GameStateDocument.class);
        var adjudicator = new MoveAdjudicator(new BoardIndex(board));
        var adjudication = adjudicator.adjudicate(state);
        return new DualStateResult(
            adjudicatedStateBuilder.build(state, adjudication),
            nextStateBuilder.build(state, adjudication)
        );
    }

    private <T> T load(Path path, Class<T> type) {
        try {
            return yamlMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML: " + path, e);
        }
    }
}
