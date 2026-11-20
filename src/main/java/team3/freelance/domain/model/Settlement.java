package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 정산 기록. 완료 처리 때 '대기'로 생기고 결제·정산 서비스(Mock)의 결과를 한 번만 기록한다 (I_3). */
@Entity
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int amount;                    // 정산금액
    private int targetRound;               // 대상회차
    private LocalDateTime createdAt;       // 생성시각
    private LocalDateTime processedAt;     // 처리시각 [0..1]
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementState state;

    protected Settlement() {
    }

    Settlement(int amount, int targetRound) {
        this.amount = amount;
        this.targetRound = targetRound;
        this.createdAt = LocalDateTime.now();
        this.state = SettlementState.PENDING;
    }

    /** record (I_3): 대기 → 성공/실패 1회만 */
    void record(boolean success) {
        if (state != SettlementState.PENDING) throw new DomainException(ErrorCode.SETTLEMENT_ALREADY_RECORDED);
        state = success ? SettlementState.SUCCESS : SettlementState.FAILED;
        processedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public int getAmount() { return amount; }
    public int getTargetRound() { return targetRound; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public SettlementState getState() { return state; }
}
