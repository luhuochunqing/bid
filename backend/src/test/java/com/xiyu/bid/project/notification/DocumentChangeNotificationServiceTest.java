// Output: notifyDocumentChanged 的分支覆盖（项目存在性、Spec 030 接收人过滤、操作类型/分类透传）
// Pos: project/notification/ - 文档变更通知纯编排层测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentChangeNotificationService — 蓝图 §消息中心-系统通知 序号 5（Spec 030 对齐）")
class DocumentChangeNotificationServiceTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private DocumentChangeNotificationService svc;

    private static final Long PID = 100L;
    private static final Long UID = 42L;

    private static final Set<ProjectNotificationRole> EXPECTED_ROLES = Set.of(
            ProjectNotificationRole.BID_ADMIN,
            ProjectNotificationRole.BID_TEAM_LEADER,
            ProjectNotificationRole.BID_LEAD,
            ProjectNotificationRole.BID_ASSISTANT);

    @BeforeEach
    void setUp() {
        svc = new DocumentChangeNotificationService(
                notificationService, projectRepository, recipientResolver);
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
        return p;
    }

    @Test
    @DisplayName("上传 → 发送 DOCUMENT_CHANGE 给有项目访问权的团队成员（排除操作人自己）")
    void sendsDocumentChangeToAccessibleTeamMembersExcludingActor() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of(1L, 2L));

        svc.notifyDocumentChanged(PID, 3001L, "中标通知书.pdf", "WIN_NOTICE",
                "王工（1001）", DocumentOperationType.UPLOAD, UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        CreateNotificationRequest req = requestCaptor.getValue();
        assertThat(req.type()).isEqualTo("DOCUMENT_CHANGE");
        assertThat(req.sourceEntityType()).isEqualTo("DOCUMENT");
        assertThat(req.sourceEntityId()).isEqualTo(3001L);
        assertThat(req.title()).isEqualTo("文档变更 - 测试项目");
        assertThat(req.body()).contains("文档「中标通知书.pdf」被 王工（1001） 上传");
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(req.payload()).containsEntry("operationType", "上传");
        assertThat(req.payload()).containsEntry("documentName", "中标通知书.pdf");
        assertThat(req.payload()).containsEntry("targetUrl", "/project/100/result");
        assertThat(req.payload()).containsEntry("projectId", PID);
        assertThat(req.payload()).containsEntry("projectName", "测试项目");
    }

    @Test
    @DisplayName("Spec 030：filterByProjectAccess 剔除无访问权用户")
    void filtersOutRecipientsWithoutProjectAccess() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of(1L));

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "BID",
                "操作人", DocumentOperationType.UPLOAD, UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("Spec 030：所有候选人被过滤掉 → 跳过通知")
    void skipsWhenAllRecipientsFilteredOut() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "BID",
                "操作人", DocumentOperationType.UPLOAD, UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("删除 → operationType=中文'删除'，body 含中文标签")
    void sendsDeleteOperationType() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of(1L));

        svc.notifyDocumentChanged(PID, 3002L, "废弃文件.docx", "OTHER",
                "李四", DocumentOperationType.DELETE, UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        CreateNotificationRequest req = requestCaptor.getValue();
        assertThat(req.body()).contains("被 李四 删除");
        assertThat(req.payload()).containsEntry("operationType", "删除");
    }

    @Test
    @DisplayName("TENDER 分类 → targetUrl 跳转 initiation 阶段")
    void tenderCategoryMapsToInitiationStage() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of(1L));

        svc.notifyDocumentChanged(PID, 3001L, "招标文件.pdf", "TENDER",
                "操作人", DocumentOperationType.UPLOAD, UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        assertThat(requestCaptor.getValue().payload()).containsEntry("targetUrl", "/project/100/initiation");
    }

    @Test
    @DisplayName("项目不存在 → 跳过通知")
    void skipsWhenProjectNotFound() {
        when(projectRepository.findById(PID)).thenReturn(Optional.empty());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "BID",
                "操作人", DocumentOperationType.UPLOAD, UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("候选接收人为空 → 跳过通知")
    void skipsWhenNoCandidates() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, UID))
                .thenReturn(List.of());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "BID",
                "操作人", DocumentOperationType.UPLOAD, UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("actorUserId=null → 使用 SYSTEM_USER_ID(0L) 作为 createdBy")
    void usesSystemUserIdWhenActorIsNull() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(recipientResolver.resolveAndFilterProjectRecipients(PID, EXPECTED_ROLES, null))
                .thenReturn(List.of(1L));

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "BID",
                "系统", DocumentOperationType.UPLOAD, null);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(0L));
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
    }
}
