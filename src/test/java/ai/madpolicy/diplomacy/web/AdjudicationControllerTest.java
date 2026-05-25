package ai.madpolicy.diplomacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdjudicationControllerTest {
    @Test
    void adjudicatesPostedGameState() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        GameStateDocument expected = TestYaml.load(Path.of("src/test/resources/regression/test1_expected.yaml"), GameStateDocument.class);
        GameStateDocument input = TestYaml.load(Path.of("src/test/resources/regression/test1_requested.yaml"), GameStateDocument.class);
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
    void defaultsMissingStatusInRequestToPending() throws Exception {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        GameStateDocument expected = TestYaml.load(Path.of("src/test/resources/regression/test1_expected.yaml"), GameStateDocument.class);
        String requestBody = java.nio.file.Files.readString(Path.of("src/test/resources/regression/test1_requested.yaml"))
            .replaceAll("(?m)^\\s*status:\\s*pending\\n", "");

        YamlAdjudicationHttpClient client = new YamlAdjudicationHttpClient();
        HttpResponse<String> response = client.adjudicateYaml(requestBody);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(TestYaml.load(response.body(), GameStateDocument.class)).isEqualTo(expected);
    }
}
