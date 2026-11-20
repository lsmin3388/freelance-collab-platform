package team3.freelance.domain.service;

import team3.freelance.domain.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Service가 화면에 돌려주는 조회용 값. 화면은 Model 객체를 직접 받지 않는다. */
public final class ReadModels {
    private ReadModels() {
    }

    public record ProjectSummary(Long id, String title, String field, String budget, LocalDate dueDate,
                                 String stateLabel, String clientName) {
        static ProjectSummary of(Project p) {
            return new ProjectSummary(p.getId(), p.getTitle(), p.getField(), ReadModels.budget(p), p.getDueDate(),
                    p.getState().label(), p.getClient().getName());
        }
    }

    public record ProjectDetail(Long id, String title, String field, String description, String expectedOutput,
                                String budget, LocalDate dueDate, ProjectState state, String stateLabel,
                                String clientName, String assignedName, int proposalCount,
                                boolean owner, boolean assigned, boolean canPropose) {
        static ProjectDetail of(Project p, User viewer) {
            boolean owner = viewer != null && p.isOwnedBy(viewer);
            boolean assigned = viewer != null && p.isAssignedTo(viewer);
            boolean canPropose = viewer != null && !owner && viewer.hasRole(Role.FREELANCER)
                    && p.getState() == ProjectState.RECRUITING && !p.hasProposalFrom(viewer);
            String assignedName = p.selectedProposal().map(sp -> sp.getFreelancer().getName()).orElse(null);
            int count = owner ? p.getProposals(viewer, ProposalSort.SUBMITTED_AT).size() : 0;
            return new ProjectDetail(p.getId(), p.getTitle(), p.getField(), p.getDescription(), p.getExpectedOutput(),
                    ReadModels.budget(p), p.getDueDate(), p.getState(), p.getState().label(), p.getClient().getName(),
                    assignedName, count, owner, assigned, canPropose);
        }
    }

    public record ProposalRow(Long id, String freelancerName, String expertise, int careerYears, int amount,
                              int durationDays, String content, LocalDateTime submittedAt, String stateLabel,
                              boolean selectable) {
    }

    public record ProposalListPage(ProjectDetail project, List<ProposalRow> rows, ProposalSort sort) {
    }

    public record MyProposal(Long projectId, String projectTitle, int amount, int durationDays,
                             String stateLabel, String projectStateLabel) {
    }

    public record VersionRow(int versionNo, String scope, LocalDate schedule, String format, LocalDateTime confirmedAt) {
    }

    public record AgreementPage(ProjectDetail project, boolean exists, String stateLabel, String scope,
                                LocalDate schedule, String format, int revision, boolean clientConfirmed,
                                boolean freelancerConfirmed, boolean confirmedByMe, boolean negotiating,
                                String freelancerName, List<VersionRow> versions, List<String> brokenOff) {
    }

    public record SubmitForm(ProjectDetail project, boolean allowed, String reason, int nextRound,
                             String allowedFormats, String agreedScope, LocalDate agreedSchedule,
                             Integer previousRound, String previousFeedback) {
    }

    public record FileRow(Long id, String fileName, long size) {
    }

    public record DeliverableRow(int round, String stateLabel, String memo, String virtualInfo,
                                 LocalDateTime submittedAt, List<FileRow> files, String feedback) {
        static DeliverableRow of(Deliverable d) {
            return new DeliverableRow(d.getRound(), d.getState().label(), d.getMemo(), d.getVirtualInfo(),
                    d.getSubmittedAt(),
                    d.getAttachments().stream().map(a -> new FileRow(a.getId(), a.getFileName(), a.getSize())).toList(),
                    d.getFeedback().map(Feedback::getContent).orElse(null));
        }
    }

    public record SettlementRow(int amount, int targetRound, String stateLabel, LocalDateTime processedAt) {
    }

    public record ReviewPage(ProjectDetail project, List<DeliverableRow> rounds, boolean canReview,
                             boolean canComplete, Integer settlementAmount, SettlementRow settlement) {
    }

    public record HistoryRow(LocalDateTime occurredAt, String typeLabel, String content,
                             String fromState, String toState, String actorName) {
    }

    public record FileDownload(String fileName, byte[] content) {
    }

    /** 상태를 바꾸는 요청의 결과 메시지와 부가 안내(통지 실패 등) */
    public record ActionResult(String message, List<String> notes) {
        public static ActionResult of(String message, List<String> notes) {
            return new ActionResult(message, List.copyOf(notes));
        }
    }

    static String budget(Project p) {
        return String.format("%,d ~ %,d원", p.getBudgetMin(), p.getBudgetMax());
    }
}
