package ai.madpolicy.diplomacy.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.madpolicy.diplomacy.web.YamlAdjudicationHttpClient;
import org.junit.jupiter.api.Test;

class RetreatRegressionCorpusTest {
    @Test
    void corpusMatchesExpectedAdjudicatedRetreatState() {
        assumeTrue(YamlAdjudicationHttpClient.canBindLocalPort(), "local port binding is required for live REST tests");
        var summary = RegressionHarness.runRetreat();

        assertThat(summary.failedCaseNames())
            .describedAs("failures=%s histogram=%s", summary.failedCases(), summary.mismatchHistogram())
            .isEmpty();
    }
}
