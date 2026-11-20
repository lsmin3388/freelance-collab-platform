package team3.freelance.domain.model;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Model 단위 시험용 객체. 저장소 없이 ID만 붙인다. */
final class Fixture {
    private static final AtomicLong SEQ = new AtomicLong(1);

    final User client = user("김의뢰", Role.CLIENT);
    final User f1 = user("박디자", Role.FREELANCER);
    final User f2 = user("최개발", Role.FREELANCER);
    final User both = user("이겸임", Role.CLIENT, Role.FREELANCER);
    final User stranger = user("남모름", Role.CLIENT, Role.FREELANCER);
    final Project project;

    Fixture() {
        project = client.registerProject("카페 로고", "디자인", "로고와 간판 시안", "png, pdf",
                300_000, 600_000, LocalDate.now().plusDays(30));
    }

    static User user(String name, Role... roles) {
        User u = User.register(name + "@t.kr", name, "010", Set.of(roles));
        ReflectionTestUtils.setField(u, "id", SEQ.getAndIncrement());
        return u;
    }

    /** 제안 3건(f1, f2, both)을 받은 상태 */
    Fixture withProposals() {
        idOf(project.receiveProposal(f1, 500_000, 14, "시안 3종"));
        idOf(project.receiveProposal(f2, 350_000, 10, "웹용 포함"));
        idOf(project.receiveProposal(both, 420_000, 20, "일러스트"));
        return this;
    }

    Proposal proposalOf(User f) {
        return project.getProposals(client, ProposalSort.SUBMITTED_AT).stream().filter(p -> p.isFrom(f)).findFirst().orElseThrow();
    }

    /** f1을 선정한 '조건 합의 중' 상태 */
    Fixture selected() {
        withProposals();
        project.selectProposal(client, proposalOf(f1));
        return this;
    }

    /** 양측 확인으로 확정된 '작업 중' 상태 (산출물 형식 png, pdf) */
    Fixture confirmed() {
        selected();
        int rev = project.activeAgreement().getRevision();
        project.confirmAgreement(f1, rev);
        project.confirmAgreement(client, rev);
        return this;
    }

    /** 1회차 제출로 '검토 중' 상태 */
    Fixture submitted() {
        confirmed();
        project.submitDeliverable(f1, List.of(file("logo_v1.png")), null, "1차 시안");
        return this;
    }

    static FileInfo file(String name) {
        return new FileInfo(name, FileInfo.formatOf(name), 10, "key-" + name);
    }

    private static void idOf(Proposal p) {
        ReflectionTestUtils.setField(p, "id", SEQ.getAndIncrement());
    }
}
