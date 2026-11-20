package team3.freelance.domain.model;

public enum SettlementState {
    PENDING("대기"), SUCCESS("성공"), FAILED("실패");

    private final String label;
    SettlementState(String label) { this.label = label; }
    public String label() { return label; }
}
