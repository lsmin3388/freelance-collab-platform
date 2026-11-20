package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 제안. 제출 후에는 고치거나 철회하지 않는다 (R5). 상태: 접수 → 선정 / 종료, 선정 → 결렬(New). */
@Entity
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "freelancer_id")
    private User freelancer;

    private int amount;                // 제안금액
    private int durationDays;          // 수행기간일수
    @Column(length = 4000)
    private String content;            // 제안내용
    private LocalDateTime submittedAt; // 제출시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalState state;

    protected Proposal() {
    }

    Proposal(Project project, User freelancer, int amount, int durationDays, String content) {
        this.project = project;
        this.freelancer = freelancer;
        this.amount = amount;
        this.durationDays = durationDays;
        this.content = content;
        this.submittedAt = LocalDateTime.now();
        this.state = ProposalState.RECEIVED;
    }

    /** isFrom (C_2): 이 제안의 제안자인가 */
    public boolean isFrom(User f) {
        return freelancer.isSameUser(f);
    }

    /** select (C_4): 접수 → 선정 */
    void select() {
        require(ProposalState.RECEIVED);
        state = ProposalState.SELECTED;
    }

    /** close (C_5): 접수 → 종료. 합의가 확정될 때 남은 접수 제안에 호출된다 (R2). */
    void close() {
        require(ProposalState.RECEIVED);
        state = ProposalState.CLOSED;
    }

    /** breakOff (D_4, New): 선정 → 결렬 */
    void breakOff() {
        require(ProposalState.SELECTED);
        state = ProposalState.BROKEN_OFF;
    }

    private void require(ProposalState expected) {
        if (state != expected) throw new DomainException(ErrorCode.PROPOSAL_NOT_SELECTABLE);
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public User getFreelancer() { return freelancer; }
    public int getAmount() { return amount; }
    public int getDurationDays() { return durationDays; }
    public String getContent() { return content; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public ProposalState getState() { return state; }
}
