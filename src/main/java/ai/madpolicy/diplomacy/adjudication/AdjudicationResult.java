package ai.madpolicy.diplomacy.adjudication;

import java.util.Map;
import java.util.Set;

public record AdjudicationResult(
    Map<String, UnitResult> byPosition,
    Set<String> standoffs
) {
    public UnitResult unitResult(String position) {
        return byPosition.get(position);
    }
}
