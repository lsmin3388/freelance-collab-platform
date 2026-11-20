package team3.freelance.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.model.ErrorCode;
import team3.freelance.domain.model.Project;
import team3.freelance.domain.model.ProjectState;
import team3.freelance.domain.service.ReadModels.ProjectDetail;
import team3.freelance.domain.service.ReadModels.ProjectSummary;

import java.util.List;

/** 탐색 서비스 (Added). 모집 중 프로젝트 목록과 프로젝트 상세. */
@Service
public class SearchService {

    private final AccessGuard guard;
    private final ProjectRepository projects;

    public SearchService(AccessGuard guard, ProjectRepository projects) {
        this.guard = guard;
        this.projects = projects;
    }

    /** searchProjects(조건): 모집 중만 (B_1) */
    @Transactional(readOnly = true)
    public List<ProjectSummary> searchProjects() {
        return projects.findByStateOrderByCreatedAtDesc(ProjectState.RECRUITING).stream()
                .map(ProjectSummary::of).toList();
    }

    /** getProjectDetail(projectId): 보는 사람에 따라 의뢰자·선정 프리랜서 여부를 함께 돌려준다. */
    @Transactional(readOnly = true)
    public ProjectDetail getProjectDetail(LoginSession s, Long projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return ProjectDetail.of(p, guard.currentUserOrNull(s));
    }
}
