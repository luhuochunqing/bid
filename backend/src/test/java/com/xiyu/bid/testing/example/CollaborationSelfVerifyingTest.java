package com.xiyu.bid.testing.example;

import com.xiyu.bid.collaboration.dto.CommentCreateRequest;
import com.xiyu.bid.collaboration.dto.CollaborationThreadDTO;
import com.xiyu.bid.collaboration.dto.ThreadCreateRequest;
import com.xiyu.bid.collaboration.dto.ThreadStatus;
import com.xiyu.bid.collaboration.service.CollaborationService;
import com.xiyu.bid.testing.SelfVerifyingTest;
import com.xiyu.bid.testing.ShadowInspector;
import com.xiyu.bid.testing.StateMachineValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 自验证测试示例 - 协作模块
 *
 * 展示如何使用Shadow Inspector和状态机验证器
 * 进行跨层一致性验证。
 */
@DisplayName("协作模块自验证测试")
class CollaborationSelfVerifyingTest extends SelfVerifyingTest {

    @Autowired
    private CollaborationService collaborationService;

    private StateMachineValidator stateMachine;
    private ThreadCreateRequest threadRequest;

    @BeforeEach
    void setUp() {
        // 使用预定义的协作线程状态机
        stateMachine = StateMachineValidator.Predefined.collaborationThread();

        threadRequest = ThreadCreateRequest.builder()
                .projectId(100L)
                .title("Test Thread")
                .createdBy(10L)
                .build();
    }

    @Test
    @DisplayName("创建线程 - 三层一致性验证")
    void createThread_shouldPersistAndAudit() {
        // When
        CollaborationThreadDTO result = collaborationService.createThread(threadRequest);

        // Then - 跨层验证
        shadowVerify("collaboration_threads", result.getId())
                .exists()                      // 数据库存在
                .hasAuditLog()                 // 审计日志存在
                .hasAuditAction("CREATE")      // 审计日志正确记录操作
                .timestampsValid(false);       // 时间戳有效（新建，未更新）

        // 验证状态机初始状态
        ThreadStatus currentStatus = queryForObject(
            "SELECT status FROM collaboration_threads WHERE id = ?",
            ThreadStatus.class,
            result.getId()
        );
        assertThat(currentStatus).isEqualTo(ThreadStatus.OPEN);
    }

