package team3.freelance.domain.model;

import java.util.List;

/**
 * confirmAgreement의 결과.
 *
 * @param confirmed         이번 확인으로 확정되었는가 (false = 상대 확인 대기)
 * @param closedProposers   확정과 함께 '종료'된 다른 제안의 제안자들 (미선정 통지 대상, R2)
 */
public record ConfirmOutcome(boolean confirmed, List<User> closedProposers) {
}
