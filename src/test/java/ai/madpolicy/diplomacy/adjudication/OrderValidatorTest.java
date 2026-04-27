package ai.madpolicy.diplomacy.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderValidatorTest {
    @Test
    void rejectsDirectMoveWithoutAdjacency() {
        var result = TestFixtures.validator().validateMove("lon_l", "kie_l", false);
        assertThat(result.isLegal()).isFalse();
        assertThat(result.failureComment()).isEqualTo(FailureComment.ILLEGAL_ORDER);
    }

    @Test
    void allowsSupportToProvinceNotExactPosition() {
        var result = TestFixtures.validator().validateSupport("nth_s", "bel_l", "bel_l");
        assertThat(result.isLegal()).isTrue();
    }

    @Test
    void rejectsConvoyMoveWithoutPossibleSeaRoute() {
        var result = TestFixtures.validator().validateMove("gre_l", "sev_l", true);
        assertThat(result.isLegal()).isFalse();
        assertThat(result.failureComment()).isEqualTo(FailureComment.ILLEGAL_ORDER);
    }

    @Test
    void rejectsConvoyOrderForFleetNotOnAnyValidRoute() {
        var result = TestFixtures.validator().validateConvoy("bla_s", "syr_l", "stp_l");
        assertThat(result.isLegal()).isFalse();
        assertThat(result.failureComment()).isEqualTo(FailureComment.ILLEGAL_ORDER);
    }
}
