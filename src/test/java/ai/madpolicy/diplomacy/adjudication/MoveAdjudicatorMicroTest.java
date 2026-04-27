package ai.madpolicy.diplomacy.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import ai.madpolicy.diplomacy.model.GamePhase;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.NationState;
import ai.madpolicy.diplomacy.model.OrderState;
import ai.madpolicy.diplomacy.model.UnitState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoveAdjudicatorMicroTest {
    @Test
    void adjudicatesRepresentativeRegressionCase() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/test6.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("lon_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("lon_l").comment()).isEqualTo("Standoff.");
        assertThat(result.unitResult("lon_l").dislodgedBy()).isEqualTo("eng");
        assertThat(result.unitResult("wal_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("yor_l").comment()).isEqualTo("Overwhelmed.");
        assertThat(result.unitResult("eng_s").status()).isEqualTo("success");
        assertThat(result.unitResult("nth_s").status()).isEqualTo("success");
    }

    @Test
    void supportToHoldMatchesSupportedUnitOrderedToSupport() {
        GameStateDocument state = new GameStateDocument(
            "game_state",
            1,
            new GamePhase("spring", "move", 1901, 0),
            Map.of(
                "france",
                new NationState(
                    "France",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("par_l", new OrderState("support", "new", "bre_c", "bre_c", false, null), null),
                        new UnitState("bre_c", new OrderState("hold", "new", null, null, false, null), null),
                        new UnitState("gas_l", new OrderState("support", "new", "par_l", "par_l", false, null), null)
                    )
                ),
                "germany",
                new NationState(
                    "Germany",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("bur_l", new OrderState("move", "new", "par_l", null, false, null), null)
                    )
                )
            ),
            List.of()
        );

        var result = RegressionFixtures.adjudicator().adjudicate(state);

        assertThat(result.unitResult("gas_l").status()).isEqualTo("success");
        assertThat(result.unitResult("par_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("par_l").comment()).isEqualTo("Support cut.");
    }

    @Test
    void supportToHoldMatchesSupportedUnitOrderedToConvoy() {
        GameStateDocument state = new GameStateDocument(
            "game_state",
            1,
            new GamePhase("spring", "move", 1901, 0),
            Map.of(
                "france",
                new NationState(
                    "France",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("bre_c", new OrderState("hold", "new", null, null, false, null), null),
                        new UnitState("eng_s", new OrderState("convoy", "new", "bre_c", "lon_l", false, null), null),
                        new UnitState("iri_s", new OrderState("support", "new", "eng_s", "eng_s", false, null), null)
                    )
                ),
                "germany",
                new NationState(
                    "Germany",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("nth_s", new OrderState("move", "new", "eng_s", null, false, null), null)
                    )
                )
            ),
            List.of()
        );

        var result = RegressionFixtures.adjudicator().adjudicate(state);

        assertThat(result.unitResult("iri_s").status()).isEqualTo("success");
        assertThat(result.unitResult("nth_s").status()).isEqualTo("failure");
        assertThat(result.unitResult("nth_s").comment()).isEqualTo("Standoff.");
    }

    @Test
    void supportToHoldAddsDefensiveStrengthToConvoyingUnit() {
        GameStateDocument state = new GameStateDocument(
            "game_state",
            1,
            new GamePhase("spring", "move", 1901, 0),
            Map.of(
                "france",
                new NationState(
                    "France",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("bre_c", new OrderState("hold", "new", null, null, false, null), null),
                        new UnitState("eng_s", new OrderState("convoy", "new", "bre_c", "lon_l", false, null), null),
                        new UnitState("iri_s", new OrderState("support", "new", "eng_s", "eng_s", false, null), null)
                    )
                ),
                "germany",
                new NationState(
                    "Germany",
                    List.of(),
                    List.of(),
                    List.of(
                        new UnitState("wal_c", new OrderState("move", "new", "eng_s", null, false, null), null),
                        new UnitState("lon_c", new OrderState("support", "new", "wal_c", "eng_s", false, null), null)
                    )
                )
            ),
            List.of()
        );

        var result = RegressionFixtures.adjudicator().adjudicate(state);

        assertThat(result.unitResult("eng_s").dislodgedBy()).isNull();
        assertThat(result.unitResult("wal_c").status()).isEqualTo("failure");
        assertThat(result.unitResult("wal_c").comment()).isEqualTo("Standoff.");
    }

    @Test
    void convoyRequiresAnArmyAtFromPosition() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_7k.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("adr_s").status()).isEqualTo("failure");
        assertThat(result.unitResult("adr_s").comment()).isEqualTo("No army to convoy.");
    }

    @Test
    void convoyedMoveCanFailByStandoffEvenWhenRouteExists() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_3g.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("lon_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("lon_l").comment()).isEqualTo("Standoff.");
    }

    @Test
    void equalDefenseAgainstNonLeavingUnitIsReportedAsStandoff() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/test6.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("lon_l").comment()).isEqualTo("Standoff.");
    }

    @Test
    void cutSupportDoesNotCauseFalseDislodgement() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/2ed_example10.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("ber_l").dislodgedBy()).isNull();
        assertThat(result.unitResult("mun_l").dislodgedBy()).isEqualTo("boh");
    }

    @Test
    void strongerHeadToHeadAttackSucceeds() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_7g.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("nwy_l").status()).isEqualTo("success");
        assertThat(result.unitResult("swe_l").dislodgedBy()).isEqualTo("nwy");
    }

    @Test
    void dislodgedCounterattackDoesNotCreateFalseStandoff() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/2ed_example5.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("rum_l").status()).isEqualTo("success");
        assertThat(result.unitResult("sev_l").status()).isEqualTo("success");
        assertThat(result.standoffs()).isEmpty();
    }

    @Test
    void supportToMoveMatchesDestinationProvinceNotExactPosition() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_2i.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("por_c").status()).isEqualTo("success");
    }

    @Test
    void supportDoesNotCountTowardSelfDislodgement() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/2ed_example2.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("boh_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("mun_l").dislodgedBy()).isNull();
    }

    @Test
    void failedConvoyedAttackDoesNotCutSupport() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_6e.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("hol_l").status()).isEqualTo("success");
        assertThat(result.unitResult("lon_l").comment()).isEqualTo("Convoy failed.");
    }

    @Test
    void convoyedAttackOnlyCutsSupportIfRouteSurvives() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_6l.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("wal_c").status()).isEqualTo("success");
        assertThat(result.unitResult("bre_l").comment()).isEqualTo("Convoy failed.");
    }

    @Test
    void secondOrderParadoxKeepsNonParadoxAttackFromBreakingThrough() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_6t.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("iri_s").status()).isEqualTo("failure");
        assertThat(result.unitResult("iri_s").comment()).isEqualTo("Standoff.");
        assertThat(result.unitResult("eng_s").status()).isEqualTo("failure");
    }

    @Test
    void equalAttackAgainstUnitLaterDislodgedIsReportedAsOverwhelmed() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/test10.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("edi_c").status()).isEqualTo("failure");
        assertThat(result.unitResult("edi_c").comment()).isEqualTo("Overwhelmed.");
    }

    @Test
    void convoyedAttackDoesNotCutRouteDeterminingSupportFromDestinationProvince() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/test25.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("tun_l").status()).isEqualTo("failure");
        assertThat(result.unitResult("tun_l").comment()).isEqualTo("Standoff.");
        assertThat(result.unitResult("ion_s").status()).isEqualTo("success");
        assertThat(result.unitResult("eas_s").comment()).isEqualTo("Standoff.");
    }

    @Test
    void protectedSupportCanStillApplyEvenIfSupporterIsDislodgedByConvoyedAttack() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_6n.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("bre_l").status()).isEqualTo("success");
        assertThat(result.unitResult("lon_c").dislodgedBy()).isEqualTo("bre");
        assertThat(result.unitResult("eng_s").status()).isEqualTo("success");
        assertThat(result.standoffs()).containsExactly("eng");
    }

    @Test
    void dislodgedAttackerStillCreatesStandoffInDifferentProvince() {
        var expected = RegressionFixtures.loadRegression("metadata/regression/datc_7h.yaml");
        var input = RegressionFixtures.normalize(expected);

        var result = RegressionFixtures.adjudicator().adjudicate(input);

        assertThat(result.unitResult("nwy_l").status()).isEqualTo("success");
        assertThat(result.unitResult("swe_l").dislodgedBy()).isEqualTo("nwy");
        assertThat(result.unitResult("nwg_s").status()).isEqualTo("failure");
        assertThat(result.unitResult("nwg_s").comment()).isEqualTo("Standoff.");
        assertThat(result.standoffs()).containsExactly("nwy");
    }
}
