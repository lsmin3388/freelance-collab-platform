package team3.freelance.domain.model;

/** 프로젝트 상태 8개 (R1). 작업 중~승인됨은 상위 상태 '진행 중'으로 묶인다. */
public enum ProjectState {
    RECRUITING("모집 중"),
    NEGOTIATING("조건 합의 중"),
    IN_PROGRESS("작업 중"),
    IN_REVIEW("검토 중"),
    REVISING("수정 중"),
    APPROVED("승인됨"),
    COMPLETED("완료"),
    CANCELLED("취소");

    private final String label;
    ProjectState(String label) { this.label = label; }
    public String label() { return label; }

    /** 상위 상태 '진행 중'(작업 중·검토 중·수정 중·승인됨)에 속하는가 */
    public boolean isOngoing() {
        return this == IN_PROGRESS || this == IN_REVIEW || this == REVISING || this == APPROVED;
    }
}
