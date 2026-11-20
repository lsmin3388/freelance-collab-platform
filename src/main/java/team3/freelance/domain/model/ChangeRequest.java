package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 변경 요청. 이번 구현 범위(핵심 시나리오 4개)에서는 '대기 중인 변경 요청이 있으면 제출 불가'(R6, R9)를
 * 판단하는 데 필요한 만큼만 둔다. 응답·철회 흐름(F_2, F_3, F_5)은 구현 범위 밖이다.
 */
@Entity
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 4000)
    private String newScope;          // 새작업범위 [0..1]
    private LocalDate newSchedule;    // 새일정 [0..1]
    private String reason;            // 사유
    @Enumerated(EnumType.STRING)
    private ChangeRequestState state;
    private LocalDateTime requestedAt;
    private Long requesterId;

    protected ChangeRequest() {
    }

    ChangeRequest(User requester, String newScope, LocalDate newSchedule, String reason) {
        if (User.isBlank(newScope) && newSchedule == null) throw new DomainException(ErrorCode.REQUIRED_FIELD);
        if (User.isBlank(reason)) throw new DomainException(ErrorCode.REASON_REQUIRED);
        this.requesterId = requester.getId();
        this.newScope = newScope;
        this.newSchedule = newSchedule;
        this.reason = reason;
        this.state = ChangeRequestState.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    /** isRequestedBy (F_2, F_5) */
    public boolean isRequestedBy(User u) {
        return u != null && requesterId.equals(u.getId());
    }

    public boolean isPending() {
        return state == ChangeRequestState.PENDING;
    }

    public Long getId() { return id; }
    public String getNewScope() { return newScope; }
    public LocalDate getNewSchedule() { return newSchedule; }
    public String getReason() { return reason; }
    public ChangeRequestState getState() { return state; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}
