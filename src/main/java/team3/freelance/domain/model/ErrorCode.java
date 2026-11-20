package team3.freelance.domain.model;

/**
 * 업무 규칙 위반의 종류. 설계 문서 Phase 3 예외 흐름 표의 "오류 코드" 열과 같다.
 * kind: FORBIDDEN(권한 없음) / CONFLICT(지금 상태에서는 불가) / INVALID(입력이 규칙에 맞지 않음) / NOT_FOUND
 */
public enum ErrorCode {
    LOGIN_REQUIRED(Kind.FORBIDDEN, "로그인이 필요합니다."),
    ROLE_REQUIRED(Kind.FORBIDDEN, "이 기능을 쓸 수 있는 역할이 없습니다."),
    NOT_OWNER(Kind.FORBIDDEN, "이 프로젝트의 의뢰자만 할 수 있습니다."),
    NOT_PARTICIPANT(Kind.FORBIDDEN, "이 프로젝트의 의뢰자 또는 선정 프리랜서만 할 수 있습니다."),
    NOT_ASSIGNED(Kind.FORBIDDEN, "이 프로젝트에 선정된 프리랜서만 할 수 있습니다."),

    INVALID_STATE(Kind.CONFLICT, "지금 프로젝트 상태에서는 할 수 없습니다."),
    OWN_PROJECT(Kind.CONFLICT, "본인이 등록한 프로젝트에는 제안할 수 없습니다."),
    DUPLICATE_PROPOSAL(Kind.CONFLICT, "이미 이 프로젝트에 제안했습니다."),
    PROPOSAL_NOT_SELECTABLE(Kind.CONFLICT, "선정할 수 없는 제안입니다."),
    NO_ACTIVE_AGREEMENT(Kind.CONFLICT, "진행 중인 합의가 없습니다."),
    STALE_REVISION(Kind.CONFLICT, "합의안이 그사이 바뀌었습니다. 바뀐 내용을 확인한 뒤 다시 확인해 주세요."),
    ALREADY_CONFIRMED(Kind.CONFLICT, "이미 이 개정안을 확인했습니다. 상대방의 확인을 기다리는 중입니다."),
    PENDING_CHANGE(Kind.CONFLICT, "응답을 기다리는 변경 요청이 있어 지금은 제출할 수 없습니다."),
    SETTLEMENT_ALREADY_RECORDED(Kind.CONFLICT, "정산 결과는 이미 기록되었습니다."),

    DEADLINE_EXCEEDED(Kind.INVALID, "수행 기간이 프로젝트 희망 마감일을 넘습니다."),
    INVALID_AMOUNT(Kind.INVALID, "금액과 기간은 1 이상이어야 합니다."),
    REQUIRED_FIELD(Kind.INVALID, "필수 항목이 비어 있습니다."),
    REASON_REQUIRED(Kind.INVALID, "사유를 입력해 주세요."),
    EMPTY_SUBMISSION(Kind.INVALID, "파일을 1개 이상 올리거나 가상 결과물 정보를 입력해 주세요."),
    FORMAT_NOT_ALLOWED(Kind.INVALID, "합의한 산출물 형식이 아닌 파일이 있습니다."),
    FEEDBACK_REQUIRED(Kind.INVALID, "수정 요청에는 피드백 내용을 반드시 적어야 합니다."),
    LOGIN_FAILED(Kind.INVALID, "이메일 또는 비밀번호가 맞지 않습니다."),

    NOT_FOUND(Kind.NOT_FOUND, "대상을 찾을 수 없습니다.");

    public enum Kind { FORBIDDEN, CONFLICT, INVALID, NOT_FOUND }

    private final Kind kind;
    private final String message;

    ErrorCode(Kind kind, String message) {
        this.kind = kind;
        this.message = message;
    }

    public Kind kind() { return kind; }
    public String message() { return message; }
}
