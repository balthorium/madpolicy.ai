package ai.madpolicy.diplomacy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoardIndexTest {
    @Test
    void resolvesProvinceAndAdjacencyLookups() {
        BoardIndex board = TestFixtures.boardIndex();

        assertThat(board.provinceIdForPosition("tri_c")).isEqualTo("tri");
        assertThat(board.areAdjacent("tri_c", "adr_s")).isTrue();
        assertThat(board.provinceHasCoast("vie")).isFalse();
    }
}
