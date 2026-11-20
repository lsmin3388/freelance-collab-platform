package team3.freelance.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.SearchService;

/** 프로젝트 검색 화면 (Added). 모집 중 프로젝트 목록. */
@Controller
public class SearchView {

    private final SearchService searchService;

    public SearchView(SearchService searchService) {
        this.searchService = searchService;
    }

    /** searchProjects() */
    @GetMapping("/")
    public String searchProjects(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s, Model model) {
        if (s == null) return "redirect:/login";
        model.addAttribute("projects", searchService.searchProjects());
        return "search";
    }
}
