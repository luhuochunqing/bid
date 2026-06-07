// Input: project_result_competitor 表行
// Output: JPA 实体 - 结果确认竞争对手情况 (PRD §3.3.1.4)
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

import java.time.LocalDateTime;

@Entity
@Table(name = "project_result_competitor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResultCompetitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    /** 竞争对手名称 */
    @Column(length = 200)
    private String name;

    /** 折扣，如：95折 */
    @Column(length = 100)
    private String discount;

    /** 账期，如：月结60天 */
    @Column(name = "payment_term", length = 100)
    private String paymentTerm;

    /** 其他说明 */
    @Column(length = 500)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