    @Test
    @DisplayName("状态转换 - OPEN到IN_PROGRESS - 状态机验证")
    void updateStatus_OPEN_to_IN_PROGRESS_shouldBeValid() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);

        // When - 执行状态转换
        CollaborationThreadDTO result = collaborationService.updateThreadStatus(
            thread.getId(),
            ThreadStatus.IN_PROGRESS
        );

        // Then - 跨层验证
        shadowVerify("collaboration_threads", result.getId())
                .exists()
                .hasAuditAction("UPDATE")
                .timestampsValid(true);  // 已更新

        // 验证状态转换合法性
        ThreadStatus currentStatus = queryForObject(
            "SELECT status FROM collaboration_threads WHERE id = ?",
            ThreadStatus.class,
            result.getId()
        );
        stateMachine.verifyTransition("OPEN", currentStatus.name());
    }

    @Test
    @DisplayName("完整状态转换路径验证")
    void fullStateTransitionPath_shouldFollowStateMachine() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);

        // When & Then - 逐步验证所有合法转换
        String[] validPath = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"};

        for (int i = 0; i < validPath.length - 1; i++) {
            String from = validPath[i];
            String to = validPath[i + 1];

            // 执行转换
            collaborationService.updateThreadStatus(
                thread.getId(),
                ThreadStatus.valueOf(to)
            );

            // 验证转换后的状态
            String currentStatus = queryForObject(
                "SELECT status FROM collaboration_threads WHERE id = ?",
                String.class,
                thread.getId()
            );

            // 状态机验证
            stateMachine.verifyTransition(from, currentStatus);

            // 数据库验证
            assertEquals(to, currentStatus,
                String.format("Expected state %s after transition from %s", to, from));
        }
    }

    @Test
    @DisplayName("审计日志完整性验证")
    void auditLogs_shouldRecordAllActions() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);
        awaitAsync(200);

        CommentCreateRequest commentRequest = CommentCreateRequest.builder()
                .userId(10L)
                .content("Test comment")
                .build();

        // When
        collaborationService.addComment(thread.getId(), commentRequest);
        awaitAsync(200);

        // Then - 验证审计日志
        Integer threadAuditCount = queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'CollaborationThread' AND entity_id = ?",
            Integer.class,
            String.valueOf(thread.getId())
        );

        Integer commentAuditCount = queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'Comment' AND entity_id IN (SELECT id FROM comments WHERE thread_id = ?)",
            Integer.class,
            thread.getId()
        );

        assertThat(threadAuditCount).isGreaterThan(0);  // 线程创建日志
        assertThat(commentAuditCount).isGreaterThan(0); // 评论添加日志
    }

    @Test
    @DisplayName("软删除验证 - 评论删除")
    void softDelete_comment_shouldMarkAsDeleted() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);

        CommentCreateRequest commentRequest = CommentCreateRequest.builder()
                .userId(10L)
                .content("Test comment")
                .build();

        var comment = collaborationService.addComment(thread.getId(), commentRequest);

        // When
        collaborationService.deleteComment(comment.getId());

        // Then - 验证软删除
        shadowVerify("comments", comment.getId())
                .exists()                // 记录仍存在（软删除）
                .softDeleted("is_deleted"); // 标记为已删除
    }

    @Test
    @DisplayName("数据一致性 - 同一线程的评论数")
    void dataConsistency_commentCountShouldMatch() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);

        int commentCount = 5;
        for (int i = 0; i < commentCount; i++) {
            CommentCreateRequest request = CommentCreateRequest.builder()
                    .userId(10L)
                    .content("Comment " + i)
                    .build();
            collaborationService.addComment(thread.getId(), request);
        }

        // When - 从数据库查询
        Integer dbCount = queryForObject(
            "SELECT COUNT(*) FROM comments WHERE thread_id = ? AND is_deleted = false",
            Integer.class,
            thread.getId()
        );

        // Then - 验证数据一致性
        assertThat(dbCount).isEqualTo(commentCount);

        // 跨层验证
        shadowVerify("collaboration_threads", thread.getId())
                .exists();
    }

    @Test
    @DisplayName("时间戳一致性验证")
    void timestamps_shouldBeConsistent() {
        // Given
        CollaborationThreadDTO thread = collaborationService.createThread(threadRequest);

        LocalDateTime createdAt = queryForObject(
            "SELECT created_at FROM collaboration_threads WHERE id = ?",
            LocalDateTime.class,
            thread.getId()
        );

        // When
        collaborationService.updateThreadStatus(thread.getId(), ThreadStatus.IN_PROGRESS);

        // Then
        LocalDateTime updatedAt = queryForObject(
            "SELECT updated_at FROM collaboration_threads WHERE id = ?",
            LocalDateTime.class,
            thread.getId()
        );

        assertNotNull(createdAt, "created_at should not be null");
        assertNotNull(updatedAt, "updated_at should not be null");
        assertTrue(updatedAt.isAfter(createdAt) || updatedAt.equals(createdAt),
            "updated_at should be after or equal to created_at");
    }

    @Test
    @DisplayName("状态机完整性验证")
    void stateMachine_shouldBeValid() {
        // 验证状态机本身的有效性
        stateMachine.validateIntegrity();

        // 验证所有状态都有定义
        assertThat(stateMachine.getValidTransitions("OPEN"))
            .containsExactly("IN_PROGRESS", "CLOSED");

        // 验证最终状态
        assertThat(stateMachine.isFinalState("CLOSED")).isTrue();
        assertThat(stateMachine.isFinalState("OPEN")).isFalse();
    }
}
