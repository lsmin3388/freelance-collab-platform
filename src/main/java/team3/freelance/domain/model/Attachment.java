package team3.freelance.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 첨부 파일 정보. 파일 본문은 FileStorage에 있고 여기에는 저장키만 둔다. */
@Entity
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;           // 파일명
    private String format;             // 형식(확장자)
    private long size;                 // 크기
    @Column(nullable = false, updatable = false)
    private String storageKey;         // 저장키
    private LocalDateTime uploadedAt;  // 업로드시각

    protected Attachment() {
    }

    Attachment(FileInfo info) {
        this.fileName = info.fileName();
        this.format = info.format();
        this.size = info.size();
        this.storageKey = info.storageKey();
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getFormat() { return format; }
    public long getSize() { return size; }
    public String getStorageKey() { return storageKey; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
