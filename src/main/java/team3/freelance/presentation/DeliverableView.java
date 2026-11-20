package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.service.DeliveryService;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.UploadedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** 결과물 제출 화면 (Added) ― 핵심 시나리오 3 */
@Controller
public class DeliverableView {

    private final DeliveryService deliveryService;

    public DeliverableView(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** show(projectId): 제출 가능 여부와 직전 피드백 */
    @GetMapping("/projects/{projectId}/deliverables/new")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                       @PathVariable Long projectId, Model model) {
        model.addAttribute("form", deliveryService.getSubmitForm(s, projectId));
        return "deliverable-submit";
    }

    /** submit() */
    @PostMapping("/projects/{projectId}/deliverables")
    public String submit(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                         @PathVariable Long projectId,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @RequestParam(required = false) String virtualInfo,
                         @RequestParam(required = false) String memo, RedirectAttributes ra) {
        List<UploadedFile> uploaded = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                try {
                    uploaded.add(new UploadedFile(f.getOriginalFilename(), f.getBytes()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        Flash.run(ra, () -> deliveryService.submit(s, projectId, uploaded, virtualInfo, memo));
        return ra.getFlashAttributes().containsKey("error")
                ? "redirect:/projects/" + projectId + "/deliverables/new"
                : "redirect:/projects/" + projectId + "/review";
    }
}
