// Input: project_retrospective 表行
// Output: JPA 实体 - WS-E 复盘
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
@Table(name = "project_retrospective")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRetrospective {

    public enum ReviewStatus {
        PENDING_REVIEW, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "result_type", length = 16)
    private String resultType;

    @Column(length = 4000)
    private String summary;

    @Column(name = "win_factors", length = 2048)
    private String winFactors;

    @Column(name = "loss_reasons", length = 2048)
    private String lossReasons;

    @Column(name = "competitor_notes", length = 2048)
    private String competitorNotes;

    @Column(name = "improvement_actions", length = 2048)
    private String improvementActions;

    @Column(name = "process_highlights", columnDefinition = "TEXT")
    private String processHighlights;

    // ========== V1035 蓝图 §3.3.1.5 新增字段 ==========

    /** 复盘会时间 */
    @Column(name = "meeting_time")
    private LocalDateTime meetingTime;

    /** 会议形式：ONLINE / OFFLINE */
    @Column(name = "meeting_format", length = 20)
    private String meetingFormat;

    /** 会议参与人 */
    @Column(name = "meeting_participants", length = 500)
    private String meetingParticipants;

    /** 丢标原因多选标记（逗号分隔的枚举值） */
    @Column(name = "loss_reason_flags", length = 1024)
    private String lossReasonFlags;

    /** 中标后续改进建议（富文本） */
    @Column(name = "post_win_improvements", columnDefinition = "TEXT")
    private String postWinImprovements;

    /** 流程存在问题（未中标·富文本） */
    @Column(name = "process_problems", columnDefinition = "TEXT")
    private String processProblems;

    /** 具体改进措施（未中标·富文本） */
    @Column(name = "post_loss_measures", columnDefinition = "TEXT")
    private String postLossMeasures;

    /** 复盘报告附件ID（逗号分隔） */
    @Column(name = "report_file_ids", length = 1024)
    private String reportFileIds;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus;

    @Column(name = "review_comment", length = 2048)
    private String reviewComment;

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
        if (reviewStatus == null) reviewStatus = ReviewStatus.PENDING_REVIEW.name();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
