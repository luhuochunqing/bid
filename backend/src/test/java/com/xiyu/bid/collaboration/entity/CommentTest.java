package com.xiyu.bid.collaboration.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

/**
 * Comment实体单元测试
 * 验证Comment实体的基本功能和状态管理
 */
class CommentTest {

    @Test
    void commentBuilder_ShouldCreateValidComment() {
        // When
        Comment comment = Comment.builder()
                .id(1L)
                .threadId(100L)
                .userId(10L)
                .content("Test comment content")
                .mentions("[1, 2, 3]")
                .parentId(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        // Then
        assertThat(comment).isNotNull();
        assertThat(comment.getId()).isEqualTo(1L);
        assertThat(comment.getThreadId()).isEqualTo(100L);
        assertThat(comment.getUserId()).isEqualTo(10L);
        assertThat(comment.getContent()).isEqualTo("Test comment content");
        assertThat(comment.getMentions()).isEqualTo("[1, 2, 3]");
        assertThat(comment.getParentId()).isNull();
        assertThat(comment.getIsDeleted()).isFalse();
    }

    @Test
    void commentBuilder_WithNestedComment_ShouldCreateValidComment() {
        // When
        Comment comment = Comment.builder()
                .id(2L)
                .threadId(100L)
                .userId(10L)
                .content("Nested comment")
                .parentId(1L)
                .isDeleted(false)
                .build();

        // Then
        assertThat(comment).isNotNull();
        assertThat(comment.getParentId()).isEqualTo(1L);
    }

    @Test
    void commentBuilder_WithDeletedFlag_ShouldCreateDeletedComment() {
        // When
        Comment comment = Comment.builder()
                .id(1L)
                .threadId(100L)
                .userId(10L)
                .content("Deleted content")
                .isDeleted(true)
                .build();

        // Then
        assertThat(comment.getIsDeleted()).isTrue();
    }

    @Test
    void commentBuilder_WithoutMentions_ShouldCreateCommentWithNullMentions() {
        // When
        Comment comment = Comment.builder()
                .id(1L)
                .threadId(100L)
                .userId(10L)
                .content("Comment without mentions")
                .build();

        // Then
        assertThat(comment.getMentions()).isNull();
    }

    @Test
    void commentSetters_ShouldUpdateFields() {
        // Given
        Comment comment = Comment.builder()
                .id(1L)
                .content("Original content")
                .build();

        // When
        comment.setContent("Updated content");
        comment.setIsDeleted(true);

        // Then
        assertThat(comment.getContent()).isEqualTo("Updated content");
        assertThat(comment.getIsDeleted()).isTrue();
    }
}
