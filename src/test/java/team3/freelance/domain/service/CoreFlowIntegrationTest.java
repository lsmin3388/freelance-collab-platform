package team3.freelance.domain.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import team3.freelance.data.external.NotificationLog;
import team3.freelance.data.external.NotificationLogRepository;
import team3.freelance.data.external.NotificationService;
import team3.freelance.data.external.PaymentService;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.domain.model.*;
import team3.freelance.domain.service.ReadModels.ActionResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계층 전체(Service → Model → Repository → Mock)를 거치는 핵심 시나리오 시험.
 * 외주 등록부터 서로 고쳐 가며 확정, 수정 요청 반복, 승인, 완료·정산까지 한 흐름으로 돌린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CoreFlowIntegrationTest {

    @Autowired AccountService accounts;
    @Autowired ProjectService projectService;
    @Autowired MatchingService matching;
    @Autowired AgreementService agreements;
    @Autowired DeliveryService delivery;
    @Autowired CollaborationService collaboration;
    @Autowired ProjectRepository projects;
    @Autowired NotificationLogRepository notificationLogs;
    @Autowired NotificationService notificationService;
    @Autowired PaymentService paymentService;
    @Autowired TransactionTemplate tx;

    LoginSession client, f1, f2, f3;
    Long pid;

    @BeforeEach
    void setUp() {
        client = login(accounts.signUp("c@t.kr", "pw", "김의뢰", "010", Set.of(Role.CLIENT)));
        f1 = login(accounts.signUp("f1@t.kr", "pw", "박디자", "010", Set.of(Role.FREELANCER)));
        f2 = login(accounts.signUp("f2@t.kr", "pw", "최개발", "010", Set.of(Role.FREELANCER)));
        f3 = login(accounts.signUp("f3@t.kr", "pw", "이겸임", "010", Set.of(Role.FREELANCER, Role.CLIENT)));
        pid = projectService.register(client, "카페 로고", "디자인", "로고와 간판 시안", "png, pdf",
                300_000, 600_000, LocalDate.now().plusDays(30));
        matching.submitProposal(f1, pid, 500_000, 14, "시안 3종");
        matching.submitProposal(f2, pid, 350_000, 10, "웹용 포함");
        matching.submitProposal(f3, pid, 420_000, 20, "일러스트");
    }

    @AfterEach
    void resetMocks() {
        notificationService.setAlwaysFail(false);
        paymentService.setAlwaysFail(false);
    }

    LoginSession login(User u) {
        return accounts.login(u.getEmail(), "pw");
    }

    Long proposalIdOf(LoginSession f) {
        return tx.execute(st -> projects.findById(pid).orElseThrow().getProposals(
                        projects.findById(pid).orElseThrow().getClient(), ProposalSort.SUBMITTED_AT).stream()
                .filter(p -> p.getFreelancer().getId().equals(f.userId())).findFirst().orElseThrow().getId());
    }

    ProjectState state() {
        return tx.execute(st -> projects.findById(pid).orElseThrow().getState());
    }

    List<String> notificationsTo(LoginSession u) {
        return notificationLogs.findByRecipientIdOrderByIdDesc(String.valueOf(u.userId())).stream()
                .map(NotificationLog::getType).toList();
    }

    UploadedFile file(String name) {
        return new UploadedFile(name, ("content of " + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 외주_등록부터_서로_고쳐가며_확정하고_수정을_반복해_완료까지() {
        // S1 선정
        matching.selectProposal(client, pid, proposalIdOf(f1));
        assertThat(state()).isEqualTo(ProjectState.NEGOTIATING);
        assertThat(notificationsTo(client)).containsExactly("선정 완료");
        assertThat(notificationsTo(f1)).containsExactly("선정됨");
        assertThat(notificationsTo(f2)).isEmpty();                                   // R2: 아직 통지하지 않음

        // S2 합의: 의뢰자 확인 → 프리랜서가 고침(확인 해제) → 의뢰자 오래된 개정 확인 실패 → 양측 재확인
        agreements.confirmAgreement(client, pid, 1);
        agreements.editAgreement(f1, pid, "로고 3종, 수정 2회", LocalDate.now().plusDays(25), "png, pdf");
        assertThatThrownBy(() -> agreements.confirmAgreement(client, pid, 1))
                .isInstanceOf(DomainException.class).hasMessageContaining("바뀌었습니다");
        assertThat(agreements.getAgreement(client, pid).clientConfirmed()).isFalse();
        ActionResult waiting = agreements.confirmAgreement(client, pid, 2);
        assertThat(waiting.message()).contains("상대방");
        agreements.confirmAgreement(f1, pid, 2);
        assertThat(state()).isEqualTo(ProjectState.IN_PROGRESS);
        assertThat(notificationsTo(f2)).containsExactly("미선정 종료");
        assertThat(notificationsTo(f3)).containsExactly("미선정 종료");
        assertThat(agreements.getAgreement(f1, pid).versions()).hasSize(1);

        // S3 제출: 형식 위반 거부(회차 미부여) → 1회차 제출
        assertThatThrownBy(() -> delivery.submit(f1, pid, List.of(file("logo.psd")), null, null))
                .isInstanceOf(DomainException.class);
        assertThat(delivery.listDeliverables(client, pid).rounds()).isEmpty();
        delivery.submit(f1, pid, List.of(file("logo_v1.png")), null, "1차");
        assertThat(state()).isEqualTo(ProjectState.IN_REVIEW);

        // S4 검토: 수정 요청 → 피드백이 제출 화면에 보임 → 2회차 → 승인 → 완료
        delivery.review(client, pid, ReviewDecision.REQUEST_REVISION, "글자를 더 크게");
        assertThat(state()).isEqualTo(ProjectState.REVISING);
        ReadModels.SubmitForm form = delivery.getSubmitForm(f1, pid);
        assertThat(form.allowed()).isTrue();
        assertThat(form.previousFeedback()).isEqualTo("글자를 더 크게");
        assertThat(form.nextRound()).isEqualTo(2);
        delivery.submit(f1, pid, List.of(file("logo_v2.png"), file("guide.pdf")), null, "글자 키움");
        delivery.review(client, pid, ReviewDecision.APPROVE, null);
        assertThat(state()).isEqualTo(ProjectState.APPROVED);

        ActionResult done = delivery.complete(client, pid);
        assertThat(done.notes()).isEmpty();
        assertThat(state()).isEqualTo(ProjectState.COMPLETED);
        ReadModels.ReviewPage page = delivery.listDeliverables(client, pid);
        assertThat(page.settlement().stateLabel()).isEqualTo("성공");
        assertThat(page.settlement().targetRound()).isEqualTo(2);
        assertThat(page.rounds()).extracting(ReadModels.DeliverableRow::stateLabel).containsExactly("승인", "수정 요청");
        assertThat(notificationsTo(f1)).contains("완료");

        // 결과물 파일은 참여자만 내려받는다
        Long fileId = page.rounds().get(0).files().get(0).id();
        assertThat(new String(collaboration.download(client, fileId).content(), StandardCharsets.UTF_8))
                .isEqualTo("content of logo_v2.png");
        assertThatThrownBy(() -> collaboration.download(f2, fileId)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.NOT_PARTICIPANT);

        // 활동 이력: 상태 전이가 순서대로 남는다
        assertThat(collaboration.getHistory(client, pid).reversed().stream()
                .filter(h -> h.typeLabel().equals("상태 전이")).map(ReadModels.HistoryRow::toState))
                .containsExactly("모집 중", "조건 합의 중", "작업 중", "검토 중", "수정 중", "검토 중", "승인됨", "완료");
    }

    @Test
    void 결렬하면_모집중으로_돌아가고_다른_제안을_다시_선정한다() {
        matching.selectProposal(client, pid, proposalIdOf(f1));
        agreements.breakOff(f1, pid, "일정이 맞지 않음");
        assertThat(state()).isEqualTo(ProjectState.RECRUITING);
        assertThat(notificationsTo(client)).contains("합의 결렬");

        matching.selectProposal(client, pid, proposalIdOf(f2));
        assertThat(state()).isEqualTo(ProjectState.NEGOTIATING);
        assertThat(agreements.getAgreement(f2, pid).brokenOff()).hasSize(1);
        assertThatThrownBy(() -> agreements.getAgreement(f1, pid)).isInstanceOf(DomainException.class);  // 더 이상 참여자 아님
    }

    @Test
    void 통지와_정산이_실패해도_선정과_완료는_유지된다() {
        notificationService.setAlwaysFail(true);
        ActionResult r = matching.selectProposal(client, pid, proposalIdOf(f1));
        assertThat(r.notes()).hasSize(2);
        assertThat(state()).isEqualTo(ProjectState.NEGOTIATING);
        assertThat(notificationLogs.findAll()).allMatch(l -> !l.isSuccess());

        agreements.confirmAgreement(client, pid, 1);
        agreements.confirmAgreement(f1, pid, 1);
        delivery.submit(f1, pid, List.of(file("a.png")), null, null);
        delivery.review(client, pid, ReviewDecision.APPROVE, null);
        paymentService.setAlwaysFail(true);
        ActionResult done = delivery.complete(client, pid);

        assertThat(state()).isEqualTo(ProjectState.COMPLETED);                         // B.4 8a
        assertThat(delivery.listDeliverables(client, pid).settlement().stateLabel()).isEqualTo("실패");
        assertThat(done.notes()).anyMatch(n -> n.contains("정산이 실패"));
    }

    @Test
    void 권한과_역할이_없으면_서비스에서_거부된다() {
        assertThatThrownBy(() -> matching.selectProposal(null, pid, proposalIdOf(f1)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.LOGIN_REQUIRED);
        assertThatThrownBy(() -> matching.selectProposal(f1, pid, proposalIdOf(f2)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.ROLE_REQUIRED);
        assertThatThrownBy(() -> matching.selectProposal(f3, pid, proposalIdOf(f2)))   // 의뢰자 역할은 있으나 소유자 아님
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.NOT_OWNER);
        assertThatThrownBy(() -> matching.selectProposal(client, 9999L, 1L))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(state()).isEqualTo(ProjectState.RECRUITING);
    }

    @Test
    void 양측이_같은_순간_확인해도_정확히_한_번_확정된다() throws Exception {
        matching.selectProposal(client, pid, proposalIdOf(f1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> byClient = () -> { start.await(); return agreements.confirmAgreement(client, pid, 1).message(); };
        Callable<String> byFreelancer = () -> { start.await(); return agreements.confirmAgreement(f1, pid, 1).message(); };
        Future<String> a = pool.submit(byClient);
        Future<String> b = pool.submit(byFreelancer);
        start.countDown();
        List<String> results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(results).filteredOn(m -> m.contains("확정되었습니다")).hasSize(1);
        assertThat(state()).isEqualTo(ProjectState.IN_PROGRESS);
        assertThat(agreements.getAgreement(client, pid).versions()).hasSize(1);
    }
}
