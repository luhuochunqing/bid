package com.xiyu.bid.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 案例实体
 */
@Entity
@Table(name = "cases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "project_date")
    private LocalDate projectDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "project_period")
    private String projectPeriod;

    @Column(name = "product_line")
    private String productLine;

    @Column(name = "source_project_id")
    private Long sourceProjectId;

    @Column(name = "archive_summary", columnDefinition = "TEXT")
    private String archiveSummary;

    @Column(name = "price_strategy", columnDefinition = "TEXT")
    private String priceStrategy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_success_factors", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "success_factor", length = 1000)
    @Builder.Default
    private List<String> successFactors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_lessons_learned", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "lesson_learned", length = 1000)
    @Builder.Default
    private List<String> lessonsLearned = new ArrayList<>();

    @Column(name = "document_snapshot_text", columnDefinition = "TEXT")
    private String documentSnapshotText;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_attachment_names", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "attachment_name")
    @Builder.Default
    private List<String> attachmentNames = new ArrayList<>();

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "visibility", length = 30)
    @Builder.Default
    private String visibility = "INTERNAL";

    @Column(name = "search_document", columnDefinition = "TEXT")
    private String searchDocument;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_tags", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_highlights", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "highlight")
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_technologies", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "technology")
    @Builder.Default
    private List<String> technologies = new ArrayList<>();

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "use_count", nullable = false)
    @Builder.Default
    private Long useCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        applyLifecycleDefaults();
        refreshSearchDocument();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        applyLifecycleDefaults();
        refreshSearchDocument();
        updatedAt = LocalDateTime.now();
    }

    public void refreshSearchDocument() {
        searchDocument = Stream.of(
                        title,
                        description,
                        customerName,
                        locationName,
                        projectPeriod,
                        productLine,
                        archiveSummary,
                        priceStrategy,
                        documentSnapshotText,
                        status,
                        visibility,
                        tags,
                        highlights,
                        technologies,
                        successFactors,
                        lessonsLearned,
                        attachmentNames)
                .flatMap(value -> value instanceof List<?> list
                        ? list.stream().filter(Objects::nonNull).map(Object::toString)
                        : Stream.ofNullable(value).map(Object::toString))
                .map(this::normalizeToken)
                .filter(token -> !token.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private void applyLifecycleDefaults() {
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
        if (visibility == null || visibility.isBlank()) {
            visibility = "INTERNAL";
        }
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (highlights == null) {
            highlights = new ArrayList<>();
        }
        if (technologies == null) {
            technologies = new ArrayList<>();
        }
        if (successFactors == null) {
            successFactors = new ArrayList<>();
        }
        if (lessonsLearned == null) {
            lessonsLearned = new ArrayList<>();
        }
        if (attachmentNames == null) {
            attachmentNames = new ArrayList<>();
        }
        if (viewCount == null) {
            viewCount = 0L;
        }
        if (useCount == null) {
            useCount = 0L;
        }
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 行业枚举
     */
    public enum Industry {
        REAL_ESTATE,
        INFRASTRUCTURE,
        MANUFACTURING,
        ENERGY,
        TRANSPORTATION,
        ENVIRONMENTAL,
        OTHER
    }

    /**
     * 结果枚举
     */
    public enum Outcome {
        WON,
        LOST,
        IN_PROGRESS
    }
}
