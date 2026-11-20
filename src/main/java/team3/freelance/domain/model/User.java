package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 사용자. 설계의 User → Client·Freelancer 일반화 {overlapping, incomplete}는 Java 단일 상속으로 표현할 수 없어
 * 역할 집합(roles)으로 구현한다. Freelancer의 속성(전문분야·경력연수)은 이 클래스에 두고 FREELANCER 역할일 때만 의미가 있다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                       // 사용자ID

    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String name;
    private String contact;                // 연락수단
    private String intro;                  // 소개
    private LocalDateTime joinedAt;        // 가입시각

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = EnumSet.noneOf(Role.class);   // /역할 [0..2]

    private String expertise;              // Freelancer.전문분야
    private int careerYears;               // Freelancer.경력연수

    protected User() {
    }

    /** User.register (M_1, M_2). 역할은 0~2개. */
    public static User register(String email, String name, String contact, Set<Role> roles) {
        if (isBlank(email) || isBlank(name)) throw new DomainException(ErrorCode.REQUIRED_FIELD);
        User u = new User();
        u.email = email.trim();
        u.name = name.trim();
        u.contact = contact;
        u.joinedAt = LocalDateTime.now();
        if (roles != null) u.roles.addAll(roles);
        return u;
    }

    /** addRole (M_2) */
    public void addRole(Role role) {
        roles.add(role);
    }

    /** hasRole (M_4) */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Freelancer.updateExpertise (B_3) */
    public void updateExpertise(String expertise, int careerYears) {
        if (!hasRole(Role.FREELANCER)) throw new DomainException(ErrorCode.ROLE_REQUIRED);
        this.expertise = expertise;
        this.careerYears = Math.max(0, careerYears);
    }

    /**
     * Client.registerProject (A_1, A_3). 의뢰자 역할이 있어야 하고, 등록된 프로젝트는 '모집 중'이다.
     */
    public Project registerProject(String title, String field, String description, String expectedOutput,
                                   int budgetMin, int budgetMax, LocalDate dueDate) {
        if (!hasRole(Role.CLIENT)) throw new DomainException(ErrorCode.ROLE_REQUIRED);
        return new Project(this, title, field, description, expectedOutput, budgetMin, budgetMax, dueDate);
    }

    /** 같은 사용자인가(사용자ID 비교). 본인 프로젝트 제안 차단(R5)과 참여자 판단에 쓴다. */
    public boolean isSameUser(User other) {
        return other != null && id != null && id.equals(other.id);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getContact() { return contact; }
    public String getIntro() { return intro; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
    public String getExpertise() { return expertise; }
    public int getCareerYears() { return careerYears; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof User u && id != null && Objects.equals(id, u.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
