package com.xiyu.bid.tender.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 标讯批量导入异步任务实体。
 * <p>持久化异步导入任务状态，支持服务重启后恢复/标记失败。
 * <p>参考 PersonnelImportTaskEntity 范式（@JdbcTypeCode JSON + @PrePersist/@PreUpdate）。
 * <p>error_details 字段存 JSON 字符串，由 service 层用 ObjectMapper 序列化/反序列化
 * 为 {@code List<TenderImportTaskError>}。
 *
 * @see com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelImportTaskEntity
 */
@Entity
@Table(name = "tender_import_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务任务 ID（UUID），对外暴露防猜测 */
    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    /** 发起用户 ID（关联 users.id） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 原始文件名（仅记录，不存文件内容） */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Excel 总行数（解析后填充） */
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    /** 已处理行数 */
    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows;

    /** 成功行数 */
    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    /** 失败行数 */
    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    /** 状态机: PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 失败行明细 JSON（List<TenderImportTaskError> 序列化） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "JSON")
    private String errorDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 完成时间（COMPLETED/PARTIAL_SUCCESS/FAILED 时填充） */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
