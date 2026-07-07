package com.xiyu.bid.casework.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bid_case_slice")
public class BidCaseSlice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_dir", nullable = false, length = 200)
    private String projectDir;

    @Column(name = "project_idx", nullable = false)
    private Integer projectIdx;

    @Column(name = "docx_file", nullable = false, length = 500)
    private String docxFile;

    @Column(name = "docx_label", nullable = false, length = 20)
    private String docxLabel;

    @Column(name = "section_idx", nullable = false)
    private Integer sectionIdx;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "text_preview", nullable = false, columnDefinition = "TEXT")
    private String textPreview;

    @Column(name = "text_length", nullable = false)
    private Integer textLength;

    @Column(name = "para_count", nullable = false)
    private Integer paraCount;

    @Column(name = "embedding", columnDefinition = "MEDIUMBLOB")
    private byte[] embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    @Column(name = "embedding_at")
    private LocalDateTime embeddingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
