// Input: opportunity_prediction_reminder_log 表行
// Output: JPA 实体 - 商机预测提醒日志
// Pos: entity/ - 持久化模型
package com.xiyu.bid.workbench.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机预测提醒日志：记录已提醒过的项目，保证"每项目只提醒一次"。
 *
 * <p>{@code project_id} 唯一：同一项目至多一条记录，提供幂等去重。
 */
@Entity
@Table(name = "opportunity_prediction_reminder_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_opportunity_prediction_reminder_project", columnNames = "project_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityPredictionReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 已提醒的项目 ID。 */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 触发提醒时的预测招标时间。 */
    @Column(name = "predicted_bid_date", nullable = false)
    private LocalDate predictedBidDate;

    /** 招标主体名称（通知文案用）。 */
    @Column(name = "purchaser_name", length = 500)
    private String purchaserName;

    /** 提醒发送时间。 */
    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
