package com.xiyu.bid.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Task entity representing a project task.
 */
@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /** Column length for department/role code/name fields. */
    private static final int LEN_CODE = 100;
    /** Column length for role code. */
    private static final int LEN_ROLE_CODE = 64;
    /** Column length for status code. */
    private static final int LEN_STATUS = 32;

    /** Unique identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning project id. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Task title. */
    @Column(nullable = false)
    private String title;

    /** Task description. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Task rich content (Markdown text, up to ~64KB via V102 TEXT column).
     * Stored as raw Markdown; the frontend is responsible for render-time
     * HTML sanitization (see {@code src/utils/markdown.js}).
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Task extended fields as a JSON object ({@code {"key":"value", ...}}),
     * stored in the V103 {@code extended_fields_json} TEXT column.
     *
     * <p>Schema of the keys/types is defined in the
     * {@code task_extended_field} table. The JSON payload is validated
     * against that schema at the service layer on write.</p>
     */
    @Column(name = "extended_fields_json", columnDefinition = "TEXT")
    private String extendedFieldsJson;

    /** Assignee user id. */
    @Column(name = "assignee_id")
    private Long assigneeId;

    /** Assignee department code. */
    @Column(name = "assignee_dept_code", length = LEN_CODE)
    private String assigneeDeptCode;

    /** Assignee department name. */
    @Column(name = "assignee_dept_name", length = LEN_CODE)
    private String assigneeDeptName;

    /** Assignee role code. */
    @Column(name = "assignee_role_code", length = LEN_ROLE_CODE)
    private String assigneeRoleCode;

    /** Assignee role name. */
    @Column(name = "assignee_role_name", length = LEN_CODE)
    private String assigneeRoleName;

    /** Current task status. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = LEN_STATUS)
    private Status status = Status.TODO;

    /** Task priority level. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    /** Due date for completion. */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Auto-set timestamps on persist. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** Auto-update timestamp on update. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 任务状态枚举.
     */
    public enum Status {
        /** Not started yet. */
        TODO,
        /** Work in progress. */
        IN_PROGRESS,
        /** Pending review. */
        REVIEW,
        /** Fully completed. */
        COMPLETED,
        /** Cancelled. */
        CANCELLED
    }

    /**
     * 任务优先级枚举.
     */
    public enum Priority {
        /** Low priority. */
        LOW,
        /** Medium priority. */
        MEDIUM,
        /** High priority. */
        HIGH,
        /** Urgent priority. */
        URGENT
    }
}
