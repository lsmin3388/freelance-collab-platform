package team3.freelance.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team3.freelance.data.repository.FileStorage;
import team3.freelance.data.repository.ProjectRepository;
import team3.freelance.data.repository.UserRepository;
import team3.freelance.domain.model.*;
import team3.freelance.domain.service.ReadModels.FileDownload;
import team3.freelance.domain.service.ReadModels.HistoryRow;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 협업 서비스 (Added). 이번 구현에서는 활동 이력 조회와 결과물 파일 내려받기만 둔다. */
@Service
public class CollaborationService {

    private final AccessGuard guard;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final FileStorage fileStorage;

    public CollaborationService(AccessGuard guard, ProjectRepository projects, UserRepository users,
                                FileStorage fileStorage) {
        this.guard = guard;
        this.projects = projects;
        this.users = users;
        this.fileStorage = fileStorage;
    }

    /** getHistory(세션, projectId) (F_4): 참여자만, 최신 순 */
    @Transactional(readOnly = true)
    public List<HistoryRow> getHistory(LoginSession s, Long projectId) {
        User u = guard.currentUser(s);
        Project p = projects.findById(projectId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!p.isOwnedBy(u) && !p.isAssignedTo(u)) throw new DomainException(ErrorCode.NOT_PARTICIPANT);
        Map<Long, String> names = users.findAll().stream().collect(Collectors.toMap(User::getId, User::getName));
        Function<ProjectState, String> label = st -> st == null ? "" : st.label();
        return p.getHistory().stream()
                .map(h -> new HistoryRow(h.getOccurredAt(), h.getType().label(), h.getContent(),
                        label.apply(h.getFromState()), label.apply(h.getToState()),
                        names.getOrDefault(h.getActorId(), "?")))
                .toList().reversed();
    }

    /** download(세션, attachmentId) : 파일 (H_1). 결과물 파일은 프로젝트 참여자만 내려받는다. */
    @Transactional(readOnly = true)
    public FileDownload download(LoginSession s, Long attachmentId) {
        User u = guard.currentUser(s);
        Project p = projects.findByAttachment(attachmentId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!p.isOwnedBy(u) && !p.isAssignedTo(u)) throw new DomainException(ErrorCode.NOT_PARTICIPANT);
        Attachment a = projects.findAttachment(attachmentId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new FileDownload(a.getFileName(), fileStorage.load(a.getStorageKey()));
    }
}
