package team3.freelance.domain.model;

import java.util.Comparator;

/** 제안 목록 정렬기준 (C_3) */
public enum ProposalSort {
    SUBMITTED_AT("제출순", Comparator.comparing(Proposal::getSubmittedAt)),
    AMOUNT("금액 낮은 순", Comparator.comparingInt(Proposal::getAmount)),
    DURATION("기간 짧은 순", Comparator.comparingInt(Proposal::getDurationDays));

    private final String label;
    private final Comparator<Proposal> comparator;

    ProposalSort(String label, Comparator<Proposal> comparator) {
        this.label = label;
        this.comparator = comparator.thenComparing(Proposal::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public String label() { return label; }
    public Comparator<Proposal> comparator() { return comparator; }
}
