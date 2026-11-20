package team3.freelance.presentation;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.service.AgreementService;
import team3.freelance.domain.service.LoginSession;

import java.time.LocalDate;

/** 작업 조건 합의 화면 (Added) ― 핵심 시나리오 2 */
@Controller
public class AgreementView {

    private final AgreementService agreementService;

    public AgreementView(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    /** 합의안 표시 + showVersions() */
    @GetMapping("/projects/{projectId}/agreement")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId, Model model) {
        model.addAttribute("page", agreementService.getAgreement(s, projectId));
        return "agreement";
    }

    /** save(): 합의안 수정 */
    @PostMapping("/projects/{projectId}/agreement")
    public String save(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId, @RequestParam String scope,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate schedule,
                       @RequestParam String deliverableFormat, RedirectAttributes ra) {
        Flash.run(ra, () -> agreementService.editAgreement(s, projectId, scope, schedule, deliverableFormat));
        return "redirect:/projects/" + projectId + "/agreement";
    }

    /** confirm(개정번호): 화면에 표시된 개정번호를 함께 보낸다 (R3) */
    @PostMapping("/projects/{projectId}/agreement/confirm")
    public String confirm(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                          @PathVariable Long projectId, @RequestParam int revision, RedirectAttributes ra) {
        Flash.run(ra, () -> agreementService.confirmAgreement(s, projectId, revision));
        return "redirect:/projects/" + projectId + "/agreement";
    }

    /** breakOff() (New) */
    @PostMapping("/projects/{projectId}/agreement/break-off")
    public String breakOff(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                           @PathVariable Long projectId, @RequestParam String reason, RedirectAttributes ra) {
        Flash.run(ra, () -> agreementService.breakOff(s, projectId, reason));
        return ra.getFlashAttributes().containsKey("error")
                ? "redirect:/projects/" + projectId + "/agreement"
                : "redirect:/projects/" + projectId;
    }
}
