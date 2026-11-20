package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 프로젝트. 제안·합의·결과물·정산·활동 이력을 묶는 저장 단위의 루트이다.
 * 프로젝트와 그 구성 객체의 상태는 이 클래스의 연산을 거쳐서만 바뀌고, 상태 전이마다 활동 이력을 남긴다.
 * 모든 연산은 검사를 먼저 끝낸 뒤 상태를 바꾼다. 그래서 예외가 나면 객체는 바뀌지 않은 채로 남는다.
 */
@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                          // 프로젝트ID

    @Version
    private long version;                     // 동시 수정 감지용 (설계 외 기술 속성)

    @Column(nullable = false)
    private String title;                     // 제목
    private String field;                     // 작업분야
    @Column(length = 4000)
    private String description;               // 작업내용
    private String expectedOutput;            // 기대결과물
    private int budgetMin;                    // 예산범위(하한)
    private int budgetMax;                    // 예산범위(상한)
    private LocalDate dueDate;                // 희망마감일
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectState state;               // 상태
    private LocalDateTime createdAt;          // 등록시각

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id")
    private User client;                      // 등록한 의뢰자

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Proposal> proposals = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "project_id")
    @OrderBy("id")
    private List<Agreement> agreements = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "project_id")
    @OrderBy("round")
    private List<Deliverable> deliverables = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "project_id")
    @OrderBy("id")
    private List<HistoryEntry> history = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;            // [0..1]

    protected Project() {
    }

    /** Client.registerProject가 만든다 (A_1). */
    Project(User client, String title, String field, String description, String expectedOutput,
            int budgetMin, int budgetMax, LocalDate dueDate) {
        if (User.isBlank(title) || User.isBlank(description) || dueDate == null) {
            throw new DomainException(ErrorCode.REQUIRED_FIELD);
        }
        if (budgetMin < 0 || budgetMax < budgetMin) throw new DomainException(ErrorCode.INVALID_AMOUNT);
        this.client = client;
        this.title = title.trim();
        this.field = field;
        this.description = description.trim();
        this.expectedOutput = expectedOutput;
        this.budgetMin = budgetMin;
        this.budgetMax = budgetMax;
        this.dueDate = dueDate;
        this.state = ProjectState.RECRUITING;
        this.createdAt = LocalDateTime.now();
        history.add(new HistoryEntry(HistoryType.STATE_TRANSITION, client, "프로젝트 등록", null, state));
    }

    // ------------------------------------------------------------------ 권한 판단 (M_4)

    /** isOwnedBy: 이 프로젝트를 등록한 의뢰자인가 */
    public boolean isOwnedBy(User u) {
        return client.isSameUser(u);
    }

    /** isAssignedTo: 선정된(상태=선정) 제안의 제안자인가. 결렬되면 다시 아무도 아니다. */
    public boolean isAssignedTo(User u) {
        return selectedProposal().map(p -> p.isFrom(u)).orElse(false);
    }

    // ------------------------------------------------------------------ 3.1 제안과 선정

    /** receiveProposal (C_1, C_2, R5, B.1 6a) */
    public Proposal receiveProposal(User f, int amount, int durationDays, String content) {
        requireState(ProjectState.RECRUITING);
        if (!f.hasRole(Role.FREELANCER)) throw new DomainException(ErrorCode.ROLE_REQUIRED);
        if (isOwnedBy(f)) throw new DomainException(ErrorCode.OWN_PROJECT);
        if (hasProposalFrom(f)) throw new DomainException(ErrorCode.DUPLICATE_PROPOSAL);
        if (amount <= 0 || durationDays <= 0) throw new DomainException(ErrorCode.INVALID_AMOUNT);
        if (LocalDate.now().plusDays(durationDays).isAfter(dueDate)) {
            throw new DomainException(ErrorCode.DEADLINE_EXCEEDED);
        }
        Proposal p = new Proposal(this, f, amount, durationDays, content);
        proposals.add(p);
        return p;
    }

    /** hasProposalFrom (C_2): 상태와 무관하게 이 프리랜서의 제안이 있는가 */
    public boolean hasProposalFrom(User f) {
        return proposals.stream().anyMatch(p -> p.isFrom(f));
    }

    /** getProposals (C_3): 의뢰자만 본다. */
    public List<Proposal> getProposals(User u, ProposalSort sort) {
        requireOwner(u);
        List<Proposal> list = new ArrayList<>(proposals);
        list.sort(sort.comparator());
        return list;
    }

    /**
     * selectProposal (C_4, C_5, D_1, R2): 제안 선정.
     * 선정 제안 → '선정', 합의 초안(협의 중, 개정 1) 생성, 프로젝트 → '조건 합의 중'.
     * 나머지 접수 제안은 그대로 둔다(합의 확정 때 종료).
     */
    public Agreement selectProposal(User u, Proposal p) {
        requireOwner(u);
        requireState(ProjectState.RECRUITING);
        if (p == null || !proposals.contains(p) || p.getState() != ProposalState.RECEIVED) {
            throw new DomainException(ErrorCode.PROPOSAL_NOT_SELECTABLE);
        }
        p.select();
        Agreement a = new Agreement(p, description, dueDate, Agreement.normalizeFormats(expectedOutput));
        agreements.add(a);
        changeState(ProjectState.NEGOTIATING, u, "제안 선정: " + p.getFreelancer().getName());
        return a;
    }

    // ------------------------------------------------------------------ 3.2 작업 조건 합의

    /** editAgreement (D_1, D_2): 의뢰자·선정 프리랜서 누구나 합의안을 고칠 수 있다. */
    public Agreement editAgreement(User u, String scope, LocalDate schedule, String deliverableFormat) {
        requireParticipant(u);
        requireState(ProjectState.NEGOTIATING);
        Agreement a = activeAgreement();
        a.edit(u, scope, schedule, deliverableFormat);
        record(HistoryType.AGREEMENT, u, "합의안 수정 (개정 " + a.getRevision() + ")");
        return a;
    }

    /**
     * confirmAgreement (D_2, D_3, C_5, R3): 화면에서 본 개정번호로 확인한다.
     * 상대가 이미 확인했으면 확정: 버전 1 생성, 남은 접수 제안 종료, 프로젝트 → '작업 중'.
     */
    public ConfirmOutcome confirmAgreement(User u, int revision) {
        requireParticipant(u);
        requireState(ProjectState.NEGOTIATING);
        Agreement a = activeAgreement();
        boolean confirmed = a.confirm(u, revision);
        record(HistoryType.AGREEMENT, u, "합의안 확인 (개정 " + revision + ")");
        if (!confirmed) return new ConfirmOutcome(false, List.of());

        List<User> closed = new ArrayList<>();
        for (Proposal o : proposals) {                       // loop 다른 접수 제안마다
            if (o.getState() == ProposalState.RECEIVED) {
                o.close();
                closed.add(o.getFreelancer());
            }
        }
        changeState(ProjectState.IN_PROGRESS, u, "합의 확정 (버전 1)");
        return new ConfirmOutcome(true, closed);
    }

    /**
     * breakOffAgreement (D_4, New): 합의 결렬. 합의·선정 제안 → '결렬', 프로젝트 → '모집 중'.
     *
     * @return 결렬을 통지할 상대방
     */
    public User breakOffAgreement(User u, String reason) {
        requireParticipant(u);
        requireState(ProjectState.NEGOTIATING);
        Agreement a = activeAgreement();
        Proposal sel = a.getProposal();
        User counterpart = isOwnedBy(u) ? sel.getFreelancer() : client;
        a.breakOff(reason);
        sel.breakOff();
        changeState(ProjectState.RECRUITING, u, "합의 결렬: " + reason.trim());
        return counterpart;
    }

    /** 유효한 합의(협의 중·확정). Project당 최대 1건. */
    public Agreement activeAgreement() {
        return agreements.stream().filter(Agreement::isActive).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NO_ACTIVE_AGREEMENT));
    }

    /** requestChange (F_1): 이번 구현에서는 R6 검사를 위해 Model 연산만 둔다. */
    public ChangeRequest requestChange(User u, String newScope, LocalDate newSchedule, String reason) {
        requireParticipant(u);
        if (state != ProjectState.IN_PROGRESS && state != ProjectState.REVISING) {
            throw new DomainException(ErrorCode.INVALID_STATE);         // R9: 검토 중에는 받지 않음
        }
        ChangeRequest cr = activeAgreement().addChangeRequest(u, newScope, newSchedule, reason);
        record(HistoryType.CHANGE_REQUEST, u, "변경 요청: " + reason);
        return cr;
    }

    // ------------------------------------------------------------------ 3.3 결과물 제출

    /** canSubmit (G_1, G_3, R6): 배정된 프리랜서 + (작업 중 | 수정 중) + 대기 변경 요청 없음 */
    public SubmitEligibility canSubmit(User u) {
        if (!isAssignedTo(u)) return SubmitEligibility.deny(ErrorCode.NOT_ASSIGNED);
        if (state != ProjectState.IN_PROGRESS && state != ProjectState.REVISING) {
            return SubmitEligibility.deny(ErrorCode.INVALID_STATE);
        }
        if (activeAgreement().hasPendingChange()) return SubmitEligibility.deny(ErrorCode.PENDING_CHANGE);
        return SubmitEligibility.OK;
    }

    /**
     * submitDeliverable (G_1, G_2, G_3, B.3): 회차 = 직전 + 1, 검토 대기로 만들고 프로젝트 → '검토 중'.
     * 파일 형식은 현재 합의 버전의 산출물형식이어야 한다.
     */
    public Deliverable submitDeliverable(User u, List<FileInfo> files, String virtualInfo, String memo) {
        SubmitEligibility e = canSubmit(u);                            // 저장 직전에 다시 확인
        if (!e.allowed()) throw new DomainException(e.reason());
        List<FileInfo> fs = files == null ? List.of() : files;
        if (fs.isEmpty() && User.isBlank(virtualInfo)) throw new DomainException(ErrorCode.EMPTY_SUBMISSION);
        Set<String> allowed = Agreement.parseFormats(activeAgreement().currentVersion()
                .map(AgreementVersion::getDeliverableFormat).orElse(""));
        for (FileInfo f : fs) {
            if (!allowed.contains(f.format())) {
                throw new DomainException(ErrorCode.FORMAT_NOT_ALLOWED,
                        "합의한 산출물 형식(" + String.join(", ", allowed) + ")이 아닌 파일입니다: " + f.fileName());
            }
        }
        int round = deliverables.size() + 1;
        Deliverable d = new Deliverable(round, fs, virtualInfo, memo);
        deliverables.add(d);
        changeState(ProjectState.IN_REVIEW, u, round + "회차 제출");
        return d;
    }

    /** getDeliverables (H_1, H_4): 회차 순 */
    public List<Deliverable> getDeliverables() {
        return Collections.unmodifiableList(deliverables);
    }

    public Optional<Deliverable> latestDeliverable() {
        return deliverables.isEmpty() ? Optional.empty() : Optional.of(deliverables.get(deliverables.size() - 1));
    }

    // ------------------------------------------------------------------ 3.3 검토와 완료

    /**
     * reviewLatest (H_2, H_3, R7): '검토 중'일 때 최신 회차만 검토한다.
     * 승인 → 프로젝트 '승인됨', 수정 요청(피드백 필수) → 프로젝트 '수정 중'.
     */
    public Deliverable reviewLatest(User u, ReviewDecision decision, String feedback) {
        requireOwner(u);
        requireState(ProjectState.IN_REVIEW);
        Deliverable d = latestDeliverable().orElseThrow(() -> new DomainException(ErrorCode.INVALID_STATE));
        if (decision == ReviewDecision.APPROVE) {
            d.approve();
            changeState(ProjectState.APPROVED, u, d.getRound() + "회차 승인");
        } else {
            d.requestRevision(feedback);                            // 피드백 없으면 FEEDBACK_REQUIRED
            changeState(ProjectState.REVISING, u, d.getRound() + "회차 수정 요청");
        }
        return d;
    }

    /** complete (I_1, I_2, R8): '승인됨'에서만. 정산 기록(대기)을 만들고 프로젝트 → '완료'. */
    public Settlement complete(User u) {
        requireOwner(u);
        requireState(ProjectState.APPROVED);
        Deliverable approved = latestDeliverable().orElseThrow();
        int amount = selectedProposal().map(Proposal::getAmount).orElseThrow();
        settlement = new Settlement(amount, approved.getRound());
        changeState(ProjectState.COMPLETED, u, "완료 처리 (" + approved.getRound() + "회차)");
        return settlement;
    }

    /** recordSettlement (I_3): 정산 서비스(Mock) 결과를 한 번 기록한다. 완료 상태는 바뀌지 않는다. */
    public void recordSettlement(boolean success) {
        if (settlement == null) throw new DomainException(ErrorCode.INVALID_STATE);
        settlement.record(success);
    }

    // ------------------------------------------------------------------ 조회

    /** getHistory (F_4): 발생 순 */
    public List<HistoryEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public Optional<Proposal> selectedProposal() {
        return proposals.stream().filter(p -> p.getState() == ProposalState.SELECTED).findFirst();
    }

    public Optional<Settlement> getSettlement() {
        return Optional.ofNullable(settlement);
    }

    public List<Agreement> getAgreements() {
        return Collections.unmodifiableList(agreements);
    }

    // ------------------------------------------------------------------ 내부 연산

    /** -changeState: 상태를 바꾸고 상태 전이 이력을 남긴다. */
    private void changeState(ProjectState next, User u, String content) {
        ProjectState prev = state;
        state = next;
        history.add(new HistoryEntry(HistoryType.STATE_TRANSITION, u, content, prev, next));
    }

    /** -record: 상태 전이가 아닌 주요 행위를 남긴다. */
    private void record(HistoryType type, User u, String content) {
        history.add(new HistoryEntry(type, u, content, state, state));
    }

    private void requireOwner(User u) {
        if (!isOwnedBy(u)) throw new DomainException(ErrorCode.NOT_OWNER);
    }

    private void requireParticipant(User u) {
        if (!isOwnedBy(u) && !isAssignedTo(u)) throw new DomainException(ErrorCode.NOT_PARTICIPANT);
    }

    private void requireState(ProjectState expected) {
        if (state != expected) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "지금 프로젝트 상태(" + state.label() + ")에서는 할 수 없습니다. '" + expected.label() + "'일 때만 가능합니다.");
        }
    }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getTitle() { return title; }
    public String getField() { return field; }
    public String getDescription() { return description; }
    public String getExpectedOutput() { return expectedOutput; }
    public int getBudgetMin() { return budgetMin; }
    public int getBudgetMax() { return budgetMax; }
    public LocalDate getDueDate() { return dueDate; }
    public ProjectState getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public User getClient() { return client; }
}
