package com.xiyu.bid.collaboration;

import com.xiyu.bid.collaboration.dto.CommentCreateRequest;
import com.xiyu.bid.collaboration.dto.CommentDTO;
import com.xiyu.bid.collaboration.dto.CommentUpdateRequest;
import com.xiyu.bid.collaboration.entity.Comment;
import com.xiyu.bid.exception.ResourceNotFoundException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

class CollaborationServiceCommentCommandTest extends AbstractCollaborationServiceTest {

    @Test
    void addComment_WithValidRequest_ShouldReturnSavedComment() {
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(commentRepository.save(any(Comment.class))).thenReturn(testComment);

        CommentDTO result = collaborationService.addComment(1L, commentCreateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getThreadId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getContent()).isEqualTo("This is a test comment");
        verify(threadRepository).findById(1L);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void addComment_WithNonExistentThread_ShouldThrowException() {
        when(threadRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.addComment(999L, commentCreateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thread not found");
        verify(threadRepository).findById(999L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_WithNullContent_ShouldThrowException() {
        CommentCreateRequest invalidRequest = CommentCreateRequest.builder()
                .threadId(1L)
                .userId(10L)
                .content(null)
                .build();
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));

        assertThatThrownBy(() -> collaborationService.addComment(1L, invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_WithEmptyContent_ShouldThrowException() {
        CommentCreateRequest invalidRequest = CommentCreateRequest.builder()
                .threadId(1L)
                .userId(10L)
                .content("   ")
                .build();
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));

        assertThatThrownBy(() -> collaborationService.addComment(1L, invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_WithNestedComment_ShouldReturnSavedComment() {
        CommentCreateRequest nestedRequest = CommentCreateRequest.builder()
                .threadId(1L)
                .userId(10L)
                .content("Nested reply")
                .parentId(5L)
                .build();
        Comment nestedComment = Comment.builder()
                .id(2L)
                .threadId(1L)
                .userId(10L)
                .content("Nested reply")
                .parentId(5L)
                .build();
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(commentRepository.save(any(Comment.class))).thenReturn(nestedComment);

        CommentDTO result = collaborationService.addComment(1L, nestedRequest);

        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isEqualTo(5L);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void updateComment_WithValidData_ShouldReturnUpdatedComment() {
        Comment updatedComment = activeComment(1L, 1L, 10L, "Updated comment content");
        when(commentRepository.findById(1L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(updatedComment);

        CommentDTO result = collaborationService.updateComment(1L, commentUpdateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Updated comment content");
        verify(commentRepository).findById(1L);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void updateComment_WithNonExistentComment_ShouldThrowException() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.updateComment(999L, commentUpdateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");
        verify(commentRepository).findById(999L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_WithDeletedComment_ShouldThrowException() {
        Comment deletedComment = Comment.builder()
                .id(1L)
                .content("Deleted content")
                .isDeleted(true)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(deletedComment));

        assertThatThrownBy(() -> collaborationService.updateComment(1L, commentUpdateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot update deleted comment");
        verify(commentRepository).findById(1L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_WithNullContent_ShouldThrowException() {
        CommentUpdateRequest invalidRequest = CommentUpdateRequest.builder()
                .content(null)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(testComment));

        assertThatThrownBy(() -> collaborationService.updateComment(1L, invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content");
        verify(commentRepository, never()).save(any());
    }
}
