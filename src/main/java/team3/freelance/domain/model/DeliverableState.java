package team3.freelance.domain.model;

public enum DeliverableState {
    PENDING_REVIEW("검토 대기"), APPROVED("승인"), REVISION_REQUESTED("수정 요청");

    private final String label;
    DeliverableState(String label) { this.label = label; }
    public String label() { return label; }
}
