package team3.freelance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 핵심 시나리오 4개의 Model 규칙 시험. 설계 문서 Phase 3 예외 흐름 표의 행마다 시험이 하나 이상 있다.
 * 모든 거부 시험은 "예외 + 상태 그대로"를 함께 확인한다.
 */
class ProjectScenarioTest {

    static void assertRejected(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }

    // ================================================================ 시나리오 0: 제안 접수 (선정의 전제)
    @Nested
    @DisplayName("제안 접수")
    class ReceiveProposal {
        @Test
        void 본인_프로젝트에는_제안할_수_없다_R5() {
            Fixture fx = new Fixture();
            User owner = fx.client;
            owner.addRole(Role.FREELANCER);
            assertRejected(() -> fx.project.receiveProposal(owner, 100, 5, "x"), ErrorCode.OWN_PROJECT);
        }

        @Test
        void 같은_프로젝트에_두_번_제안할_수_없다_C2() {
            Fixture fx = new Fixture().withProposals();
            assertRejected(() -> fx.project.receiveProposal(fx.f1, 100, 5, "x"), ErrorCode.DUPLICATE_PROPOSAL);
        }

        @Test
        void 수행기간이_마감일을_넘으면_거부_B1_6a() {
            Fixture fx = new Fixture();
            assertRejected(() -> fx.project.receiveProposal(fx.f1, 100, 31, "x"), ErrorCode.DEADLINE_EXCEEDED);
        }
    }

    // ================================================================ 시나리오 1: 제안 선정
    @Nested
    @DisplayName("S1 제안 선정")
    class Select {
        @Test
        void 선정하면_조건합의중_합의초안_생성_나머지_제안은_접수로_남는다() {
            Fixture fx = new Fixture().withProposals();
            Agreement a = fx.project.selectProposal(fx.client, fx.proposalOf(fx.f1));

            assertThat(fx.project.getState()).isEqualTo(ProjectState.NEGOTIATING);
            assertThat(fx.proposalOf(fx.f1).getState()).isEqualTo(ProposalState.SELECTED);
            assertThat(fx.proposalOf(fx.f2).getState()).isEqualTo(ProposalState.RECEIVED);     // R2
            assertThat(fx.proposalOf(fx.both).getState()).isEqualTo(ProposalState.RECEIVED);
            assertThat(a.getState()).isEqualTo(AgreementState.NEGOTIATING);
            assertThat(a.getRevision()).isEqualTo(1);
            assertThat(a.getScope()).isEqualTo("로고와 간판 시안");                          // 초안 = 프로젝트 내용
            assertThat(a.getDeliverableFormat()).isEqualTo("png, pdf");
            assertThat(fx.project.isAssignedTo(fx.f1)).isTrue();
            assertThat(fx.project.getHistory()).last()
                    .satisfies(h -> assertThat(h.getToState()).isEqualTo(ProjectState.NEGOTIATING));
        }

        @Test
        void 의뢰자가_아니면_거부되고_아무것도_바뀌지_않는다() {
            Fixture fx = new Fixture().withProposals();
            Proposal p = fx.proposalOf(fx.f1);
            assertRejected(() -> fx.project.selectProposal(fx.stranger, p), ErrorCode.NOT_OWNER);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.RECRUITING);
            assertThat(p.getState()).isEqualTo(ProposalState.RECEIVED);
            assertThat(fx.project.getAgreements()).isEmpty();
        }

        @Test
        void 모집중이_아니면_다시_선정할_수_없다() {
            Fixture fx = new Fixture().selected();
            assertRejected(() -> fx.project.selectProposal(fx.client, fx.proposalOf(fx.f2)), ErrorCode.INVALID_STATE);
        }

