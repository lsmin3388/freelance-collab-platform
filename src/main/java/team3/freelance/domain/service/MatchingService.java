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

import java.util.ArrayList;
import java.util.List;

/**
 * 매칭 서비스 (Added). 제안 제출, 제안 목록, 제안 선정과 선정 통지.
 * 상태를 바꾸는 요청은 [권한 확인 → 잠금 조회 → Model 연산 → 저장]을 하나의 저장 단위(트랜잭션)로 묶고,
 * 커밋이 끝난 뒤에 알림(Mock)을 호출한다.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final AccessGuard guard;
    private final ProjectRepository projects;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public MatchingService(AccessGuard guard, ProjectRepository projects, NotificationService notificationService,
                           PlatformTransactionManager txManager) {
        this.guard = guard;
        this.projects = projects;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(txManager);
    }

    /** submitProposal(세션, projectId, ...) : 제안결과 (B.1, C_1, C_2, R5) */
    @Transactional
    public Long submitProposal(LoginSession s, Long projectId, int amount, int durationDays, String content) {
        User f = guard.requireRole(s, Role.FREELANCER);
        Project p = load(projectId);
        Proposal proposal = p.receiveProposal(f, amount, durationDays, content);
        projects.save(p);
        projects.flush();
        return proposal.getId();
    }

    /** listProposals(세션, projectId, 정렬) (C_3, B.2 단계 1~2) */
    @Transactional(readOnly = true)
    public ProposalListPage listProposals(LoginSession s, Long projectId, ProposalSort sort) {
        User c = guard.currentUser(s);
        Project p = projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        boolean recruiting = p.getState() == ProjectState.RECRUITING;
        List<ProposalRow> rows = p.getProposals(c, sort).stream().map(pr -> new ProposalRow(
                pr.getId(), pr.getFreelancer().getName(), pr.getFreelancer().getExpertise(),
                pr.getFreelancer().getCareerYears(), pr.getAmount(), pr.getDurationDays(), pr.getContent(),
                pr.getSubmittedAt(), pr.getState().label(),
                recruiting && pr.getState() == ProposalState.RECEIVED)).toList();
        return new ProposalListPage(ProjectDetail.of(p, c), rows, sort);
    }

    /**
     * selectProposal(세션, projectId, proposalId) (B.2, C_4, C_5, D_1, R2) ― 핵심 시나리오 1.
     */
    public ActionResult selectProposal(LoginSession s, Long projectId, Long proposalId) {
        record Selected(User client, User freelancer, String title) {
        }
        Selected r = tx.execute(status -> {                                      // 하나의 저장 단위
            User c = guard.requireRole(s, Role.CLIENT);                          // 1.1
            Project p = lockAndLoad(projectId);                                  // 1.2
            Proposal sel = p.getProposals(c, ProposalSort.SUBMITTED_AT).stream() // 1.3 (의뢰자 확인 포함)
                    .filter(x -> x.getId().equals(proposalId)).findFirst().orElse(null);
            p.selectProposal(c, sel);                                            // 1.4
            projects.save(p);                                                    // 1.5
            return new Selected(c, sel.getFreelancer(), p.getTitle());
        });
        List<String> notes = new ArrayList<>();                                  // par: 커밋 뒤 통지
        notify(r.client(), "선정 완료", "[" + r.title() + "] " + r.freelancer().getName() + "님의 제안을 선정했습니다.", notes);
        notify(r.freelancer(), "선정됨", "[" + r.title() + "] 제안이 선정되었습니다. 작업 조건을 합의해 주세요.", notes);
        return ActionResult.of(r.freelancer().getName() + "님의 제안을 선정했습니다. 작업 조건을 합의해 주세요.", notes);
    }

    /** getMyProposals(세션) */
    @Transactional(readOnly = true)
    public List<MyProposal> getMyProposals(LoginSession s) {
        User f = guard.currentUser(s);
        return projects.findProposalsByFreelancer(f.getId()).stream()
                .map(pr -> new MyProposal(pr.getProject().getId(), pr.getProject().getTitle(), pr.getAmount(),
                        pr.getDurationDays(), pr.getState().label(), pr.getProject().getState().label()))
                .toList();
    }

    private Project load(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private Project lockAndLoad(Long projectId) {
        return projects.findByIdForUpdate(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    /** 알림(Mock) 호출. 실패해도 선정은 유지하고 안내만 남긴다 (B.2 8a). */
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
