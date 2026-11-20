package team3.freelance.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.domain.model.Project;
import team3.freelance.domain.model.Role;
import team3.freelance.domain.model.User;
import team3.freelance.domain.service.ReadModels.ProjectSummary;

import java.time.LocalDate;
import java.util.List;

/** 프로젝트 서비스 (Added). 프로젝트 등록과 내 프로젝트 조회. */
@Service
public class ProjectService {

    private final AccessGuard guard;
    private final ProjectRepository projects;

    public ProjectService(AccessGuard guard, ProjectRepository projects) {
        this.guard = guard;
        this.projects = projects;
    }

    /** register(세션, ...) : 프로젝트ID (A_1) */
    @Transactional
    public Long register(LoginSession s, String title, String field, String description, String expectedOutput,
                         int budgetMin, int budgetMax, LocalDate dueDate) {
        User c = guard.requireRole(s, Role.CLIENT);
        Project p = c.registerProject(title, field, description, expectedOutput, budgetMin, budgetMax, dueDate);
        return projects.save(p).getId();
    }

    /** getMyProjects(세션) (C_3) */
    @Transactional(readOnly = true)
    public List<ProjectSummary> getMyProjects(LoginSession s) {
        User u = guard.currentUser(s);
        return projects.findByClient(u.getId()).stream().map(ProjectSummary::of).toList();
    }
}
