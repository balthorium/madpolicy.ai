package ai.madpolicy.diplomacy.adjudication;

public enum FailureComment {
    ILLEGAL_ORDER("Illegal or useless order."),
    NO_UNIT_TO_SUPPORT("No unit to support."),
    SUPPORTED_UNIT_DID_NOT_MAKE_CORRESPONDING_ORDER("Supported unit did not make corresponding order."),
    NO_ARMY_TO_CONVOY("No army to convoy."),
    CONVOYED_UNIT_DID_NOT_MAKE_CORRESPONDING_ORDER("Convoyed unit did not make corresponding order."),
    CONVOY_FAILED("Convoy failed."),
    CONVOY_FAILED_DUE_TO_PARADOX_SZYKMAN_RULE("Convoy failed due to paradox (Szykman Rule)."),
    SUPPORT_CUT("Support cut."),
    STANDOFF("Standoff."),
    OVERWHELMED("Overwhelmed."),
    CANNOT_DISLODGE_OWN_UNIT("Cannot dislodge own unit.");

    private final String wireValue;

    FailureComment(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
