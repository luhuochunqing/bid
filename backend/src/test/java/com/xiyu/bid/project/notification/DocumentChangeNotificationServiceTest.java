// Output: notifyDocumentChanged 的分支覆盖（项目存在性、团队成员过滤、操作类型透传）
// Pos: project/notification/ - 文档变更通知纯编排层测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentChangeNotificationService — 蓝图 §消息中心-系统通知 序号 5")
class DocumentChangeNotificationServiceTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private DocumentChangeNotificationService svc;

    private static final Long PID = 100L;
    private static final Long UID = 42L;

    @BeforeEach
    void setUp() {
        svc = new DocumentChangeNotificationService(notificationService, projectRepository, projectMemberRepository);
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
    @DisplayName("上传 → 发送 DOCUMENT_CHANGE 给团队成员（排除操作人自己）")
    void sendsDocumentChangeToTeamMembersExcludingActor() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        // 团队成员含操作人 UID=42 自己，应被过滤掉
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(UID), member(1L), member(2L)));

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
        assertThat(req.payload()).containsEntry("targetUrl", "/project/100/drafting");
    }

    @Test
    @DisplayName("删除 → operationType=删除 透传到 payload 与 body")
    void sendsDeleteOperationType() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID))
                .thenReturn(List.of(member(1L)));

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
    @DisplayName("团队成员为空 → 跳过通知")
    void skipsWhenNoTeamMembers() {
        when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
        when(projectMemberRepository.findByProjectId(PID)).thenReturn(List.of());

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "操作人", "上传", UID);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("仅操作人自己是团队成员 → 过滤后为空 → 跳过通知")
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

        svc.notifyDocumentChanged(PID, 3001L, "文档.pdf", "系统", "上传", null);

        verify(notificationService).createNotification(requestCaptor.capture(), eq(0L));
        assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
    }
}
