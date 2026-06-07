// Input: project_result 表行
// Output: JPA 实体 - WS-D 结果确认
// Pos: entity/ - 持久化模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

@Entity
@Table(name = "project_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 4 类结果：WON / LOST / FAILED / ABANDONED（{@link com.xiyu.bid.project.core.BidResultType}）。 */
    @Column(name = "result_type", nullable = false, length = 16)
    private String resultType;

    @Column(name = "award_amount", precision = 20, scale = 2)
    private BigDecimal awardAmount;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    /** 单附件 id 兼容 V99 schema 的 evidence_attachment_id 列；多附件以逗号分隔扩展存放（不破坏列）。 */
    @Column(name = "evidence_attachment_id")
    private Long evidenceAttachmentId;

    /** 多附件 id 序列化为 CSV，供前端展示与 §3.5 复盘溯源。 */
    @Column(name = "evidence_doc_ids", length = 1024)
    private String evidenceDocIds;

    @Column(name = "summary", length = 2048)
    private String summary;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (registeredAt == null) registeredAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
