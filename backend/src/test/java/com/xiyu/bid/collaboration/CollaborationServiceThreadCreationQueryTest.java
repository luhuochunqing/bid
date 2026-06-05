package com.xiyu.bid.collaboration;

import com.xiyu.bid.collaboration.dto.CollaborationThreadDTO;
import com.xiyu.bid.collaboration.dto.ThreadCreateRequest;
import com.xiyu.bid.collaboration.dto.ThreadStatus;
import com.xiyu.bid.collaboration.entity.CollaborationThread;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

class CollaborationServiceThreadCreationQueryTest extends AbstractCollaborationServiceTest {

    @Test
    void createThread_WithValidRequest_ShouldReturnSavedThread() {
        when(threadRepository.save(any(CollaborationThread.class))).thenReturn(testThread);

        CollaborationThreadDTO result = collaborationService.createThread(threadCreateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getTitle()).isEqualTo("Discussion about bid strategy");
        assertThat(result.getStatus()).isEqualTo(ThreadStatus.OPEN);
        assertThat(result.getCreatedBy()).isEqualTo(10L);
        verify(threadRepository).save(any(CollaborationThread.class));
    }

    @Test
    void createThread_WithNullProjectId_ShouldThrowException() {
        ThreadCreateRequest invalidRequest = ThreadCreateRequest.builder()
                .projectId(null)
                .title("Test thread")
                .createdBy(10L)
                .build();

        assertThatThrownBy(() -> collaborationService.createThread(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID");
        verify(threadRepository, never()).save(any());
    }

    @Test
    void createThread_WithNullTitle_ShouldThrowException() {
        ThreadCreateRequest invalidRequest = ThreadCreateRequest.builder()
                .projectId(100L)
                .title(null)
                .createdBy(10L)
                .build();

        assertThatThrownBy(() -> collaborationService.createThread(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title");
        verify(threadRepository, never()).save(any());
    }

    @Test
    void createThread_WithEmptyTitle_ShouldThrowException() {
        ThreadCreateRequest invalidRequest = ThreadCreateRequest.builder()
                .projectId(100L)
                .title("   ")
                .createdBy(10L)
                .build();

        assertThatThrownBy(() -> collaborationService.createThread(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title");
        verify(threadRepository, never()).save(any());
    }

    @Test
    void getThreadsByProject_WithValidProjectId_ShouldReturnThreads() {
        CollaborationThread thread2 = thread(
                2L,
                100L,
                "Second thread",
                CollaborationThread.ThreadStatus.IN_PROGRESS
        );
        when(threadRepository.findByProjectId(100L)).thenReturn(List.of(testThread, thread2));

        List<CollaborationThreadDTO> result = collaborationService.getThreadsByProject(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProjectId()).isEqualTo(100L);
        assertThat(result.get(1).getProjectId()).isEqualTo(100L);
        verify(threadRepository).findByProjectId(100L);
    }

    @Test
    void getThreadsByProject_WithNoThreads_ShouldReturnEmptyList() {
        when(threadRepository.findByProjectId(999L)).thenReturn(List.of());

        List<CollaborationThreadDTO> result = collaborationService.getThreadsByProject(999L);

        assertThat(result).isEmpty();
        verify(threadRepository).findByProjectId(999L);
    }
}
