package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.model.ReviewDecision;
import team3.freelance.domain.service.DeliveryService;
import team3.freelance.domain.service.LoginSession;

/** 결과물 검토·완료 화면 (Added) ― 핵심 시나리오 4 */
@Controller
public class ReviewView {

    private final DeliveryService deliveryService;

    public ReviewView(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** show(projectId): 회차 목록과 피드백 이력 */
    @GetMapping("/projects/{projectId}/review")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId, Model model) {
        model.addAttribute("page", deliveryService.listDeliverables(s, projectId));
        return "review";
    }

    /** review(): 승인 또는 수정 요청 */
    @PostMapping("/projects/{projectId}/review")
    public String review(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                         @PathVariable Long projectId, @RequestParam ReviewDecision decision,
                         @RequestParam(required = false) String feedback, RedirectAttributes ra) {
        Flash.run(ra, () -> deliveryService.review(s, projectId, decision, feedback));
        return "redirect:/projects/" + projectId + "/review";
    }

    /** complete(): 화면에서 재확인을 거친 뒤 호출된다 (B.4 단계 2) */
    @PostMapping("/projects/{projectId}/complete")
    public String complete(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                           @PathVariable Long projectId, RedirectAttributes ra) {
        Flash.run(ra, () -> deliveryService.complete(s, projectId));
        return "redirect:/projects/" + projectId + "/review";
    }
}
