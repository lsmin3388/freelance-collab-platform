package team3.freelance.domain.model;

/** reviewLatest의 결정 : {승인 | 수정 요청} */
public enum ReviewDecision {
    APPROVE("승인"), REQUEST_REVISION("수정 요청");

    private final String label;
    ReviewDecision(String label) { this.label = label; }
    public String label() { return label; }
}
