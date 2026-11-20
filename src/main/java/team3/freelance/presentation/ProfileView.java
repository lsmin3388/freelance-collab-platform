package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.MatchingService;
import team3.freelance.domain.service.ProjectService;

/** 프로필 화면 (Added). 이번 구현에서는 내 프로젝트·내 제안 목록만. */
@Controller
public class ProfileView {

    private final ProjectService projectService;
    private final MatchingService matchingService;

    public ProfileView(ProjectService projectService, MatchingService matchingService) {
        this.projectService = projectService;
        this.matchingService = matchingService;
    }

    /** showMyProjects() · showMyProposals() */
    @GetMapping("/me")
    public String showMine(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s, Model model) {
        model.addAttribute("myProjects", projectService.getMyProjects(s));
        model.addAttribute("myProposals", matchingService.getMyProposals(s));
        return "me";
    }
}
