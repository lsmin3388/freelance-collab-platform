package team3.freelance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team3.freelance.domain.model.Role;
import team3.freelance.domain.model.User;
import team3.freelance.domain.service.AccountService;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.MatchingService;
import team3.freelance.domain.service.ProjectService;

import java.time.LocalDate;
import java.util.Set;

/**
 * 시연용 데이터. 계정 4개(비밀번호 모두 1234)와 제안 3건이 접수된 '모집 중' 프로젝트 1개를 만든다.
 * 실제 Service를 거쳐 만들므로 이 데이터도 업무 규칙을 지킨다.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
class DemoData implements ApplicationRunner {

    private final AccountService accounts;
    private final ProjectService projects;
    private final MatchingService matching;

    DemoData(AccountService accounts, ProjectService projects, MatchingService matching) {
        this.accounts = accounts;
        this.projects = projects;
        this.matching = matching;
    }

    @Override
    public void run(ApplicationArguments args) {
        User client = accounts.signUp("client@demo.kr", "1234", "김의뢰", "010-1111-1111", Set.of(Role.CLIENT));
        User f1 = accounts.signUp("design@demo.kr", "1234", "박디자", "010-2222-2222", Set.of(Role.FREELANCER));
        User f2 = accounts.signUp("dev@demo.kr", "1234", "최개발", "010-3333-3333", Set.of(Role.FREELANCER));
        User both = accounts.signUp("both@demo.kr", "1234", "이겸임", "010-4444-4444", Set.of(Role.CLIENT, Role.FREELANCER));
        accounts.updateExpertise(f1.getId(), "브랜드 디자인", 5);
        accounts.updateExpertise(f2.getId(), "웹 개발", 3);
        accounts.updateExpertise(both.getId(), "일러스트", 2);

        LoginSession c = new LoginSession(client.getId(), client.getName());
        Long pid = projects.register(c, "동네 카페 브랜드 로고 디자인", "디자인",
                "새로 여는 카페의 로고와 간판용 시안을 만들어 주세요. 따뜻한 느낌을 원합니다.",
                "png, pdf", 300_000, 600_000, LocalDate.now().plusDays(30));
        matching.submitProposal(new LoginSession(f1.getId(), f1.getName()), pid, 500_000, 14,
                "브랜드 로고 5년 경력입니다. 시안 3종과 수정 2회를 포함합니다.");
        matching.submitProposal(new LoginSession(f2.getId(), f2.getName()), pid, 350_000, 10,
                "간단한 로고와 웹용 파일까지 함께 드립니다.");
        matching.submitProposal(new LoginSession(both.getId(), both.getName()), pid, 420_000, 20,
                "손그림 느낌의 일러스트 로고를 제안합니다.");
    }
}
