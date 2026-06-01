package ai.madpolicy.diplomacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.app.AdjudicationService;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.io.AdjudicatedStateBuilder;
import ai.madpolicy.diplomacy.io.NextStateBuilder;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OfficiateControllerMockMvcTest {
    private static final MediaType YAML = MediaType.valueOf("application/yaml");
    private static final long FIXED_STARTED = 1_700_000_000L;

    private MockMvc mockMvc;

    @TempDir
    private Path gamesRoot;

    @BeforeEach
    void setUp() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new YamlWebConfig().extendMessageConverters(converters);
        Clock fixedClock = Clock.fixed(Instant.ofEpochSecond(FIXED_STARTED), ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdjudicationController(new AdjudicationService(fixedClock, gamesRoot)))
            .setMessageConverters(converters.toArray(HttpMessageConverter[]::new))
            .build();
    }

    @Test
    void returnsInitialGameStateWithRequestedOptionsAndStartedTime() throws Exception {
        String responseBody = mockMvc.perform(get("/officiate")
                .param("game_id", "game-123")
                .param("move_phase_minutes", "30")
                .param("retreat_phase_minutes", "7")
                .param("adjustment_phase_minutes", "9")
                .accept(YAML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        GameStateDocument state = TestYaml.load(responseBody, GameStateDocument.class);
        assertThat(state.phase().started()).isEqualTo(FIXED_STARTED);
        assertThat(state.options().gameId()).isEqualTo("game-123");
        assertThat(state.options().movePhaseMinutes()).isEqualTo(30);
        assertThat(state.options().retreatPhaseMinutes()).isEqualTo(7);
        assertThat(state.options().adjustmentPhaseMinutes()).isEqualTo(9);
    }

    @Test
    void savesReturnedInitialGameStateUnderGameIdAndPhaseFilename() throws Exception {
        String responseBody = mockMvc.perform(get("/officiate")
                .param("game_id", "archive-game")
                .accept(YAML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Path savedState = gamesRoot.resolve("archive-game").resolve("1901_spring_move.yaml");
        assertThat(Files.isRegularFile(savedState)).isTrue();
        assertThat(TestYaml.load(savedState, GameStateDocument.class))
            .isEqualTo(TestYaml.load(responseBody, GameStateDocument.class));
    }

    @Test
    void returnsInitialGameStateWithDefaultOptions() throws Exception {
        String responseBody = mockMvc.perform(get("/officiate").accept(YAML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        GameStateDocument state = TestYaml.load(responseBody, GameStateDocument.class);
        assertThat(state.phase().started()).isEqualTo(FIXED_STARTED);
        assertThat(UUID.fromString(state.options().gameId())).isNotNull();
        assertThat(state.options().movePhaseMinutes()).isEqualTo(15);
        assertThat(state.options().retreatPhaseMinutes()).isEqualTo(5);
        assertThat(state.options().adjustmentPhaseMinutes()).isEqualTo(5);
    }

    @Test
    void rejectsNonPositivePhaseDurations() throws Exception {
        mockMvc.perform(get("/officiate")
                .param("move_phase_minutes", "0")
                .accept(YAML))
            .andExpect(status().isBadRequest());
    }

    @Test
    void officiatesAdjudicatedGameStateToNextState() throws Exception {
        GameStateDocument input = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test6_requested.yaml"), GameStateDocument.class);
        var adjudicator = new MoveAdjudicator(new BoardIndex(new BoardCatalog().resolve(
            input.board(),
            input.boardRevision()
        )));
        var adjudication = adjudicator.adjudicate(input);
        GameStateDocument adjudicated = new AdjudicatedStateBuilder().build(input, adjudication);
        GameStateDocument expected = new NextStateBuilder().build(input, adjudication);

        String responseBody = mockMvc.perform(post("/officiate")
                .contentType(YAML)
                .accept(YAML)
                .content(TestYaml.write(adjudicated)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(responseBody).doesNotContain("comment: null");
        assertThat(responseBody).doesNotContain("dislodged_by: null");
        assertThat(responseBody).doesNotContain("order: null");
        assertThat(responseBody).doesNotContain("from: null");
        assertThat(responseBody).doesNotContain("to: null");
        assertThat(responseBody).doesNotContain("via_convoy: null");
        assertThat(responseBody).contains("type: \"disband\"");
        GameStateDocument actual = TestYaml.load(responseBody, GameStateDocument.class);
        assertThat(actual.phase().started()).isEqualTo(FIXED_STARTED);
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("phase.started")
            .isEqualTo(expected);
    }

    @Test
    void officiatePreservesGameOptions() throws Exception {
        String requestBody = java.nio.file.Files.readString(Path.of("src/test/resources/regression/officiate/adjustment/test26_requested.yaml"))
            .replace(
                "phase:\n",
                """
                options:
                  game_id: carry-through
                  move_phase_minutes: 31
                  retreat_phase_minutes: 11
                  adjustment_phase_minutes: 13
                phase:
                """
            );

        String responseBody = mockMvc.perform(post("/officiate")
                .contentType(YAML)
                .accept(YAML)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        GameStateDocument actual = TestYaml.load(responseBody, GameStateDocument.class);
        assertThat(actual.phase().started()).isEqualTo(FIXED_STARTED);
        assertThat(actual.options().gameId()).isEqualTo("carry-through");
        assertThat(actual.options().movePhaseMinutes()).isEqualTo(31);
        assertThat(actual.options().retreatPhaseMinutes()).isEqualTo(11);
        assertThat(actual.options().adjustmentPhaseMinutes()).isEqualTo(13);
    }
}
