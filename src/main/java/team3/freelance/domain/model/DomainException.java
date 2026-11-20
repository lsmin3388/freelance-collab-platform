package team3.freelance.domain.model;

/** 업무 규칙 위반. Model 연산이 던지고, 화면이 메시지로 보여 준다. 던지기 전의 변경은 저장되지 않는다. */
public class DomainException extends RuntimeException {
    private final ErrorCode code;

    public DomainException(ErrorCode code) {
        this(code, code.message());
    }

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }
}
