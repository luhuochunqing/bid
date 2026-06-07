package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.service.MentionApplicationService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.dto.TaskActivityDTO;
import com.xiyu.bid.task.dto.TaskCommentCreateRequest;
import com.xiyu.bid.task.entity.TaskComment;
import com.xiyu.bid.task.repository.TaskCommentRepository;
import com.xiyu.bid.task.repository.TaskHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskActivityService — 评论与动态")
class TaskActivityServiceTest {

    private TaskRepository taskRepository;
    private TaskCommentRepository commentRepository;
    private TaskHistoryRepository historyRepository;
    private UserRepository userRepository;
    private ProjectAccessScopeService accessScopeService;
    private MentionApplicationService mentionService;
    private TaskActivityService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        commentRepository = mock(TaskCommentRepository.class);
        historyRepository = mock(TaskHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        accessScopeService = mock(ProjectAccessScopeService.class);
        mentionService = mock(MentionApplicationService.class);
        service = new TaskActivityService(
                taskRepository,
                commentRepository,
                historyRepository,
                userRepository,
                accessScopeService,
                mentionService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("createComment 写 task_comment 并复用 MentionApplicationService")
    void createComment_PersistsAndDispatchesMentions() {
        Task task = Task.builder().id(99L).projectId(10L).title("准备商务标").build();
        User actor = User.builder().id(7L).username("alice").fullName("Alice").role(User.Role.STAFF).build();
        when(taskRepository.findById(99L)).thenReturn(Optional.of(task));
        when(accessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(actor));
        when(commentRepository.save(any(TaskComment.class))).thenAnswer(invocation -> {
            TaskComment comment = invocation.getArgument(0);
            comment.setId(123L);
            comment.setCreatedAt(LocalDateTime.parse("2026-05-04T10:15:30"));
            return comment;
        });

        TaskActivityDTO created = service.createComment(
                99L,
                new TaskCommentCreateRequest("请 @[Bob](8) 看一下"),
                "alice"
        );

        ArgumentCaptor<TaskComment> commentCaptor = ArgumentCaptor.forClass(TaskComment.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getTaskId()).isEqualTo(99L);
        assertThat(commentCaptor.getValue().getAuthorUserId()).isEqualTo(7L);
        assertThat(commentCaptor.getValue().getContent()).isEqualTo("请 @[Bob](8) 看一下");

        ArgumentCaptor<CreateMentionRequest> mentionCaptor = ArgumentCaptor.forClass(CreateMentionRequest.class);
        verify(mentionService).createMention(mentionCaptor.capture(), anyLong());
        assertThat(mentionCaptor.getValue().sourceEntityType()).isEqualTo("TASK");
        assertThat(mentionCaptor.getValue().sourceEntityId()).isEqualTo(99L);
        assertThat(mentionCaptor.getValue().title()).contains("准备商务标");

        assertThat(created.type()).isEqualTo("COMMENT");
        assertThat(created.actorName()).isEqualTo("Alice");
        assertThat(created.content()).isEqualTo("请 @[Bob](8) 看一下");
    }
}
