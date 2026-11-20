package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 활동 이력. 추가만 되고 고치거나 지우지 않는다 (모든 열 updatable = false). */
@Entity
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private LocalDateTime occurredAt;     // 발생시각
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private HistoryType type;             // 유형
    @Column(length = 1000, updatable = false)
    private String content;               // 내용
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private ProjectState fromState;       // 이전상태 [0..1]
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private ProjectState toState;         // 이후상태 [0..1]
    @Column(updatable = false)
    private Long actorId;                 // 행위자ID

    protected HistoryEntry() {
    }

    HistoryEntry(HistoryType type, User actor, String content, ProjectState from, ProjectState to) {
        this.occurredAt = LocalDateTime.now();
        this.type = type;
        this.actorId = actor.getId();
        this.content = content;
        this.fromState = from;
        this.toState = to;
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public HistoryType getType() { return type; }
    public String getContent() { return content; }
    public ProjectState getFromState() { return fromState; }
    public ProjectState getToState() { return toState; }
    public Long getActorId() { return actorId; }
}
