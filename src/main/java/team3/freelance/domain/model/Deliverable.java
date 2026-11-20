package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 결과물(회차). 상태: 검토 대기 → 승인 / 수정 요청. 이전 회차는 지우지 않고 쌓는다. */
@Entity
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private int round;                      // 회차번호
    @Column(length = 4000)
    private String virtualInfo;             // 가상결과물정보 [0..1]
    @Column(length = 4000)
    private String memo;                    // 제출메모
    private LocalDateTime submittedAt;      // 제출시각
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliverableState state;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "deliverable_id")
    @OrderBy("id")
    private List<Attachment> attachments = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "feedback_id")
    private Feedback feedback;              // [0..1]

    protected Deliverable() {
    }

    Deliverable(int round, List<FileInfo> files, String virtualInfo, String memo) {
        this.round = round;
        this.virtualInfo = User.isBlank(virtualInfo) ? null : virtualInfo.trim();
        this.memo = memo;
        this.submittedAt = LocalDateTime.now();
        this.state = DeliverableState.PENDING_REVIEW;
        for (FileInfo f : files) attachments.add(new Attachment(f));   // loop 파일마다 <<create>>
    }

    /** approve (H_2): 검토 대기 → 승인 */
    void approve() {
        requirePending();
        state = DeliverableState.APPROVED;
    }

    /** requestRevision (H_2, H_3): 검토 대기 → 수정 요청. 피드백 내용이 반드시 있어야 한다. */
    Feedback requestRevision(String content) {
        requirePending();
        if (User.isBlank(content)) throw new DomainException(ErrorCode.FEEDBACK_REQUIRED);
        feedback = new Feedback(content.trim());
        state = DeliverableState.REVISION_REQUESTED;
        return feedback;
    }

    /** getFeedback (G_3, H_4) */
    public Optional<Feedback> getFeedback() {
        return Optional.ofNullable(feedback);
    }

    private void requirePending() {
        if (state != DeliverableState.PENDING_REVIEW) throw new DomainException(ErrorCode.INVALID_STATE);
    }

    public Long getId() { return id; }
    public int getRound() { return round; }
    public String getVirtualInfo() { return virtualInfo; }
    public String getMemo() { return memo; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public DeliverableState getState() { return state; }
    public List<Attachment> getAttachments() { return Collections.unmodifiableList(attachments); }
}
