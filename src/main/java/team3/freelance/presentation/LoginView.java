package team3.freelance.presentation;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.service.AccountService;
import team3.freelance.domain.service.LoginSession;

/** 회원가입·로그인 화면 (Added). 이번 구현에서는 로그인만. */
@Controller
public class LoginView {

    private final AccountService accountService;

    public LoginView(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/login")
    public String show() {
        return "login";
    }

    /** submitLogin() */
    @PostMapping("/login")
    public String submitLogin(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        try {
            session.setAttribute(LoginSession.KEY, accountService.login(email, password));
            return "redirect:/";
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
