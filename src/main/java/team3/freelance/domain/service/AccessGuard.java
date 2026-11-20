package team3.freelance.domain.service;

import org.springframework.stereotype.Component;
import team3.freelance.data.repository.UserRepository;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.model.ErrorCode;
import team3.freelance.domain.model.Role;
import team3.freelance.domain.model.User;

/**
 * 권한 확인 (Added). 세션의 로그인 여부와 역할만 확인한다 (M_4).
 * '이 프로젝트의 의뢰자·선정 프리랜서인가'는 Project.isOwnedBy·isAssignedTo가 판단한다.
 */
@Component
public class AccessGuard {

    private final UserRepository users;

    public AccessGuard(UserRepository users) {
        this.users = users;
    }

    /** currentUser(세션) : User */
    public User currentUser(LoginSession session) {
        if (session == null) throw new DomainException(ErrorCode.LOGIN_REQUIRED);
        return users.findById(session.userId()).orElseThrow(() -> new DomainException(ErrorCode.LOGIN_REQUIRED));
    }

    /** requireRole(세션, 역할) : User */
    public User requireRole(LoginSession session, Role role) {
        User u = currentUser(session);
        if (!u.hasRole(role)) {
            throw new DomainException(ErrorCode.ROLE_REQUIRED, role.label() + " 역할이 있어야 합니다.");
        }
        return u;
    }

    /** 로그인하지 않았으면 null (공개 조회 화면용) */
    public User currentUserOrNull(LoginSession session) {
        return session == null ? null : users.findById(session.userId()).orElse(null);
    }
}
