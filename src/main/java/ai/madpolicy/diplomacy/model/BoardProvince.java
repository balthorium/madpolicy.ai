package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BoardProvince(
    String name,
    List<BoardPosition> positions,
    @JsonProperty("supply_center") Object supplyCenter
) {
    public boolean isSupplyCenter() {
        return supplyCenter instanceof String;
    }

    public String homeNation() {
        if (!(supplyCenter instanceof String nationId) || "neutral".equals(nationId)) {
            return null;
        }
        return nationId;
    }
}
