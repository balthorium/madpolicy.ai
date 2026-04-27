package ai.madpolicy.diplomacy.regression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegressionCorpusTest {
    @Test
    void corpusMatchesExpectedAdjudicatedState() {
        var summary = RegressionHarness.runAll();

        assertThat(summary.failedCaseNames())
            .describedAs("failures=%s histogram=%s", summary.failedCases(), summary.mismatchHistogram())
            .isEmpty();
    }
}
