// Output: notifyDocumentChanged 的分支覆盖（项目存在性、Spec 030 接收人过滤、操作类型透传）
// Pos: project/notification/ - 文档变更通知纯编排层测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
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
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private DocumentChangeNotificationService svc;

    private static final Long PID = 100L;
    private static final Long UID = 42L;

    @BeforeEach
    void setUp() {
        svc = new DocumentChangeNotificationService(
                notificationService, projectRepository, projectMemberRepository, projectAccessScopeService);
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
        return p;
    }

    private ProjectMember member(Long userId) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        return m;
    }

    @Test
    @DisplayName("上传 → 发送 DOCUMENT_CHANGE 给有项目访问权的团队成员（排除操作人自己）")
    void sendsDocumentChangeToAccessibleTeamMembersExcludingActor() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(UID), member(1L), member(2L)));
        // 操作人 UID=42 已在前置过滤排除；1L 和 2L 都有访问权
        when(projectAccessScopeService.canAccessProject(1L, PID)).thenReturn(true);
        when(projectAccessScopeService.canAccessProject(2L, PID)).thenReturn(true);

        svc.notifyDocumentChanged(PID, 3001L, "中标通知书.pdf", "王工（1001）", "上传", UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        CreateNotificationRequest req = requestCaptor.getValue();
        assertThat(req.type()).isEqualTo("DOCUMENT_CHANGE");
        assertThat(req.sourceEntityType()).isEqualTo("DOCUMENT");
        assertThat(req.sourceEntityId()).isEqualTo(3001L);
        assertThat(req.title()).isEqualTo("文档变更 - 测试项目");
        assertThat(req.body()).contains("文档「中标通知书.pdf」被 王工（1001） 上传");
        // 关键断言：操作人自己被排除
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(req.payload()).containsEntry("operationType", "上传");
        assertThat(req.payload()).containsEntry("documentName", "中标通知书.pdf");
        // P0-1：payload targetUrl 精确指向项目 drafting 页（企微外发会用它覆盖默认 /document/editor/ 跳转）
        assertThat(req.payload()).containsEntry("targetUrl", "/project/100/drafting");
    }

    @Test
    @DisplayName("Spec 030：剔除对该项目无访问权的团队成员（避免点击后 403）")
    void filtersOutRecipientsWithoutProjectAccess() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L), member(2L), member(3L)));
        // 1L 有访问权，2L 无访问权（如历史成员/已停用），3L 无访问权
        when(projectAccessScopeService.canAccessProject(1L, PID)).thenReturn(true);
        when(projectAccessScopeService.canAccessProject(2L, PID)).thenReturn(false);
        when(projectAccessScopeService.canAccessProject(3L, PID)).thenReturn(false);

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        // 关键断言：只有 1L 收到通知
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("Spec 030 降级：access scope 异常时回退到未过滤广播")
    void fallsBackToUnfilteredBroadcastWhenAccessScopeThrows() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L), member(2L)));
        when(projectAccessScopeService.canAccessProject(any(), eq(PID)))
                .thenThrow(new RuntimeException("DB 故障"));

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        // 关键断言：降级为原候选广播
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("Spec 030：所有候选人都无访问权 → 跳过通知")
    void skipsWhenAllRecipientsFilteredOut() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L), member(2L)));
        when(projectAccessScopeService.canAccessProject(1L, PID)).thenReturn(false);
        when(projectAccessScopeService.canAccessProject(2L, PID)).thenReturn(false);

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("删除 → operationType=删除 透传到 payload 与 body")
    void sendsDeleteOperationType() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L)));
        when(projectAccessScopeService.canAccessProject(1L, PID)).thenReturn(true);

        svc.notifyDocumentChanged(PID, 3002L, "废弃文件.docx", "李四", "删除", UID);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
        CreateNotificationRequest req = requestCaptor.getValue();
        assertThat(req.body()).contains("被 李四 删除");
        assertThat(req.payload()).containsEntry("operationType", "删除");
    }

    @Test
    @DisplayName("项目不存在 → 跳过通知")
    void skipsWhenProjectNotFound() {
        when(projectRepository.findById(PID)).thenReturn(Optional.empty());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("候选团队成员为空 → 跳过通知")
    void skipsWhenNoCandidates() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID)).thenReturn(List.of());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("仅操作人自己是团队成员 → 前置过滤后为空 → 跳过通知")
    void skipsWhenOnlyActorInTeam() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(UID)));

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("actorUserId=null → 使用 SYSTEM_USER_ID(0L) 作为 createdBy")
    void usesSystemUserIdWhenActorIsNull() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L)));
        when(projectAccessScopeService.canAccessProject(1L, PID)).thenReturn(true);

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "系统", "上传", null);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(0L));
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
    }
}
