package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.MatchingService;
import team3.freelance.domain.service.ReadModels.ActionResult;
import team3.freelance.domain.service.SearchService;

import java.util.List;

/** 프로젝트 상세·제안 작성 화면 (Added) */
@Controller
public class ProjectDetailView {

    private final SearchService searchService;
    private final MatchingService matchingService;

    public ProjectDetailView(SearchService searchService, MatchingService matchingService) {
        this.searchService = searchService;
        this.matchingService = matchingService;
    }

    /** show(projectId) */
    @GetMapping("/projects/{projectId}")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId, Model model) {
        if (s == null) return "redirect:/login";
        model.addAttribute("p", searchService.getProjectDetail(s, projectId));
        return "project-detail";
    }

    /** submitProposal() */
    @PostMapping("/projects/{projectId}/proposals")
    public String submitProposal(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                                 @PathVariable Long projectId, @RequestParam int amount, @RequestParam int durationDays,
                                 @RequestParam String content, RedirectAttributes ra) {
        Flash.run(ra, () -> {
            matchingService.submitProposal(s, projectId, amount, durationDays, content);
            return ActionResult.of("제안을 제출했습니다. 의뢰자의 선정을 기다립니다.", List.of());
        });
        return "redirect:/projects/" + projectId;
    }
}
