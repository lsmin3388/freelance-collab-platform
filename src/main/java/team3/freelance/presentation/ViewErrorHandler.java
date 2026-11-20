package team3.freelance.presentation;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.model.ErrorCode;

/** 화면 조회 중 업무 규칙 위반이 나면 오류 화면을 보여 준다. 로그인이 필요하면 로그인 화면으로 보낸다. */
@ControllerAdvice
class ViewErrorHandler {

    @ExceptionHandler(DomainException.class)
    String handle(DomainException e, Model model, HttpServletResponse response) {
        if (e.code() == ErrorCode.LOGIN_REQUIRED) return "redirect:/login";
        response.setStatus(switch (e.code().kind()) {
            case FORBIDDEN -> 403;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case INVALID -> 400;
        });
        model.addAttribute("message", e.getMessage());
        model.addAttribute("code", e.code().name());
        return "error-page";
    }
}
