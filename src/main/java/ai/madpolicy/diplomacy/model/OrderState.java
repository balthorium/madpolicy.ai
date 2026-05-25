package ai.madpolicy.diplomacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderState(
    String type,
    String status,
    String to,
    String from,
    @JsonProperty("via_convoy") Boolean viaConvoy,
    String comment
) {
    public OrderState {
        if (status == null) {
            status = "pending";
        }
    }
}
