package team3.freelance.domain.model;

/** canSubmit의 결과: 제출가능여부와 불가 사유 (G_1, G_3) */
public record SubmitEligibility(boolean allowed, ErrorCode reason) {
    public static final SubmitEligibility OK = new SubmitEligibility(true, null);

    public static SubmitEligibility deny(ErrorCode reason) {
        return new SubmitEligibility(false, reason);
    }
}
