package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_case")
public class KnowledgeCase implements KnowledgeCaseReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_project_id", nullable = false)
    private Long sourceProjectId;

    @Column(name = "source_project_name", nullable = false)
    private String sourceProjectName;

    @Column(name = "scoring_point_title", nullable = false)
    private String scoringPointTitle;

    @Column(name = "scoring_point_id")
    private Long scoringPointId;

    @Column(name = "requirement_raw", nullable = false, columnDefinition = "TEXT")
    private String requirementRaw;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "reuse_count", nullable = false)
    private Integer reuseCount = 0;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, OFF_SHELF

    @Column(name = "customer_type")
    private String customerType;

    @Column(name = "project_type")
    private String projectType;

    @Column(name = "bid_result", length = 20)
    private String bidResult;

    @Column(name = "scoring_category", length = 50)
    private String scoringCategory;

    @Column(name = "product_line", length = 100)
    private String productLine;

    /** 历史切片原版投标文件的 OBS 对象键（仅导入管道写入，签发端点只读）。 */
    @Column(name = "obs_bid_file_key", length = 512)
    private String obsBidFileKey;

    /** 应答片段在标书原文中的最近有效章节标题 */
    @Column(name = "source_section_title", length = 500)
    private String sourceSectionTitle;

    /** 标书原文章节层级路径，使用 > 分隔 */
    @Column(name = "source_section_path", length = 2000)
    private String sourceSectionPath;

    /** 来源标书用户可读文件名 */
    @Column(name = "source_document_name", length = 255)
    private String sourceDocumentName;

    /** 来源标书可证明的逻辑页码 */
    @Column(name = "source_page_number")
    private Integer sourcePageNumber;

    /** Markdown 章节扫描稳定序号 */
    @Column(name = "source_section_index")
    private Integer sourceSectionIndex;

    /** 业务子主题（如 SLA_SERVICE, SYS_ARCH, LOGISTICS_WAREHOUSE 等）。 */
    @Column(name = "sub_topic", length = 50)
    private String subTopic;

    /** 案例文本质量与充实度评分（0-100）。 */
    @Column(name = "quality_score", nullable = false)
    private Integer qualityScore = 60;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
