package team3.freelance.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team3.freelance.data.external.AuthService;
import team3.freelance.data.repository.UserRepository;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.model.ErrorCode;
import team3.freelance.domain.model.Role;
import team3.freelance.domain.model.User;

import java.util.Set;

/** 계정 서비스 (Added). 이번 구현에서는 가입·로그인만 둔다. */
@Service
public class AccountService {

    private final UserRepository users;
    private final AuthService authService;

    public AccountService(UserRepository users, AuthService authService) {
        this.users = users;
        this.authService = authService;
    }

    /** signUp(이메일, 비밀번호, 이름, 연락수단, 역할[0..2]) */
    @Transactional
    public User signUp(String email, String password, String name, String contact, Set<Role> roles) {
        if (users.findByEmail(email).isPresent() || !authService.register(email, password)) {
            throw new DomainException(ErrorCode.REQUIRED_FIELD, "이미 가입된 이메일입니다.");
        }
        return users.save(User.register(email, name, contact, roles));
    }

    /** login(이메일, 비밀번호) : 세션 */
    @Transactional(readOnly = true)
    public LoginSession login(String email, String password) {
        if (!authService.authenticate(email, password)) throw new DomainException(ErrorCode.LOGIN_FAILED);
        User u = users.findByEmail(email).orElseThrow(() -> new DomainException(ErrorCode.LOGIN_FAILED));
        return new LoginSession(u.getId(), u.getName());
    }

    @Transactional
    public void updateExpertise(Long userId, String expertise, int careerYears) {
        User u = users.findById(userId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        u.updateExpertise(expertise, careerYears);
    }
}
