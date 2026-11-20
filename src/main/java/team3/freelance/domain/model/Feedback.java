package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 피드백. 수정 요청된 회차에만 1건 있다 (H_3). */
@Entity
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 4000, nullable = false)
    private String content;             // 내용
    private LocalDateTime writtenAt;    // 작성시각

    protected Feedback() {
    }

    Feedback(String content) {
        this.content = content;
        this.writtenAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public LocalDateTime getWrittenAt() { return writtenAt; }
}
