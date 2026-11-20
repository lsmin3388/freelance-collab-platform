package team3.freelance.presentation;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.service.ReadModels.ActionResult;

import java.util.function.Supplier;

/** 화면 공통: 상태를 바꾸는 요청을 실행하고 결과 또는 거부 사유를 다음 화면에 한 번 보여 준다. */
final class Flash {
    private Flash() {
    }

    static void run(RedirectAttributes ra, Supplier<ActionResult> action) {
        try {
            ActionResult r = action.get();
            ra.addFlashAttribute("ok", r.message());
            if (!r.notes().isEmpty()) ra.addFlashAttribute("notes", r.notes());
        } catch (DomainException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("errorCode", e.code().name());
        }
    }
}
