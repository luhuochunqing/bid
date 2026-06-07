package com.xiyu.bid.casework.infrastructure;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "archive_file")
public class ArchiveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "document_category", nullable = false)
    private String documentCategory; // TENDER(招标文件), BID(标书文件), OPEN_LIST(开标一览表), WIN_NOTICE(中标通知书), DEPOSIT_RECEIPT(保证金银行回单), OTHER

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "upload_user_id", nullable = false)
    private Long uploadUserId;

    @Column(name = "upload_user_name", nullable = false)
    private String uploadUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
