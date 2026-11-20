package team3.freelance.presentation;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.SessionAttribute;
import team3.freelance.domain.service.CollaborationService;
import team3.freelance.domain.service.LoginSession;
import team3.freelance.domain.service.ReadModels.FileDownload;
import team3.freelance.domain.service.SearchService;

import java.nio.charset.StandardCharsets;

/** 프로젝트 진행 화면 (Added). 이번 구현에서는 활동 이력과 파일 내려받기만. */
@Controller
public class WorkspaceView {

    private final CollaborationService collaborationService;
    private final SearchService searchService;

    public WorkspaceView(CollaborationService collaborationService, SearchService searchService) {
        this.collaborationService = collaborationService;
        this.searchService = searchService;
    }

    /** showHistory() */
    @GetMapping("/projects/{projectId}/history")
    public String showHistory(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                              @PathVariable Long projectId, Model model) {
        model.addAttribute("rows", collaborationService.getHistory(s, projectId));
        model.addAttribute("p", searchService.getProjectDetail(s, projectId));
        return "history";
    }

    /** download(attachmentId) */
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@SessionAttribute(name = LoginSession.KEY, required = false) LoginSession s,
                                           @PathVariable Long attachmentId) {
        FileDownload f = collaborationService.download(s, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(f.content());
    }
}
