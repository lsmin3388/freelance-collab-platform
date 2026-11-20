package team3.freelance.domain.model;

public enum ChangeRequestState {
    PENDING("대기"), ACCEPTED("수락"), REJECTED("거절"), WITHDRAWN("철회");

    private final String label;
    ChangeRequestState(String label) { this.label = label; }
    public String label() { return label; }
}
