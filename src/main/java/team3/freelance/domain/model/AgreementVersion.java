package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 합의 버전. 확정 시점의 합의 내용을 고정한 기록이며 생성 뒤에는 바뀌지 않는다 (setter 없음). */
@Entity
public class AgreementVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private int versionNo;              // 버전번호
    @Column(length = 4000, updatable = false)
    private String scope;               // 작업범위
    @Column(updatable = false)
    private LocalDate schedule;         // 일정
    @Column(updatable = false)
    private String deliverableFormat;   // 산출물형식
    @Column(updatable = false)
    private LocalDateTime confirmedAt;  // 확정시각

    protected AgreementVersion() {
    }

    AgreementVersion(int versionNo, String scope, LocalDate schedule, String deliverableFormat) {
        this.versionNo = versionNo;
        this.scope = scope;
        this.schedule = schedule;
        this.deliverableFormat = deliverableFormat;
        this.confirmedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public int getVersionNo() { return versionNo; }
    public String getScope() { return scope; }
    public LocalDate getSchedule() { return schedule; }
    public String getDeliverableFormat() { return deliverableFormat; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}
