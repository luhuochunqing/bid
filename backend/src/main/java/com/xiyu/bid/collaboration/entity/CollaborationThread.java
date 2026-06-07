package com.xiyu.bid.collaboration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 协作讨论线程实体
 * 管理项目相关的协作讨论主题
 */
@Entity
@Table(name = "collaboration_threads", indexes = {
    @Index(name = "idx_thread_project", columnList = "project_id"),
    @Index(name = "idx_thread_status", columnList = "status"),
    @Index(name = "idx_thread_project_status", columnList = "project_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * 讨论标题
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * 讨论状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ThreadStatus status = ThreadStatus.OPEN;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 讨论状态枚举
     */
    public enum ThreadStatus {
        OPEN,           // 开放中
        IN_PROGRESS,    // 进行中
        RESOLVED,       // 已解决
        CLOSED          // 已关闭
    }
}
