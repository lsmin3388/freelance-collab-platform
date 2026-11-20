package team3.freelance.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team3.freelance.data.external.NotificationService;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.domain.model.*;
import team3.freelance.domain.service.ReadModels.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 합의 서비스 (Added). 합의안 수정·확인·결렬(New)과 버전 조회 ― 핵심 시나리오 2.
 * 의뢰자와 선정 프리랜서가 합의안을 번갈아 고치고, 같은 개정번호를 양쪽이 확인하면 확정된다.
 */
@Service
public class AgreementService {

    private static final Logger log = LoggerFactory.getLogger(AgreementService.class);

    private final AccessGuard guard;
    private final ProjectRepository projects;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public AgreementService(AccessGuard guard, ProjectRepository projects, NotificationService notificationService,
                            PlatformTransactionManager txManager) {
        this.guard = guard;
        this.projects = projects;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 합의 화면 조회: 현재 합의안, 양측 확인 여부, 버전 목록, 지난 결렬 기록 (D_3) */
    @Transactional(readOnly = true)
    public AgreementPage getAgreement(LoginSession s, Long projectId) {
        User u = guard.currentUser(s);
        Project p = projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!p.isOwnedBy(u) && !p.isAssignedTo(u)) throw new DomainException(ErrorCode.NOT_PARTICIPANT);
        ProjectDetail detail = ProjectDetail.of(p, u);
        List<String> brokenOff = p.getAgreements().stream()
                .filter(a -> a.getState() == AgreementState.BROKEN_OFF)
                .map(a -> a.getProposal().getFreelancer().getName() + "님과 결렬: " + a.getBreakOffReason())
                .toList();
        Agreement a = p.getAgreements().stream().filter(Agreement::isActive).findFirst().orElse(null);
        if (a == null) {
            return new AgreementPage(detail, false, null, null, null, null, 0, false, false, false, false,
                    null, List.of(), brokenOff);
        }
        List<VersionRow> versions = a.getVersions().stream()
                .sorted(Comparator.comparingInt(AgreementVersion::getVersionNo).reversed())
                .map(v -> new VersionRow(v.getVersionNo(), v.getScope(), v.getSchedule(), v.getDeliverableFormat(),
                        v.getConfirmedAt())).toList();
        return new AgreementPage(detail, true, a.getState().label(), a.getScope(), a.getSchedule(),
                a.getDeliverableFormat(), a.getRevision(), a.isClientConfirmed(), a.isFreelancerConfirmed(),
                a.isConfirmedBy(u), a.getState() == AgreementState.NEGOTIATING,
                a.getProposal().getFreelancer().getName(), versions, brokenOff);
    }

    /** editAgreement(세션, projectId, 작업범위, 일정, 산출물형식) (D_1, D_2, R3) */
    public ActionResult editAgreement(LoginSession s, Long projectId, String scope, LocalDate schedule, String format) {
        Integer revision = tx.execute(status -> {                               // 하나의 저장 단위
            User u = guard.currentUser(s);
            Project p = lockAndLoad(projectId);
            Agreement a = p.editAgreement(u, scope, schedule, format);
            projects.save(p);
            return a.getRevision();
        });
        return ActionResult.of("합의안을 고쳤습니다(개정 " + revision + "). 양측 확인이 해제되어 둘 다 다시 확인해야 합니다.", List.of());
    }

    /** confirmAgreement(세션, projectId, 개정번호) (D_2, D_3, C_5, R3) */
    public ActionResult confirmAgreement(LoginSession s, Long projectId, int revision) {
        record Confirmed(User client, User freelancer, ConfirmOutcome outcome, String title) {
        }
        Confirmed r = tx.execute(status -> {                                   // 하나의 저장 단위
            User u = guard.currentUser(s);
            Project p = lockAndLoad(projectId);
            ConfirmOutcome outcome = p.confirmAgreement(u, revision);
            projects.save(p);
            User freelancer = p.selectedProposal().map(Proposal::getFreelancer).orElseThrow();
            return new Confirmed(p.getClient(), freelancer, outcome, p.getTitle());
        });
        if (!r.outcome().confirmed()) {
            return ActionResult.of("개정 " + revision + "을 확인했습니다. 상대방이 같은 개정을 확인하면 확정됩니다.", List.of());
        }
        List<String> notes = new ArrayList<>();
        // par: 확정 통지 (의뢰자, 선정 프리랜서)
        notify(r.client(), "작업 시작", "[" + r.title() + "] 작업 조건이 확정되었습니다(버전 1).", notes);
        notify(r.freelancer(), "작업 시작", "[" + r.title() + "] 작업 조건이 확정되었습니다(버전 1). 작업을 시작해 주세요.", notes);
        for (User other : r.outcome().closedProposers()) {                     // loop 종료된 제안마다
            notify(other, "미선정 종료", "[" + r.title() + "] 다른 제안과 합의가 확정되어 제안이 종료되었습니다.", notes);
        }
        return ActionResult.of("작업 조건이 확정되었습니다(버전 1). 프로젝트가 '작업 중'이 되었습니다.", notes);
    }

    /** breakOff(세션, projectId, 사유) (D_4, New, R4) */
    public ActionResult breakOff(LoginSession s, Long projectId, String reason) {
        record Broken(User counterpart, String title) {
        }
        Broken r = tx.execute(status -> {                                      // 하나의 저장 단위
            User u = guard.currentUser(s);
            Project p = lockAndLoad(projectId);
            User counterpart = p.breakOffAgreement(u, reason);
            projects.save(p);
            return new Broken(counterpart, p.getTitle());
        });
        List<String> notes = new ArrayList<>();
        notify(r.counterpart(), "합의 결렬", "[" + r.title() + "] 작업 조건 합의가 결렬되었습니다. 사유: " + reason, notes);
        return ActionResult.of("합의가 결렬되었습니다. 프로젝트가 다시 '모집 중'이 되었습니다.", notes);
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
