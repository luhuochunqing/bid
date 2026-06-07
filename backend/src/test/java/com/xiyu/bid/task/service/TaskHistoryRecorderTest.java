package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.changetracking.event.EntityChangedEvent;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.entity.TaskHistory;
import com.xiyu.bid.task.repository.TaskHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskHistoryRecorder — 任务更新快照")
class TaskHistoryRecorderTest {

    private final TaskHistoryRepository historyRepository = mock(TaskHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TaskHistoryRecorder recorder = new TaskHistoryRecorder(
            historyRepository,
            userRepository,
            new ObjectMapper(),
            eventPublisher
    );

    @Test
    @DisplayName("recordUpdate 保存 after 快照并发布 TASK 变更事件")
    void recordUpdate_SavesSnapshotAndPublishesTaskEvent() {
        Task before = Task.builder()
                .id(99L)
                .projectId(10L)
                .title("准备商务标")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();
        Task after = Task.builder()
                .id(99L)
                .projectId(10L)
                .title("准备商务标 V2")
                .status(Task.Status.IN_PROGRESS)
                .priority(Task.Priority.HIGH)
                .build();
        User actor = User.builder().id(7L).username("alice").fullName("Alice").role(User.Role.STAFF).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(actor));
        when(historyRepository.save(any(TaskHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.recordUpdate(before, after, "alice");

        ArgumentCaptor<TaskHistory> historyCaptor = ArgumentCaptor.forClass(TaskHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        TaskHistory saved = historyCaptor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(99L);
        assertThat(saved.getActorUserId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo("UPDATE");
        assertThat(saved.getSnapshotJson()).contains("\"title\":\"准备商务标 V2\"");

        ArgumentCaptor<EntityChangedEvent> eventCaptor = ArgumentCaptor.forClass(EntityChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EntityChangedEvent event = eventCaptor.getValue();
        assertThat(event.entityType()).isEqualTo("TASK");
        assertThat(event.entityId()).isEqualTo(99L);
        assertThat(event.actorUserId()).isEqualTo(7L);
        assertThat(event.metadata()).containsEntry("projectId", 10L);
        assertThat(event.before()).containsEntry("title", "准备商务标");
        assertThat(event.after()).containsEntry("title", "准备商务标 V2");
    }
}
