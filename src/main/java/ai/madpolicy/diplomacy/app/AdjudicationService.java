package ai.madpolicy.diplomacy.app;

import ai.madpolicy.diplomacy.adjudication.AdjudicationResult;
import ai.madpolicy.diplomacy.adjudication.AdjustmentAdjudicator;
import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.adjudication.RetreatAdjudicator;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.io.AdjudicatedStateBuilder;
import ai.madpolicy.diplomacy.io.NextStateBuilder;
import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.GameOptions;
import ai.madpolicy.diplomacy.model.GamePhase;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdjudicationService {
    private static final Path INITIAL_STATE_PATH = Path.of("data/seeds/standard/1.yaml");
    private static final Path DEFAULT_GAMES_ROOT = Path.of("data/games");

    private final BoardCatalog boardCatalog;
    private final ObjectMapper yamlMapper;
    private final AdjudicatedStateBuilder adjudicatedStateBuilder;
    private final Clock clock;
    private final Path gamesRoot;

    public AdjudicationService() {
        this(Clock.systemUTC());
    }

    public AdjudicationService(Clock clock) {
        this(clock, DEFAULT_GAMES_ROOT);
    }

    @Autowired
    public AdjudicationService(
        Clock clock,
        @Value("${diplomacy.games.root:data/games}") String gamesRoot
    ) {
        this(clock, Path.of(gamesRoot));
    }

    public AdjudicationService(Clock clock, Path gamesRoot) {
        this.boardCatalog = new BoardCatalog();
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        this.yamlMapper.setDefaultPropertyInclusion(
            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
        );
        this.adjudicatedStateBuilder = new AdjudicatedStateBuilder();
        this.clock = clock;
        this.gamesRoot = gamesRoot;
    }

    public GameStateDocument adjudicate(GameStateDocument state) {
        BoardDocument board = boardCatalog.resolve(state.board(), state.boardRevision());
        BoardIndex boardIndex = new BoardIndex(board);
        AdjudicationResult adjudication = switch (state.phase().step()) {
            case "retreat" -> new RetreatAdjudicator(boardIndex).adjudicate(state);
            case "adjustment" -> new AdjustmentAdjudicator(boardIndex).adjudicate(state);
            default -> new MoveAdjudicator(boardIndex).adjudicate(state);
        };
        return adjudicatedStateBuilder.build(state, adjudication);
    }

    public GameStateDocument initialState() {
        return initialState(null, null, null, null);
    }

    public GameStateDocument initialState(
        String gameId,
        Integer movePhaseMinutes,
        Integer retreatPhaseMinutes,
        Integer adjustmentPhaseMinutes
    ) {
        GameOptions options = new GameOptions(
            gameId == null || gameId.isBlank() ? UUID.randomUUID().toString() : gameId,
            phaseMinutes("move_phase_minutes", movePhaseMinutes, 15),
            phaseMinutes("retreat_phase_minutes", retreatPhaseMinutes, 5),
            phaseMinutes("adjustment_phase_minutes", adjustmentPhaseMinutes, 5)
        );
        GameStateDocument state = withOptionsAndStarted(load(INITIAL_STATE_PATH, GameStateDocument.class), options);
        saveInitialState(state);
        return state;
    }

    public GameStateDocument officiate(GameStateDocument adjudicatedState) {
        BoardDocument board = boardCatalog.resolve(adjudicatedState.board(), adjudicatedState.boardRevision());
        return withStarted(new NextStateBuilder(new BoardIndex(board)).build(adjudicatedState));
    }

    private <T> T load(Path path, Class<T> type) {
        try {
            return yamlMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse YAML: " + path, e);
        }
    }

    private int phaseMinutes(String name, Integer value, int defaultValue) {
        int resolved = value == null ? defaultValue : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return resolved;
    }

    private void saveInitialState(GameStateDocument state) {
        String gameId = state.options().gameId();
        if (!isSafePathSegment(gameId)) {
            throw new IllegalArgumentException("game_id must be a single path segment");
        }
        GamePhase phase = state.phase();
        Path gameDir = gamesRoot.resolve(gameId);
        Path statePath = gameDir.resolve(phase.year() + "_" + phase.season() + "_" + phase.step() + ".yaml");
        try {
            Files.createDirectories(gameDir);
            yamlMapper.writeValue(statePath.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save initial game state: " + statePath, e);
        }
    }

    private boolean isSafePathSegment(String value) {
        return value != null
            && !value.isBlank()
            && !".".equals(value)
            && !"..".equals(value)
            && !value.contains("/")
            && !value.contains("\\");
    }

    private GameStateDocument withOptionsAndStarted(GameStateDocument state, GameOptions options) {
        return new GameStateDocument(
            state.kind(),
            state.schema(),
            state.revision(),
            state.board(),
            state.boardRevision(),
            options,
            withStarted(state.phase()),
            state.nations(),
            state.standoffs()
        );
    }

    private GameStateDocument withStarted(GameStateDocument state) {
        return new GameStateDocument(
            state.kind(),
            state.schema(),
            state.revision(),
            state.board(),
            state.boardRevision(),
            state.options(),
            withStarted(state.phase()),
            state.nations(),
            state.standoffs()
        );
    }

    private GamePhase withStarted(GamePhase phase) {
        return new GamePhase(
            phase.season(),
            phase.step(),
            phase.year(),
            clock.instant().getEpochSecond()
        );
    }
}
