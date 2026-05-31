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
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OfficiateControllerMockMvcTest {
    private static final MediaType YAML = MediaType.valueOf("application/yaml");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new YamlWebConfig().extendMessageConverters(converters);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdjudicationController(new AdjudicationService()))
            .setMessageConverters(converters.toArray(HttpMessageConverter[]::new))
            .build();
    }

    @Test
    void returnsInitialGameState() throws Exception {
        String responseBody = mockMvc.perform(get("/officiate").accept(YAML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(YAML))
            .andReturn()
            .getResponse()
            .getContentAsString();

        GameStateDocument expected = TestYaml.load(Path.of("data/seeds/standard/1.yaml"), GameStateDocument.class);
        assertThat(TestYaml.load(responseBody, GameStateDocument.class)).isEqualTo(expected);
    }

    @Test
    void officiatesAdjudicatedGameStateToNextState() throws Exception {
        GameStateDocument input = TestYaml.load(Path.of("src/test/resources/regression/test6_requested.yaml"), GameStateDocument.class);
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
        assertThat(responseBody).doesNotContain("from: null");
        assertThat(responseBody).doesNotContain("to: null");
        assertThat(responseBody).doesNotContain("via_convoy: null");
        assertThat(TestYaml.load(responseBody, GameStateDocument.class)).isEqualTo(expected);
    }
}
