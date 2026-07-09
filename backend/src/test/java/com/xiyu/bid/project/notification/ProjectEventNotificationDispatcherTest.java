// Output: ProjectEventNotificationDispatcher 事件通知标题与 payload 验证
// Pos: project/notification/ - 事件分发器测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectEventNotificationDispatcherTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    private ProjectEventNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ProjectEventNotificationDispatcher(
                notificationService, projectRepository, recipientResolver);
    }

    @Test
    void notifyTaskStatusChanged_titleShouldIncludeProjectNameAndTaskName() {
        Long projectId = 10L;
        Long taskId = 99L;
        String taskName = "编写技术标书";
        Long assigneeId = 8L;
        Long actorUserId = 7L;

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(Project.builder().id(projectId).name("西安地铁项目").build()));
        when(recipientResolver.getProjectMemberUserIds(projectId, actorUserId))
                .thenReturn(List.of());

        dispatcher.notifyTaskStatusChanged(
                projectId, taskId, taskName, "待处理", "审核中", assigneeId, actorUserId);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(actorUserId));
        assertThat(captor.getValue().title())
                .isEqualTo("任务状态变更 - 西安地铁项目 - 编写技术标书");
    }
}
