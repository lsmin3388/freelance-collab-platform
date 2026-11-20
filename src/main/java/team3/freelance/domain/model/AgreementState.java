package team3.freelance.domain.model;

public enum AgreementState {
    NEGOTIATING("협의 중"), CONFIRMED("확정"), BROKEN_OFF("결렬");

    private final String label;
    AgreementState(String label) { this.label = label; }
    public String label() { return label; }
}
