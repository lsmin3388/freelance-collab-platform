package team3.freelance.presentation;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import team3.freelance.domain.model.DomainException;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.ProjectService;

import java.time.LocalDate;

/** 프로젝트 등록 화면 (Added) */
@Controller
public class ProjectFormView {

    private final ProjectService projectService;

    public ProjectFormView(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects/new")
    public String show(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s) {
        if (s == null) return "redirect:/login";
        return "project-form";
    }

    /** submit() */
    @PostMapping("/projects")
    public String submit(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                         @RequestParam String title, @RequestParam String field, @RequestParam String description,
                         @RequestParam String expectedOutput, @RequestParam int budgetMin, @RequestParam int budgetMax,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate, Model model) {
        try {
            Long id = projectService.register(s, title, field, description, expectedOutput, budgetMin, budgetMax, dueDate);
            return "redirect:/projects/" + id;
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            return "project-form";
        }
    }
}