        @Test
        void 다른_프로젝트의_제안이나_없는_제안은_선정할_수_없다() {
            Fixture fx = new Fixture().withProposals();
            Fixture other = new Fixture().withProposals();
            assertRejected(() -> fx.project.selectProposal(fx.client, other.proposalOf(other.f1)),
                    ErrorCode.PROPOSAL_NOT_SELECTABLE);
            assertRejected(() -> fx.project.selectProposal(fx.client, null), ErrorCode.PROPOSAL_NOT_SELECTABLE);
        }
    }

    // ================================================================ 시나리오 2: 작업 조건 합의
    @Nested
    @DisplayName("S2 작업 조건 합의")
    class Negotiate {
        @Test
        void 고치면_개정번호가_오르고_양측_확인이_해제된다_R3() {
            Fixture fx = new Fixture().selected();
            fx.project.confirmAgreement(fx.client, 1);
            Agreement a = fx.project.editAgreement(fx.f1, "로고 3종 + 간판", LocalDate.now().plusDays(20), "PNG, ai");

            assertThat(a.getRevision()).isEqualTo(2);
            assertThat(a.isClientConfirmed()).isFalse();
            assertThat(a.isFreelancerConfirmed()).isFalse();
            assertThat(a.getDeliverableFormat()).isEqualTo("png, ai");
            assertThat(fx.project.getState()).isEqualTo(ProjectState.NEGOTIATING);
        }

        @Test
        void 서로_고쳐가며_같은_개정을_양쪽이_확인하면_확정된다() {
            Fixture fx = new Fixture().selected();
            // 1) 의뢰자가 초안(개정 1)을 확인
            assertThat(fx.project.confirmAgreement(fx.client, 1).confirmed()).isFalse();
            // 2) 프리랜서가 일정을 고침 → 개정 2, 의뢰자 확인 해제
            fx.project.editAgreement(fx.f1, "로고와 간판 시안", LocalDate.now().plusDays(25), "png, pdf");
            // 3) 의뢰자가 범위를 다시 고침 → 개정 3
            fx.project.editAgreement(fx.client, "로고와 간판 시안, 수정 2회 포함", LocalDate.now().plusDays(25), "png, pdf");
            // 4) 프리랜서 확인(대기) → 5) 의뢰자 확인(확정)
            assertThat(fx.project.confirmAgreement(fx.f1, 3).confirmed()).isFalse();
            ConfirmOutcome out = fx.project.confirmAgreement(fx.client, 3);

            assertThat(out.confirmed()).isTrue();
            assertThat(out.closedProposers()).containsExactlyInAnyOrder(fx.f2, fx.both);
            Agreement a = fx.project.activeAgreement();
            assertThat(a.getState()).isEqualTo(AgreementState.CONFIRMED);
            assertThat(a.getVersions()).hasSize(1);
            assertThat(a.currentVersion().orElseThrow().getScope()).isEqualTo("로고와 간판 시안, 수정 2회 포함");
            assertThat(fx.project.getState()).isEqualTo(ProjectState.IN_PROGRESS);
            assertThat(fx.proposalOf(fx.f2).getState()).isEqualTo(ProposalState.CLOSED);
        }

        @Test
        void 화면에서_본_개정이_이미_바뀌었으면_확인은_무효_STALE() {
            Fixture fx = new Fixture().selected();
            fx.project.editAgreement(fx.f1, "범위 변경", LocalDate.now().plusDays(20), "png");
            assertRejected(() -> fx.project.confirmAgreement(fx.client, 1), ErrorCode.STALE_REVISION);
            assertThat(fx.project.activeAgreement().isClientConfirmed()).isFalse();
        }

        @Test
        void 같은_쪽이_두_번_확인하면_거부() {
            Fixture fx = new Fixture().selected();
            fx.project.confirmAgreement(fx.f1, 1);
            assertRejected(() -> fx.project.confirmAgreement(fx.f1, 1), ErrorCode.ALREADY_CONFIRMED);
        }

        @Test
        void 참여자가_아니면_고치거나_확인할_수_없다() {
            Fixture fx = new Fixture().selected();
            assertRejected(() -> fx.project.editAgreement(fx.f2, "x", LocalDate.now(), "png"), ErrorCode.NOT_PARTICIPANT);
            assertRejected(() -> fx.project.confirmAgreement(fx.stranger, 1), ErrorCode.NOT_PARTICIPANT);
        }

        @Test
        void 필수_항목이_비면_고칠_수_없다() {
            Fixture fx = new Fixture().selected();
            assertRejected(() -> fx.project.editAgreement(fx.f1, " ", LocalDate.now(), "png"), ErrorCode.REQUIRED_FIELD);
            assertRejected(() -> fx.project.editAgreement(fx.f1, "x", null, "png"), ErrorCode.REQUIRED_FIELD);
            assertRejected(() -> fx.project.editAgreement(fx.f1, "x", LocalDate.now(), "한글만"), ErrorCode.REQUIRED_FIELD);
            assertThat(fx.project.activeAgreement().getRevision()).isEqualTo(1);
        }

        @Test
        void 확정_뒤에는_고치거나_결렬할_수_없다() {
            Fixture fx = new Fixture().confirmed();
            assertRejected(() -> fx.project.editAgreement(fx.f1, "x", LocalDate.now(), "png"), ErrorCode.INVALID_STATE);
            assertRejected(() -> fx.project.breakOffAgreement(fx.client, "사유"), ErrorCode.INVALID_STATE);
        }

        @Test
        void 결렬하면_모집중으로_돌아가고_남은_제안에서_다시_고를_수_있다_D4() {
            Fixture fx = new Fixture().selected();
            User counterpart = fx.project.breakOffAgreement(fx.f1, "일정이 맞지 않음");

            assertThat(counterpart).isEqualTo(fx.client);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.RECRUITING);
            assertThat(fx.proposalOf(fx.f1).getState()).isEqualTo(ProposalState.BROKEN_OFF);
            assertThat(fx.project.getAgreements().get(0).getState()).isEqualTo(AgreementState.BROKEN_OFF);
            assertThat(fx.project.isAssignedTo(fx.f1)).isFalse();
            assertRejected(() -> fx.project.receiveProposal(fx.f1, 1, 1, "재지원"), ErrorCode.DUPLICATE_PROPOSAL);

            fx.project.selectProposal(fx.client, fx.proposalOf(fx.f2));               // 다시 선정
            assertThat(fx.project.activeAgreement().getProposal().isFrom(fx.f2)).isTrue();
            assertThat(fx.project.getAgreements()).hasSize(2);
        }

        @Test
        void 결렬에는_사유가_필요하다() {
            Fixture fx = new Fixture().selected();
            assertRejected(() -> fx.project.breakOffAgreement(fx.client, "  "), ErrorCode.REASON_REQUIRED);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.NEGOTIATING);
            assertThat(fx.proposalOf(fx.f1).getState()).isEqualTo(ProposalState.SELECTED);
        }
    }

    // ================================================================ 시나리오 3: 결과물 제출
    @Nested
    @DisplayName("S3 결과물 제출")
    class Submit {
        @Test
        void 제출하면_1회차_검토대기_프로젝트는_검토중() {
            Fixture fx = new Fixture().confirmed();
            Deliverable d = fx.project.submitDeliverable(fx.f1,
                    List.of(Fixture.file("logo.PNG"), Fixture.file("guide.pdf")), null, "1차 시안");
            assertThat(d.getRound()).isEqualTo(1);
            assertThat(d.getState()).isEqualTo(DeliverableState.PENDING_REVIEW);
            assertThat(d.getAttachments()).hasSize(2);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.IN_REVIEW);
        }

        @Test
        void 파일_없이_가상결과물_정보만으로도_제출할_수_있다() {
            Fixture fx = new Fixture().confirmed();
            assertThat(fx.project.submitDeliverable(fx.f1, List.of(), "https://figma.example/logo", null).getRound()).isEqualTo(1);
        }

        @Test
        void 제출_불가_사유를_먼저_알려준다_canSubmit() {
            Fixture fx = new Fixture().selected();
            assertThat(fx.project.canSubmit(fx.f2).reason()).isEqualTo(ErrorCode.NOT_ASSIGNED);   // B.3 2b
            assertThat(fx.project.canSubmit(fx.f1).reason()).isEqualTo(ErrorCode.INVALID_STATE);  // 조건 합의 중
            Fixture ok = new Fixture().confirmed();
            assertThat(ok.project.canSubmit(ok.f1).allowed()).isTrue();
        }

        @Test
        void 대기중인_변경_요청이_있으면_제출할_수_없다_R6() {
            Fixture fx = new Fixture().confirmed();
            fx.project.requestChange(fx.client, null, LocalDate.now().plusDays(29), "일정 당김");
            assertThat(fx.project.canSubmit(fx.f1).reason()).isEqualTo(ErrorCode.PENDING_CHANGE);
            assertRejected(() -> fx.project.submitDeliverable(fx.f1, List.of(Fixture.file("a.png")), null, null),
                    ErrorCode.PENDING_CHANGE);
            assertThat(fx.project.getDeliverables()).isEmpty();
        }

        @Test
        void 빈_제출과_합의되지_않은_형식은_거부되고_회차가_부여되지_않는다_B3_6a() {
            Fixture fx = new Fixture().confirmed();
            assertRejected(() -> fx.project.submitDeliverable(fx.f1, List.of(), "  ", null), ErrorCode.EMPTY_SUBMISSION);
            assertRejected(() -> fx.project.submitDeliverable(fx.f1,
                    List.of(Fixture.file("logo.png"), Fixture.file("source.psd")), null, null), ErrorCode.FORMAT_NOT_ALLOWED);
            assertThat(fx.project.getDeliverables()).isEmpty();
            assertThat(fx.project.getState()).isEqualTo(ProjectState.IN_PROGRESS);
        }

        @Test
        void 검토중에는_다음_회차를_낼_수_없다() {
            Fixture fx = new Fixture().submitted();
            assertRejected(() -> fx.project.submitDeliverable(fx.f1, List.of(Fixture.file("b.png")), null, null),
                    ErrorCode.INVALID_STATE);
        }
    }

    // ================================================================ 시나리오 4: 검토·피드백·완료
    @Nested
    @DisplayName("S4 검토·피드백·완료")
    class Review {
        @Test
        void 수정요청과_재제출을_반복하다_승인하고_완료한다() {
            Fixture fx = new Fixture().submitted();
            // 1회차 수정 요청
            Deliverable r1 = fx.project.reviewLatest(fx.client, ReviewDecision.REQUEST_REVISION, "글자를 더 크게");
            assertThat(r1.getState()).isEqualTo(DeliverableState.REVISION_REQUESTED);
            assertThat(r1.getFeedback()).get().extracting(Feedback::getContent).isEqualTo("글자를 더 크게");
            assertThat(fx.project.getState()).isEqualTo(ProjectState.REVISING);
            // 2회차 재제출 → 다시 검토 중
            Deliverable r2 = fx.project.submitDeliverable(fx.f1, List.of(Fixture.file("logo_v2.png")), null, "글자 키움");
            assertThat(r2.getRound()).isEqualTo(2);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.IN_REVIEW);
            // 2회차 승인
            fx.project.reviewLatest(fx.client, ReviewDecision.APPROVE, null);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.APPROVED);
            assertThat(fx.project.getDeliverables()).extracting(Deliverable::getState)
                    .containsExactly(DeliverableState.REVISION_REQUESTED, DeliverableState.APPROVED);
            // 완료 → 정산 기록 대기
            Settlement st = fx.project.complete(fx.client);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.COMPLETED);
            assertThat(st.getState()).isEqualTo(SettlementState.PENDING);
            assertThat(st.getAmount()).isEqualTo(500_000);
            assertThat(st.getTargetRound()).isEqualTo(2);
            fx.project.recordSettlement(true);
            assertThat(st.getState()).isEqualTo(SettlementState.SUCCESS);
            assertRejected(() -> fx.project.recordSettlement(false), ErrorCode.SETTLEMENT_ALREADY_RECORDED);
        }

        @Test
        void 수정요청에는_피드백이_반드시_있어야_한다_H3() {
            Fixture fx = new Fixture().submitted();
            assertRejected(() -> fx.project.reviewLatest(fx.client, ReviewDecision.REQUEST_REVISION, " "),
                    ErrorCode.FEEDBACK_REQUIRED);
            assertThat(fx.project.getState()).isEqualTo(ProjectState.IN_REVIEW);
            assertThat(fx.project.latestDeliverable().orElseThrow().getState()).isEqualTo(DeliverableState.PENDING_REVIEW);
        }

        @Test
        void 의뢰자만_검토하고_검토중일때만_검토한다() {
            Fixture fx = new Fixture().submitted();
            assertRejected(() -> fx.project.reviewLatest(fx.f1, ReviewDecision.APPROVE, null), ErrorCode.NOT_OWNER);
            fx.project.reviewLatest(fx.client, ReviewDecision.APPROVE, null);
            assertRejected(() -> fx.project.reviewLatest(fx.client, ReviewDecision.APPROVE, null), ErrorCode.INVALID_STATE);
        }

        @Test
        void 승인됨이_아니면_완료할_수_없다_B4_2a() {
            Fixture fx = new Fixture().submitted();
            assertRejected(() -> fx.project.complete(fx.client), ErrorCode.INVALID_STATE);
            fx.project.reviewLatest(fx.client, ReviewDecision.APPROVE, null);
            assertRejected(() -> fx.project.complete(fx.f1), ErrorCode.NOT_OWNER);
            assertThat(fx.project.getSettlement()).isEmpty();
        }

        @Test
        void 상태_전이마다_활동_이력이_쌓인다() {
            Fixture fx = new Fixture().submitted();
            fx.project.reviewLatest(fx.client, ReviewDecision.APPROVE, null);
            fx.project.complete(fx.client);
            assertThat(fx.project.getHistory().stream()
                    .filter(h -> h.getType() == HistoryType.STATE_TRANSITION).map(HistoryEntry::getToState))
                    .containsExactly(ProjectState.RECRUITING, ProjectState.NEGOTIATING, ProjectState.IN_PROGRESS,
                            ProjectState.IN_REVIEW, ProjectState.APPROVED, ProjectState.COMPLETED);
        }
    }
}
