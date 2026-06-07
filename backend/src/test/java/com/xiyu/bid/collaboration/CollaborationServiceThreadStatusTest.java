package com.xiyu.bid.collaboration;

import com.xiyu.bid.collaboration.dto.CollaborationThreadDTO;
import com.xiyu.bid.collaboration.dto.ThreadStatus;
import com.xiyu.bid.collaboration.entity.CollaborationThread;
import com.xiyu.bid.exception.ResourceNotFoundException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

class CollaborationServiceThreadStatusTest extends AbstractCollaborationServiceTest {

    @Test
    void updateThreadStatus_WithValidData_ShouldReturnUpdatedThread() {
        CollaborationThread updatedThread = thread(
                1L,
                100L,
                "Discussion about bid strategy",
                CollaborationThread.ThreadStatus.IN_PROGRESS
        );
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(threadRepository.save(any(CollaborationThread.class))).thenReturn(updatedThread);

        CollaborationThreadDTO result = collaborationService.updateThreadStatus(1L, ThreadStatus.IN_PROGRESS);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ThreadStatus.IN_PROGRESS);
        verify(threadRepository).findById(1L);
        verify(threadRepository).save(any(CollaborationThread.class));
    }

    @Test
    void updateThreadStatus_WithNonExistentThread_ShouldThrowException() {
        when(threadRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.updateThreadStatus(999L, ThreadStatus.CLOSED))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thread not found");
        verify(threadRepository).findById(999L);
        verify(threadRepository, never()).save(any());
    }

    @Test
    void updateThreadStatus_ToAllStatuses_ShouldUpdateSuccessfully() {
        when(threadRepository.findById(1L)).thenReturn(Optional.of(testThread));
        when(threadRepository.save(any(CollaborationThread.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CollaborationThreadDTO inProgress = collaborationService.updateThreadStatus(1L, ThreadStatus.IN_PROGRESS);
        CollaborationThreadDTO resolved = collaborationService.updateThreadStatus(1L, ThreadStatus.RESOLVED);
        CollaborationThreadDTO closed = collaborationService.updateThreadStatus(1L, ThreadStatus.CLOSED);

        assertThat(inProgress.getStatus()).isEqualTo(ThreadStatus.IN_PROGRESS);
        assertThat(resolved.getStatus()).isEqualTo(ThreadStatus.RESOLVED);
        assertThat(closed.getStatus()).isEqualTo(ThreadStatus.CLOSED);
        verify(threadRepository, times(3)).save(any(CollaborationThread.class));
    }
}
