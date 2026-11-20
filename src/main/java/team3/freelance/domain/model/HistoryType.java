package team3.freelance.domain.model;

public enum HistoryType {
    STATE_TRANSITION("상태 전이"), AGREEMENT("합의"), CHANGE_REQUEST("변경 요청"), DELIVERABLE("결과물"), REVIEW("검토");

    private final String label;
    HistoryType(String label) { this.label = label; }
    public String label() { return label; }
}
