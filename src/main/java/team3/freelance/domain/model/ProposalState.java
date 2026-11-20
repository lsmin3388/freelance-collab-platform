package team3.freelance.domain.model;

public enum ProposalState {
    RECEIVED("접수"), SELECTED("선정"), CLOSED("종료"), BROKEN_OFF("결렬");

    private final String label;
    ProposalState(String label) { this.label = label; }
    public String label() { return label; }
}
