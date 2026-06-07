package com.xiyu.bid.collaboration;

import com.xiyu.bid.collaboration.dto.CommentDTO;
import com.xiyu.bid.collaboration.entity.Comment;
import com.xiyu.bid.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

class CollaborationServiceCommentReadDeleteTest extends AbstractCollaborationServiceTest {

    @Test
    void deleteComment_WithValidId_ShouldSoftDelete() {
        Comment deletedComment = Comment.builder()
                .id(1L)
                .content("Content to delete")
                .isDeleted(true)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(deletedComment);

        collaborationService.deleteComment(1L);

        verify(commentRepository).findById(1L);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void deleteComment_WithNonExistentComment_ShouldThrowException() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.deleteComment(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found");
        verify(commentRepository).findById(999L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_WithAlreadyDeletedComment_ShouldThrowException() {
        Comment alreadyDeletedComment = Comment.builder()
                .id(1L)
                .content("Content")
                .isDeleted(true)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(alreadyDeletedComment));

        assertThatThrownBy(() -> collaborationService.deleteComment(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Comment already deleted");
        verify(commentRepository).findById(1L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void getCommentsByThread_WithValidThreadId_ShouldReturnComments() {
        Comment comment2 = activeComment(2L, 1L, 11L, "Second comment");
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(commentRepository.findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(testComment, comment2));

        List<CommentDTO> result = collaborationService.getCommentsByThread(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getThreadId()).isEqualTo(1L);
        assertThat(result.get(1).getThreadId()).isEqualTo(1L);
        verify(threadRepository).findById(1L);
        verify(commentRepository).findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(1L);
    }

    @Test
    void getCommentsByThread_WithNoComments_ShouldReturnEmptyList() {
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(commentRepository.findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        List<CommentDTO> result = collaborationService.getCommentsByThread(1L);

        assertThat(result).isEmpty();
        verify(threadRepository).findById(1L);
        verify(commentRepository).findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(1L);
    }

    @Test
    void getMentionsForUser_WithValidUserId_ShouldReturnComments() {
        Comment mentionedComment = Comment.builder()
                .id(2L)
                .threadId(1L)
                .userId(10L)
                .content("Mentioning user")
                .mentions("[10, 20, 30]")
                .isDeleted(false)
                .build();
        when(commentRepository.findByMentionsContainingAndIsDeletedFalse("[10]"))
                .thenReturn(List.of(testComment, mentionedComment));

        List<CommentDTO> result = collaborationService.getMentionsForUser(10L);

        assertThat(result).hasSize(2);
        verify(commentRepository).findByMentionsContainingAndIsDeletedFalse("[10]");
    }

    @Test
    void getMentionsForUser_WithNoMentions_ShouldReturnEmptyList() {
        when(commentRepository.findByMentionsContainingAndIsDeletedFalse("[999]"))
                .thenReturn(List.of());

        List<CommentDTO> result = collaborationService.getMentionsForUser(999L);

        assertThat(result).isEmpty();
        verify(commentRepository).findByMentionsContainingAndIsDeletedFalse("[999]");
    }
}
