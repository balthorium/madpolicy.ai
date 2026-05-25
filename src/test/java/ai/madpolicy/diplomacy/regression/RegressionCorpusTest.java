package ai.madpolicy.diplomacy.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.madpolicy.diplomacy.web.YamlAdjudicationHttpClient;
import org.junit.jupiter.api.Test;

class RegressionCorpusTest {
    @Test
    void corpusMatchesExpectedAdjudicatedState() {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        var summary = RegressionHarness.runAll();

        assertThat(summary.failedCaseNames())
            .describedAs("failures=%s histogram=%s", summary.failedCases(), summary.mismatchHistogram())
            .isEmpty();
    }
}
