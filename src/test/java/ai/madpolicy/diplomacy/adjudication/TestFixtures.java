package ai.madpolicy.diplomacy.adjudication;

import ai.madpolicy.diplomacy.app.BoardCatalog;
import ai.madpolicy.diplomacy.domain.BoardIndex;

final class TestFixtures {
    private TestFixtures() {
    }

    static OrderValidator validator() {
        return new OrderValidator(new BoardIndex(new BoardCatalog().resolve("standard")));
    }
}
