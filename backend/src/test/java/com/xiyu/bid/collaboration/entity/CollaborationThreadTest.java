package com.xiyu.bid.collaboration.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

/**
 * CollaborationThread实体单元测试
 * 验证CollaborationThread实体的基本功能和状态管理
 */
class CollaborationThreadTest {

    @Test
    void threadBuilder_ShouldCreateValidThread() {
        // When
        CollaborationThread thread = CollaborationThread.builder()
                .id(1L)
                .projectId(100L)
                .title("Discussion about bid strategy")
                .status(CollaborationThread.ThreadStatus.OPEN)
                .createdBy(10L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Then
        assertThat(thread).isNotNull();
        assertThat(thread.getId()).isEqualTo(1L);
        assertThat(thread.getProjectId()).isEqualTo(100L);
        assertThat(thread.getTitle()).isEqualTo("Discussion about bid strategy");
        assertThat(thread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.OPEN);
        assertThat(thread.getCreatedBy()).isEqualTo(10L);
    }

    @Test
    void threadBuilder_WithAllStatuses_ShouldCreateValidThread() {
        // Given & When
        CollaborationThread openThread = CollaborationThread.builder()
                .id(1L)
                .status(CollaborationThread.ThreadStatus.OPEN)
                .build();

        CollaborationThread inProgressThread = CollaborationThread.builder()
                .id(2L)
                .status(CollaborationThread.ThreadStatus.IN_PROGRESS)
                .build();

        CollaborationThread resolvedThread = CollaborationThread.builder()
                .id(3L)
                .status(CollaborationThread.ThreadStatus.RESOLVED)
                .build();

        CollaborationThread closedThread = CollaborationThread.builder()
                .id(4L)
                .status(CollaborationThread.ThreadStatus.CLOSED)
                .build();

        // Then
        assertThat(openThread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.OPEN);
        assertThat(inProgressThread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.IN_PROGRESS);
        assertThat(resolvedThread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.RESOLVED);
        assertThat(closedThread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.CLOSED);
    }

    @Test
    void threadSetters_ShouldUpdateFields() {
        // Given
        CollaborationThread thread = CollaborationThread.builder()
                .id(1L)
                .title("Original title")
                .status(CollaborationThread.ThreadStatus.OPEN)
                .build();

        // When
        thread.setTitle("Updated title");
        thread.setStatus(CollaborationThread.ThreadStatus.IN_PROGRESS);

        // Then
        assertThat(thread.getTitle()).isEqualTo("Updated title");
        assertThat(thread.getStatus()).isEqualTo(CollaborationThread.ThreadStatus.IN_PROGRESS);
    }

    @Test
    void threadStatusEnum_ShouldHaveFourValues() {
        // When
        CollaborationThread.ThreadStatus[] statuses = CollaborationThread.ThreadStatus.values();

        // Then
        assertThat(statuses).hasSize(4);
        assertThat(statuses).containsExactlyInAnyOrder(
                CollaborationThread.ThreadStatus.OPEN,
                CollaborationThread.ThreadStatus.IN_PROGRESS,
                CollaborationThread.ThreadStatus.RESOLVED,
                CollaborationThread.ThreadStatus.CLOSED
        );
    }
}
