package team3.freelance.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team3.freelance.data.external.NotificationService;
import team3.freelance.data.external.PaymentService;
import team3.freelance.data.repository.FileStorage;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.domain.model.*;
import team3.freelance.domain.service.ReadModels.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 결과물 서비스 (Added). 결과물 제출(핵심 시나리오 3), 검토·피드백과 완료 처리(핵심 시나리오 4).
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final AccessGuard guard;
    private final ProjectRepository projects;
    private final FileStorage fileStorage;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public DeliveryService(AccessGuard guard, ProjectRepository projects, FileStorage fileStorage,
                           PaymentService paymentService, NotificationService notificationService,
                           PlatformTransactionManager txManager) {
        this.guard = guard;
        this.projects = projects;
        this.fileStorage = fileStorage;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 제출 화면 조회 (B.3 단계 1~2, S-1): 제출 가능 여부, 합의 기준, 직전 회차 피드백 */
    @Transactional(readOnly = true)
    public SubmitForm getSubmitForm(LoginSession s, Long projectId) {
        User f = guard.currentUser(s);
        Project p = load(projectId);
        SubmitEligibility e = p.canSubmit(f);
        if (e.reason() == ErrorCode.NOT_ASSIGNED) throw new DomainException(ErrorCode.NOT_ASSIGNED);   // B.3 2b
        AgreementVersion v = p.activeAgreement().currentVersion().orElse(null);
        Deliverable prev = p.latestDeliverable().orElse(null);
        String prevFeedback = prev == null ? null : prev.getFeedback().map(Feedback::getContent).orElse(null);
        return new SubmitForm(ProjectDetail.of(p, f), e.allowed(), e.allowed() ? null : e.reason().message(),
                p.getDeliverables().size() + 1,
                v == null ? "" : v.getDeliverableFormat(), v == null ? null : v.getScope(),
                v == null ? null : v.getSchedule(),
                prevFeedback == null ? null : prev.getRound(), prevFeedback);
    }

    /**
     * submit(세션, projectId, 파일[0..*], 가상정보, 메모) : 제출결과 (B.3, G_1~G_3, R6) ― 핵심 시나리오 3.
     * 파일 저장과 회차 부여를 하나의 저장 단위로 처리한다. 실패하면 회차가 부여되지 않는다 (B.3 7a).
     */
    public ActionResult submit(LoginSession s, Long projectId, List<UploadedFile> files, String virtualInfo, String memo) {
        List<UploadedFile> fs = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        Integer round = tx.execute(status -> {                                  // 하나의 저장 단위
            User f = guard.requireRole(s, Role.FREELANCER);                     // 1.1
            Project p = lockAndLoad(projectId);                                 // 1.2
            SubmitEligibility e = p.canSubmit(f);                               // 1.3 (B.3 단계 2 다시 확인)
            if (!e.allowed()) throw new DomainException(e.reason());
            List<FileInfo> infos = new ArrayList<>();
            for (UploadedFile u : fs) {                                          // loop 파일마다
                String key = fileStorage.store(u.content());                    // 1.4
                infos.add(new FileInfo(u.fileName(), FileInfo.formatOf(u.fileName()), u.content().length, key));
            }
            Deliverable d = p.submitDeliverable(f, infos, virtualInfo, memo);   // 1.5
            projects.save(p);                                                   // 1.6
            return d.getRound();
        });
        return ActionResult.of(round + "회차 결과물을 제출했습니다. 의뢰자 검토를 기다립니다.", List.of());
    }

    /** listDeliverables(세션, projectId): 회차·피드백 이력 (H_1, H_4). 참여자만. */
    @Transactional(readOnly = true)
    public ReviewPage listDeliverables(LoginSession s, Long projectId) {
        User u = guard.currentUser(s);
        Project p = load(projectId);
        if (!p.isOwnedBy(u) && !p.isAssignedTo(u)) throw new DomainException(ErrorCode.NOT_PARTICIPANT);
        List<DeliverableRow> rows = p.getDeliverables().stream().map(DeliverableRow::of).toList().reversed();
        boolean owner = p.isOwnedBy(u);
        Integer amount = p.selectedProposal().map(Proposal::getAmount).orElse(null);
        SettlementRow st = p.getSettlement().map(x -> new SettlementRow(x.getAmount(), x.getTargetRound(),
                x.getState().label(), x.getProcessedAt())).orElse(null);
        return new ReviewPage(ProjectDetail.of(p, u), rows,
                owner && p.getState() == ProjectState.IN_REVIEW,
                owner && p.getState() == ProjectState.APPROVED, amount, st);
    }

    /**
     * review(세션, projectId, 결정, 피드백) (H_2, H_3, R7) ― 핵심 시나리오 4.
     * 승인 → 승인됨, 수정 요청(피드백 필수) → 수정 중.
     */
    public ActionResult review(LoginSession s, Long projectId, ReviewDecision decision, String feedback) {
        Integer round = tx.execute(status -> {                                  // 하나의 저장 단위
            User c = guard.requireRole(s, Role.CLIENT);
            Project p = lockAndLoad(projectId);
            Deliverable d = p.reviewLatest(c, decision, feedback);
            projects.save(p);
            return d.getRound();
        });
        String msg = decision == ReviewDecision.APPROVE
                ? round + "회차를 승인했습니다. 완료 처리를 할 수 있습니다."
                : round + "회차에 수정을 요청했습니다. 프리랜서가 피드백을 보고 다시 제출합니다.";
        return ActionResult.of(msg, List.of());
    }

    /**
     * complete(세션, projectId) : 완료결과 (B.4, I_1~I_3, R8, R15).
     * ① 완료 전이 + 정산 기록(대기)을 한 저장 단위로 커밋 ② 정산(Mock) ③ 결과 기록 ④ 완료 통지.
     * ②~④가 실패해도 '완료'는 되돌리지 않는다 (B.4 8a, 9a).
     */
    public ActionResult complete(LoginSession s, Long projectId) {
        record Completed(int amount, User freelancer, String title) {
        }
        Completed c = tx.execute(status -> {                                    // 하나의 저장 단위
            User u = guard.requireRole(s, Role.CLIENT);
            Project p = lockAndLoad(projectId);
            Settlement st = p.complete(u);
            projects.save(p);
            User f = p.selectedProposal().map(Proposal::getFreelancer).orElseThrow();
            return new Completed(st.getAmount(), f, p.getTitle());
        });
        List<String> notes = new ArrayList<>();
        boolean paid;
        try {
            paid = paymentService.settle(projectId, c.amount());               // 정산 (Mock)
        } catch (RuntimeException e) {
            log.warn("정산 호출 실패: project {}", projectId, e);
            paid = false;
        }
        final boolean result = paid;
        try {
            tx.executeWithoutResult(status -> {                                  // 정산 결과 기록
                Project p = lockAndLoad(projectId);
                p.recordSettlement(result);
                projects.save(p);
            });
        } catch (RuntimeException e) {
            log.warn("정산 결과 기록 실패: project {}", projectId, e);
            notes.add("정산 결과를 기록하지 못해 정산 기록이 '대기'로 남았습니다.");
        }
        if (!paid) notes.add("정산이 실패해 정산 기록에 '실패'로 남겼습니다. 프로젝트는 '완료' 상태를 유지합니다.");
        notify(c.freelancer(), "완료", "[" + c.title() + "] 프로젝트가 완료 처리되었습니다. 정산 "
                + (paid ? "성공" : "실패") + ".", notes);
        return ActionResult.of("프로젝트를 완료 처리했습니다. 정산 금액 " + String.format("%,d원", c.amount()) + ".", notes);
    }

    private Project load(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private Project lockAndLoad(Long projectId) {
        return projects.findByIdForUpdate(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void notify(User to, String type, String content, List<String> notes) {
        boolean ok;
        try {
            ok = notificationService.send(String.valueOf(to.getId()), type, content);
        } catch (RuntimeException e) {
            log.warn("통지 호출 실패: {} {}", to.getId(), type, e);
            ok = false;
        }
        if (!ok) notes.add(to.getName() + "님에게 '" + type + "' 통지를 보내지 못했습니다. 처리 결과는 그대로 유지됩니다.");
    }
}
