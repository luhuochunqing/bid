package com.xiyu.bid.casework.application;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CasePrecipitationNotifier 单元测试。
 *
 * <p>验证案例沉淀消息中心通知两条通道：
 * <ol>
 *   <li>成功通道：通知 recipient = project.managerId，payload 携带 count/deeplink</li>
 *   <li>失败通道：通知 reason + count=0</li>
 *   <li>project 为 null 时跳过通知（不抛异常）</li>
 * </ol>
 */
class CasePrecipitationNotifierTest {

    private NotificationApplicationService notificationAppService;
    private CasePrecipitationNotifier notifier;

    @BeforeEach
    void setUp() {
        notificationAppService = mock(NotificationApplicationService.class);
        notifier = new CasePrecipitationNotifier(notificationAppService);
    }

    @Test
    @DisplayName("成功通知 — 携带入库数量 + 处理时长 + 跳转链接")
    void notifySuccess_withProject() {
        Project project = projectWithManager(7L);

        notifier.notifySuccess(1L, "E2E 项目", 5, 2300L, project, null);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationAppService, times(1)).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.title()).isEqualTo("AI 生成案例完成");
        assertThat(req.body()).contains("E2E 项目").contains("5 条案例").contains("2 秒");
        assertThat(req.payload())
                .containsEntry("projectId", 1L)
                .containsEntry("count", 5)
                .containsEntry("success", true)
                .containsEntry("durationMs", 2300L)
                .containsEntry("deeplink", "/knowledge/case");
    }

    @Test
    @DisplayName("成功通知 — project 为 null 时跳过，不调用 notificationAppService")
    void notifySuccess_nullProject() {
        notifier.notifySuccess(1L, "无主项目", 3, 100L, null, null);
        verify(notificationAppService, never()).createNotification(any(), anyLong());
    }

    @Test
    @DisplayName("成功通知 — managerId 为 null 时跳过")
    void notifySuccess_nullManagerId() {
        Project project = new Project();
        notifier.notifySuccess(1L, "无主项目", 3, 100L, project, null);
        verify(notificationAppService, never()).createNotification(any(), anyLong());
    }

    @Test
    @DisplayName("成功通知 — 显式 triggerUserId 优先于 managerId（手动触发路径）")
    void notifySuccess_triggerUserIdOverridesManager() {
        Project project = projectWithManager(11L);

        notifier.notifySuccess(1L, "E2E 项目", 5, 100L, project, 42L);

        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationAppService, times(1)).createNotification(any(), recipientCaptor.capture());
        assertThat(recipientCaptor.getValue()).isEqualTo(42L);
    }

    @Test
    @DisplayName("失败通知 — 携带失败原因 + count=0")
    void notifyFailure_withProject() {
        Project project = projectWithManager(11L);

        notifier.notifyFailure(1L, "E2E 项目", "缺少标书文件", project, null);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationAppService, times(1)).createNotification(captor.capture(), anyLong());
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.title()).isEqualTo("AI 生成案例失败");
        assertThat(req.body()).contains("缺少标书文件");
        assertThat(req.payload())
                .containsEntry("count", 0)
                .containsEntry("success", false)
                .containsEntry("reason", "缺少标书文件")
                .containsEntry("deeplink", "/knowledge/case");
    }

    @Test
    @DisplayName("通知异常时不向上抛 — 防阻塞主流程")
    void notifySuccess_swallowsExceptions() {
        Project project = projectWithManager(7L);
        when(notificationAppService.createNotification(any(), anyLong()))
                .thenThrow(new RuntimeException("消息中心故障"));

        // 不应抛异常
        notifier.notifySuccess(1L, "E2E 项目", 5, 100L, project, null);
        notifier.notifyFailure(1L, "E2E 项目", "some reason", project, null);
    }

    private Project projectWithManager(Long managerId) {
        Project p = new Project();
        p.setId(1L);
        p.setName("E2E 项目");
        p.setManagerId(managerId);
        return p;
    }
}
