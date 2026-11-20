package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.model.ProposalSort;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.MatchingService;

/** 제안 목록 화면 (Added) ― 핵심 시나리오 1 제안 선정의 입구 */
@Controller
public class ProposalListView {

    private final MatchingService matchingService;

    public ProposalListView(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /** show(projectId, 정렬) */
    @GetMapping("/projects/{projectId}/proposals")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId,
                       @RequestParam(defaultValue = "SUBMITTED_AT") ProposalSort sort, Model model) {
        model.addAttribute("page", matchingService.listProposals(s, projectId, sort));
        model.addAttribute("sorts", ProposalSort.values());
        return "proposals";
    }

    /** select(proposalId): 화면에서 재확인(confirm 대화상자)을 거친 뒤 호출된다 (B.2 단계 4). */
    @PostMapping("/projects/{projectId}/proposals/{proposalId}/select")
    public String select(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                         @PathVariable Long projectId, @PathVariable Long proposalId, RedirectAttributes ra) {
        Flash.run(ra, () -> matchingService.selectProposal(s, projectId, proposalId));
        return ra.getFlashAttributes().containsKey("error")
                ? "redirect:/projects/" + projectId + "/proposals"
                : "redirect:/projects/" + projectId + "/agreement";
    }
}
