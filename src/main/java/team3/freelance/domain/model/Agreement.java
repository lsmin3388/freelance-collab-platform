package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 작업 조건 합의. 선정된 제안 한 건에 대해 의뢰자와 선정 프리랜서가 합의안을 고치고 확인한다.
 * <ul>
 *   <li>edit: 개정번호 + 1, 양측 확인 해제 (R3)</li>
 *   <li>confirm: 보낸 개정번호가 현재와 같을 때만 유효. 상대가 이미 확인한 안을 확인하면 확정되고 버전 1이 생긴다.</li>
 *   <li>edit·confirm·breakOff는 협의 중일 때만</li>
 * </ul>
 */
@Entity
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;                 // 대상 (선정된 제안)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgreementState state;
    @Column(length = 4000)
    private String scope;                      // 작업범위
    private LocalDate schedule;                // 일정
    private String deliverableFormat;          // 산출물형식 (예: "pdf, png")
    private int revision;                      // 개정번호
    private boolean clientConfirmed;           // 의뢰자확인
    private boolean freelancerConfirmed;       // 프리랜서확인
    private String breakOffReason;             // 결렬사유 [0..1] (New)
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = false)
    @JoinColumn(name = "agreement_id")
    @OrderBy("versionNo")
    private List<AgreementVersion> versions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = false)
    @JoinColumn(name = "agreement_id")
    @OrderBy("id")
    private List<ChangeRequest> changeRequests = new ArrayList<>();

    protected Agreement() {
    }

    /** 선정 때 Project.selectProposal이 만든다. 초안은 프로젝트 내용으로 채운다 (R3). */
    Agreement(Proposal proposal, String scope, LocalDate schedule, String deliverableFormat) {
        this.proposal = proposal;
        this.state = AgreementState.NEGOTIATING;
        this.scope = scope;
        this.schedule = schedule;
        this.deliverableFormat = deliverableFormat;
        this.revision = 1;
        this.createdAt = LocalDateTime.now();
    }

    /** edit (D_1, D_2): 합의안 수정. 개정번호 + 1, 양측 확인 해제. */
    void edit(User u, String scope, LocalDate schedule, String deliverableFormat) {
        requireNegotiating();
        if (User.isBlank(scope) || schedule == null || parseFormats(deliverableFormat).isEmpty()) {
            throw new DomainException(ErrorCode.REQUIRED_FIELD);
        }
        this.scope = scope.trim();
        this.schedule = schedule;
        this.deliverableFormat = normalizeFormats(deliverableFormat);
        this.revision++;
        this.clientConfirmed = false;
        this.freelancerConfirmed = false;
    }

    /**
     * confirm (D_2, D_3, R3): u 쪽 확인을 표시한다.
     *
     * @return 이 확인으로 확정되었으면 true(버전 1 생성), 상대 확인을 기다리면 false
     */
    boolean confirm(User u, int revision) {
        requireNegotiating();
        if (revision != this.revision) throw new DomainException(ErrorCode.STALE_REVISION);
        boolean freelancerSide = proposal.isFrom(u);
        if (freelancerSide ? freelancerConfirmed : clientConfirmed) {
            throw new DomainException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (freelancerSide) freelancerConfirmed = true; else clientConfirmed = true;
        if (clientConfirmed && freelancerConfirmed) {
            versions.add(new AgreementVersion(versions.size() + 1, scope, schedule, deliverableFormat));
            state = AgreementState.CONFIRMED;
            return true;
        }
        return false;
    }

    /** breakOff (D_4, New): 협의 중 → 결렬 */
    void breakOff(String reason) {
        requireNegotiating();
        if (User.isBlank(reason)) throw new DomainException(ErrorCode.REASON_REQUIRED);
        this.breakOffReason = reason.trim();
        this.state = AgreementState.BROKEN_OFF;
    }

    /** addChangeRequest (F_1): 확정된 합의에만, 대기 요청은 1건까지 (R9) */
    ChangeRequest addChangeRequest(User u, String newScope, LocalDate newSchedule, String reason) {
        if (state != AgreementState.CONFIRMED) throw new DomainException(ErrorCode.INVALID_STATE);
        if (hasPendingChange()) throw new DomainException(ErrorCode.PENDING_CHANGE);
        ChangeRequest cr = new ChangeRequest(u, newScope, newSchedule, reason);
        changeRequests.add(cr);
        return cr;
    }

    /** hasPendingChange (F_1, G_1) */
    public boolean hasPendingChange() {
        return changeRequests.stream().anyMatch(ChangeRequest::isPending);
    }

    /** currentVersion (F_4): 가장 최근 버전. 확정 전이면 없음. */
    public Optional<AgreementVersion> currentVersion() {
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(versions.size() - 1));
    }

    /** getVersions (D_3) */
    public List<AgreementVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }

    public boolean isActive() {
        return state == AgreementState.NEGOTIATING || state == AgreementState.CONFIRMED;
    }

    public boolean isConfirmedBy(User u) {
        return proposal.isFrom(u) ? freelancerConfirmed : clientConfirmed;
    }

    private void requireNegotiating() {
        if (state != AgreementState.NEGOTIATING) throw new DomainException(ErrorCode.INVALID_STATE);
    }

    /** "PDF, png / zip" → {pdf, png, zip}. 산출물형식은 파일 확장자를 쉼표로 적는다. */
    public static Set<String> parseFormats(String formats) {
        Set<String> out = new LinkedHashSet<>();
        if (formats == null) return out;
        for (String t : formats.toLowerCase().split("[^a-z0-9]+")) {
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    static String normalizeFormats(String formats) {
        return String.join(", ", parseFormats(formats));
    }

    public Long getId() { return id; }
    public Proposal getProposal() { return proposal; }
    public AgreementState getState() { return state; }
    public String getScope() { return scope; }
    public LocalDate getSchedule() { return schedule; }
    public String getDeliverableFormat() { return deliverableFormat; }
    public int getRevision() { return revision; }
    public boolean isClientConfirmed() { return clientConfirmed; }
    public boolean isFreelancerConfirmed() { return freelancerConfirmed; }
    public String getBreakOffReason() { return breakOffReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ChangeRequest> getChangeRequests() { return Collections.unmodifiableList(changeRequests); }
}
