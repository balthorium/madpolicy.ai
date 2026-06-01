package ai.madpolicy.diplomacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.madpolicy.diplomacy.adjudication.MoveAdjudicator;
import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.domain.BoardIndex;
import ai.madpolicy.diplomacy.io.AdjudicatedStateBuilder;
import ai.madpolicy.diplomacy.io.NextStateBuilder;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjudicationControllerTest {
    @Test
    void returnsInitialGameState() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.initialStateYaml();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
            contentType -> assertThat(contentType).startsWith("application/yaml")
        );

        GameStateDocument expected = TestYaml.load(Path.of("data/seeds/standard/1.yaml"), GameStateDocument.class);
        GameStateDocument actual = TestYaml.load(response.body(), GameStateDocument.class);
        assertThat(actual.phase().started()).isEqualTo(YamlAdjudicationHttpClient.FIXED_EPOCH_SECONDS);
        assertThat(UUID.fromString(actual.options().gameId())).isNotNull();
        assertThat(actual.options().movePhaseMinutes()).isEqualTo(15);
        assertThat(actual.options().retreatPhaseMinutes()).isEqualTo(5);
        assertThat(actual.options().adjustmentPhaseMinutes()).isEqualTo(5);
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("phase.started", "options")
            .isEqualTo(expected);
        Path savedState = YamlAdjudicationHttpClient.gamesRoot()
            .resolve(actual.options().gameId())
            .resolve("1901_spring_move.yaml");
        assertThat(savedState).isRegularFile();
        assertThat(TestYaml.load(savedState, GameStateDocument.class)).isEqualTo(actual);
    }

    @Test
    void officiatesAdjudicatedGameStateToNextState() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        GameStateDocument input = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test6_requested.yaml"), GameStateDocument.class);
        var adjudicator = new MoveAdjudicator(new BoardIndex(new BoardCatalog().resolve(
            input.board(),
            input.boardRevision()
        )));
        var adjudication = adjudicator.adjudicate(input);
        GameStateDocument adjudicated = new AdjudicatedStateBuilder().build(input, adjudication);
        GameStateDocument expected = new NextStateBuilder().build(input, adjudication);
        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.officiateYaml(TestYaml.write(adjudicated));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
            contentType -> assertThat(contentType).startsWith("application/yaml")
        );
        assertThat(response.body()).doesNotContain("comment: null");
        assertThat(response.body()).doesNotContain("dislodged_by: null");
        assertThat(response.body()).doesNotContain("order: null");
        assertThat(response.body()).doesNotContain("from: null");
        assertThat(response.body()).doesNotContain("to: null");
        assertThat(response.body()).doesNotContain("via_convoy: null");
        assertThat(response.body()).contains("type: \"disband\"");
        GameStateDocument actual = TestYaml.load(response.body(), GameStateDocument.class);
        assertThat(actual.phase().started()).isEqualTo(YamlAdjudicationHttpClient.FIXED_EPOCH_SECONDS);
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("phase.started")
            .isEqualTo(expected);
    }

    @Test
    void adjudicatesPostedGameState() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        GameStateDocument expected = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test1_expected.yaml"), GameStateDocument.class);
        GameStateDocument input = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test1_requested.yaml"), GameStateDocument.class);
        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.adjudicateYaml(TestYaml.write(input));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
            contentType -> assertThat(contentType).startsWith("application/yaml")
        );
        assertThat(response.body()).doesNotContain("comment: null");
        assertThat(response.body()).doesNotContain("dislodged_by: null");
        assertThat(response.body()).doesNotContain("from: null");
        assertThat(response.body()).doesNotContain("to: null");
        assertThat(response.body()).doesNotContain("via_convoy: null");
        assertThat(TestYaml.load(response.body(), GameStateDocument.class)).isEqualTo(expected);
    }

    @Test
    void adjudicationPreservesGameOptions() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        String requestBody = java.nio.file.Files.readString(Path.of("src/test/resources/regression/adjudicate/move/test1_requested.yaml"))
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

        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.adjudicateYaml(requestBody);

        assertThat(response.statusCode()).isEqualTo(200);
        GameStateDocument actual = TestYaml.load(response.body(), GameStateDocument.class);
        assertThat(actual.options().gameId()).isEqualTo("carry-through");
        assertThat(actual.options().movePhaseMinutes()).isEqualTo(31);
        assertThat(actual.options().retreatPhaseMinutes()).isEqualTo(11);
        assertThat(actual.options().adjustmentPhaseMinutes()).isEqualTo(13);
    }

    @Test
    void defaultsMissingStatusInRequestToPending() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        GameStateDocument expected = TestYaml.load(Path.of("src/test/resources/regression/adjudicate/move/test1_expected.yaml"), GameStateDocument.class);
        String requestBody = java.nio.file.Files.readString(Path.of("src/test/resources/regression/adjudicate/move/test1_requested.yaml"))
            .replaceAll("(?m)^\\s*status:\\s*pending\\n", "");

        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.adjudicateYaml(requestBody);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(TestYaml.load(response.body(), GameStateDocument.class)).isEqualTo(expected);
    }
}
