package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDocumentWorkflowServiceTest {

    private ProjectDocumentRepository projectDocumentRepository;
    private ProjectDocumentBindingGateway bindingGateway;
    private ProjectDocumentFileStorage fileStorage;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private ProjectDocumentWorkflowService service;
    private ProjectDocumentDownloadService downloadService;
    private CurrentUserResolver currentUserResolver;
    private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private com.xiyu.bid.project.repository.BidDocumentReviewRepository bidDocumentReviewRepository;
    private ProjectStageService projectStageService;
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        taskRepository = mock(TaskRepository.class);
        projectDocumentRepository = mock(ProjectDocumentRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        userRepository = mock(UserRepository.class);
        bindingGateway = mock(ProjectDocumentBindingGateway.class);
        fileStorage = mock(ProjectDocumentFileStorage.class);
        projectLeadAssignmentRepository = mock(ProjectLeadAssignmentRepository.class);
        currentUserResolver = mock(CurrentUserResolver.class);
        bidDocumentReviewRepository = mock(com.xiyu.bid.project.repository.BidDocumentReviewRepository.class);
        projectStageService = mock(ProjectStageService.class);

        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository,
                projectAccessScopeService,
                taskRepository,
                projectDocumentRepository,
                projectScoreDraftRepository
        );
        ProjectDocumentViewAssembler viewAssembler = new ProjectDocumentViewAssembler();

        service = new ProjectDocumentWorkflowService(
                guardService,
                projectDocumentRepository,
                userRepository,
                projectLeadAssignmentRepository,
                viewAssembler,
                bindingGateway,
                currentUserResolver,
                bidDocumentReviewRepository,
                taskRepository
        );
        downloadService = new ProjectDocumentDownloadService(guardService, fileStorage, projectStageService);

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder().id(1001L).status(Project.Status.BIDDING).build()));
        // CO-375：终态项目（WON）也允许上传/创建文档（复盘阶段需要）
        when(projectRepository.findById(1002L)).thenReturn(Optional.of(Project.builder().id(1002L).status(Project.Status.WON).build()));
        when(currentUserResolver.getCurrentRoleCode()).thenReturn("admin");
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(1L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("admin").build())
                        .build());
        // CO-373：resolveEffectiveRoleCode 委托 EffectiveRoleResolver，非 OSS 用户回退到实体 roleCode
        org.mockito.Mockito.lenient().when(currentUserResolver.resolveEffectiveRoleCode(any(com.xiyu.bid.entity.User.class)))
                .thenAnswer(inv -> inv.<com.xiyu.bid.entity.User>getArgument(0).getRoleCode());
        doReturn(new Long[]{null, null})
                .when(projectLeadAssignmentRepository)
                .resolveLeadIdsByProjectId(1001L);
    }

    @Test
    void createProjectDocument_ShouldPersistExtendedFieldsAndNotifyGateway() {
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3001L);
            document.setCreatedAt(LocalDateTime.of(2026, 4, 18, 10, 30));
            return document;
        });

        ProjectDocumentDTO dto = service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name(" 中标通知书.pdf ")
                .size(" 5MB ")
                .fileType(" application/pdf ")
                .uploaderName(" 王工 ")
                .documentCategory(" BID_RESULT_NOTICE ")
                .linkedEntityType(" BID_RESULT ")
                .linkedEntityId(2001L)
                .fileUrl(" https://files.example.com/notice.pdf ")
                .build());

        assertThat(dto.getName()).isEqualTo("中标通知书.pdf");
        assertThat(dto.getDocumentCategory()).isEqualTo("BID_RESULT_NOTICE");
        assertThat(dto.getLinkedEntityType()).isEqualTo("BID_RESULT");
        assertThat(dto.getLinkedEntityId()).isEqualTo(2001L);
        assertThat(dto.getFileUrl()).isEqualTo("https://files.example.com/notice.pdf");
        verify(bindingGateway).onDocumentCreated(any(ProjectDocument.class));
    }

    @Test
    void createProjectDocument_ShouldAllowOnTerminalProject_WON() {
        // CO-375：复盘阶段在项目中标（WON）后进行，需要上传复盘报告
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3005L);
            document.setCreatedAt(LocalDateTime.of(2026, 6, 28, 10, 0));
            return document;
        });

        ProjectDocumentDTO dto = service.createProjectDocument(1002L, ProjectDocumentCreateRequest.builder()
                .name("复盘报告.pdf")
                .fileType("pdf")
                .uploaderName("李总")
                .documentCategory("RETROSPECTIVE_REPORT")
                .fileUrl("bid-agent://retrospective/1002/report.pdf")
                .build());

        assertThat(dto.getName()).isEqualTo("复盘报告.pdf");
        assertThat(dto.getDocumentCategory()).isEqualTo("RETROSPECTIVE_REPORT");
        verify(bindingGateway).onDocumentCreated(any(ProjectDocument.class));
    }

    @Test
    void getProjectDocumentFile_ShouldLoadStoredDocumentBytes() throws Exception {
        ProjectDocument doc = ProjectDocument.builder()
                .id(3003L)
                .projectId(1001L)
                .name("任务附件.docx")
                .fileType("docx")
                .fileUrl("doc-insight://task/file.docx")
                .build();
        when(projectDocumentRepository.findById(3003L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("doc-insight://task/file.docx"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://task/file.docx",
                        null,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "附件内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile file = downloadService.getProjectDocumentFile(1001L, 3003L);

        assertThat(file.fileName()).isEqualTo("任务附件.docx");
        assertThat(file.fileUrl()).isEqualTo("doc-insight://task/file.docx");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("附件内容".getBytes(StandardCharsets.UTF_8));
        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(file.contentLength()).isEqualTo("附件内容".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void getProjectDocumentFile_ShouldPreferStoredResourceAndInferContentTypeByFileName() throws Exception {
        ProjectDocument doc = ProjectDocument.builder()
                .id(3004L)
                .projectId(1001L)
                .name("投标报价.xlsx")
                .fileUrl("doc-insight://task/price.xlsx")
                .build();
        when(projectDocumentRepository.findById(3004L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("doc-insight://task/price.xlsx"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://task/price.xlsx",
                        null,
                        null,
                        "报价".getBytes(StandardCharsets.UTF_8),
                        new ByteArrayResource("报价".getBytes(StandardCharsets.UTF_8))
                )));

        ProjectDocumentDownloadFile file = downloadService.getProjectDocumentFile(1001L, 3004L);

        assertThat(file.fileName()).isEqualTo("投标报价.xlsx");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("报价".getBytes(StandardCharsets.UTF_8));
        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void getProjectDocuments_ShouldApplyOptionalFilters() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L,
                "BID_RESULT_ANALYSIS",
                "BID_RESULT",
                2002L
        )).thenReturn(List.of(ProjectDocument.builder()
                .id(3002L)
                .projectId(1001L)
                .name("未中标分析报告.docx")
                .size("1MB")
                .fileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .uploaderName("李总")
                .documentCategory("BID_RESULT_ANALYSIS")
                .linkedEntityType("BID_RESULT")
                .linkedEntityId(2002L)
                .fileUrl("https://files.example.com/report.docx")
                .createdAt(LocalDateTime.of(2026, 4, 18, 9, 0))
                .build()));

        List<ProjectDocumentDTO> documents = service.getProjectDocuments(
                1001L,
                "BID_RESULT_ANALYSIS",
                "BID_RESULT",
                2002L
        );

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getDocumentCategory()).isEqualTo("BID_RESULT_ANALYSIS");
        assertThat(documents.getFirst().getLinkedEntityId()).isEqualTo(2002L);
        verify(projectDocumentRepository).findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L,
                "BID_RESULT_ANALYSIS",
                "BID_RESULT",
                2002L
        );
    }

    @Test
    void getProjectDocuments_asAssignedReviewer_shouldBeAllowed() {
        Long reviewerUserId = 42L;
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(reviewerUserId)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-Team").build())
                        .build());
        doReturn(new Long[]{null, null})
                .when(projectLeadAssignmentRepository)
                .resolveLeadIdsByProjectId(1001L);
        when(bidDocumentReviewRepository.findByProjectId(1001L))
                .thenReturn(Optional.of(com.xiyu.bid.project.entity.BidDocumentReviewEntity.builder()
                        .projectId(1001L)
                        .reviewerId(reviewerUserId)
                        .build()));
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null))
                .thenReturn(List.of(ProjectDocument.builder()
                        .id(3006L)
                        .projectId(1001L)
                        .name("投标文件.pdf")
                        .build()));

        List<ProjectDocumentDTO> documents = service.getProjectDocuments(1001L);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getName()).isEqualTo("投标文件.pdf");
    }

    @Test
    void getProjectDocuments_asProjectTaskAssignee_shouldBeAllowed() {
        // CO-361: 项目的任务执行人（非主负责人、非审核人）也需要查看投标文件以完成任务交付。
        // 镜像 getProjectDocuments_asAssignedReviewer_shouldBeAllowed 的写法。
        Long assigneeUserId = 55L;
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(assigneeUserId)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-projectLeader").build())
                        .build());
        doReturn(new Long[]{null, null})
                .when(projectLeadAssignmentRepository)
                .resolveLeadIdsByProjectId(1001L);
        // 策略 deny（bid-projectLeader 非主负责人），审核人也不命中 → 走 isProjectTaskAssignee 放行
        when(bidDocumentReviewRepository.findByProjectId(1001L)).thenReturn(Optional.empty());
        when(taskRepository.existsByProjectIdAndAssigneeId(1001L, assigneeUserId)).thenReturn(true);
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null))
                .thenReturn(List.of(ProjectDocument.builder()
                        .id(3007L)
                        .projectId(1001L)
                        .name("任务交付物.pdf")
                        .build()));

        List<ProjectDocumentDTO> documents = service.getProjectDocuments(1001L);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getName()).isEqualTo("任务交付物.pdf");
        verify(taskRepository).existsByProjectIdAndAssigneeId(1001L, assigneeUserId);
    }

    @Test
    void getProjectDocuments_asNonAssigneeNonReviewerNonLead_shouldThrow() {
        // CO-361: 既非主/副负责人、也非审核人、也非任务执行人 → 仍 403，确保放行不越权。
        Long otherUserId = 66L;
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(otherUserId)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-projectLeader").build())
                        .build());
        doReturn(new Long[]{null, null})
                .when(projectLeadAssignmentRepository)
                .resolveLeadIdsByProjectId(1001L);
        when(bidDocumentReviewRepository.findByProjectId(1001L)).thenReturn(Optional.empty());
        when(taskRepository.existsByProjectIdAndAssigneeId(1001L, otherUserId)).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getProjectDocuments(1001L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("仅项目主负责人可查看项目文档");
    }

    @Test
    void deleteProjectDocument_asAdmin_shouldSucceed() {
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("adminuser");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        com.xiyu.bid.entity.RoleProfile roleProfile = com.xiyu.bid.entity.RoleProfile.builder()
                .code("/bidAdmin")
                .build();
        com.xiyu.bid.entity.User user = com.xiyu.bid.entity.User.builder()
                .username("adminuser")
                .roleProfile(roleProfile)
                .build();
        when(userRepository.findByUsername("adminuser")).thenReturn(Optional.of(user));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9001L)
                .projectId(1001L)
                .name("test.pdf")
                .build();
        when(projectDocumentRepository.findById(9001L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9001L);

        verify(projectDocumentRepository).delete(doc);
        verify(bindingGateway).onDocumentDeleted(doc);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void deleteProjectDocument_asNonAdmin_shouldThrowAccessDeniedException() {
        // CO-383: 非管理员且非上传者本人 → 拒绝
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(888L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-Team").build())
                        .build());

        ProjectDocument doc = ProjectDocument.builder()
                .id(9001L)
                .projectId(1001L)
                .name("test.pdf")
                .uploaderId(1L)
                .build();
        when(projectDocumentRepository.findById(9001L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9001L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("权限不足，仅投标管理员/组长或上传者本人允许删除文档");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteProjectDocument_asUploaderSelf_shouldSucceed() {
        // CO-383: 上传者本人可删除自己上传的文件（未提交前可重传）
        Long uploaderId = 500L;
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(uploaderId)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-projectLeader").build())
                        .build());

        ProjectDocument doc = ProjectDocument.builder()
                .id(9200L)
                .projectId(1001L)
                .name("招标文件.pdf")
                .documentCategory("TENDER_DOCUMENT")
                .uploaderId(uploaderId)
                .build();
        when(projectDocumentRepository.findById(9200L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9200L);

        verify(projectDocumentRepository).delete(doc);
        verify(bindingGateway).onDocumentDeleted(doc);
    }

    // ============ CO-382: 删除文档权限策略对齐蓝图 §3.3.1.2 ============
    // 蓝图：删除文档权限属于「投标管理员/组长」列，即 admin / /bidAdmin / bid-TeamLeader
    // 投标负责人/辅助人（bid-projectLeader, bid-Team）不应删除文档
    // Service 层 Policy 是真权限闸门；Controller @PreAuthorize 只是早过滤，不能取代 Service Policy

    @Test
    void deleteProjectDocument_asBidTeamLeader_shouldSucceed() {
        // 蓝图：投标组长属于「投标管理员/组长」列，允许删除文档
        when(currentUserResolver.getCurrentRoleCode()).thenReturn("bid-TeamLeader");

        ProjectDocument doc = ProjectDocument.builder()
                .id(9101L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID_DOCUMENT")
                .build();
        when(projectDocumentRepository.findById(9101L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9101L);

        verify(projectDocumentRepository).delete(doc);
        verify(bindingGateway).onDocumentDeleted(doc);
    }

    @Test
    void deleteProjectDocument_asBidProjectLeader_shouldThrowAccessDeniedException() {
        // CO-383: bid-projectLeader 非上传者本人 → 拒绝
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(777L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-projectLeader").build())
                        .build());

        ProjectDocument doc = ProjectDocument.builder()
                .id(9102L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID_DOCUMENT")
                .uploaderId(1L)
                .build();
        when(projectDocumentRepository.findById(9102L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9102L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
    }

    // ============ CO-381: 投标文件阶段只读守卫 ============
    // 需求：BID_DOCUMENT 类型在 DRAFTING 阶段可下载；推进到 EVALUATING/CLOSED 等后续阶段后只读不可下载。
    // 非本任务影响的其他类型文档（如 BID_RESULT_NOTICE/RETROSPECTIVE_REPORT）不受阶段守卫影响。

    @Test
    void getProjectDocumentFile_BidDocument_inDraftingStage_succeeds() throws Exception {
        // 场景：标书制作阶段，投标负责人/审核人下载投标文件
        ProjectDocument doc = ProjectDocument.builder()
                .id(3101L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileType("pdf")
                .fileUrl("doc-insight://bid/file.pdf")
                .documentCategory("BID_DOCUMENT")
                .build();
        when(projectDocumentRepository.findById(3101L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.DRAFTING);
        when(fileStorage.load("doc-insight://bid/file.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://bid/file.pdf",
                        null,
                        "application/pdf",
                        "投标内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile file = downloadService.getProjectDocumentFile(1001L, 3101L);

        assertThat(file.fileName()).isEqualTo("投标文件.pdf");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("投标内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getProjectDocumentFile_BidDocument_inEvaluatingStage_throwsBusinessException() {
        // 场景：项目已推进到评标阶段，回到 DRAFTING tab 想下载投标文件 → 拒绝
        ProjectDocument doc = ProjectDocument.builder()
                .id(3102L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileUrl("doc-insight://bid/file.pdf")
                .documentCategory("BID_DOCUMENT")
                .build();
        when(projectDocumentRepository.findById(3102L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.EVALUATING);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        downloadService.getProjectDocumentFile(1001L, 3102L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409)
                .hasMessageContaining("投标文件")
                .hasMessageContaining("只读");

        verify(fileStorage, org.mockito.Mockito.never()).load(any());
    }

    @Test
    void getProjectDocumentFile_BidDocument_inClosedStage_throwsBusinessException() {
        // 场景：项目已结项，回到 DRAFTING tab 想下载投标文件 → 拒绝
        ProjectDocument doc = ProjectDocument.builder()
                .id(3103L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileUrl("doc-insight://bid/file.pdf")
                .documentCategory("BID_DOCUMENT")
                .build();
        when(projectDocumentRepository.findById(3103L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.CLOSED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        downloadService.getProjectDocumentFile(1001L, 3103L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409);

        verify(fileStorage, org.mockito.Mockito.never()).load(any());
    }

    @Test
    void getProjectDocumentFile_nonBidDocument_inEvaluatingStage_succeeds() throws Exception {
        // 守卫只针对 BID_DOCUMENT，其他类型文档（如中标通知书/复盘报告）在任意阶段都能下载
        ProjectDocument doc = ProjectDocument.builder()
                .id(3104L)
                .projectId(1001L)
                .name("中标通知书.pdf")
                .fileType("pdf")
                .fileUrl("doc-insight://result/notice.pdf")
                .documentCategory("BID_RESULT_NOTICE")
                .build();
        when(projectDocumentRepository.findById(3104L)).thenReturn(Optional.of(doc));
        // 不需要 stub projectStageService.currentStage，因为非 BID_DOCUMENT 不会调用
        when(fileStorage.load("doc-insight://result/notice.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://result/notice.pdf",
                        null,
                        "application/pdf",
                        "中标".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile file = downloadService.getProjectDocumentFile(1001L, 3104L);

        assertThat(file.fileName()).isEqualTo("中标通知书.pdf");
        verify(projectStageService, org.mockito.Mockito.never()).currentStage(any());
    }

    @Test
    void getProjectDocumentFile_BidDocument_inDraftingStage_reviewingState_succeeds() throws Exception {
        // 场景：DRAFTING 阶段已 submit-review 进入 REVIEWING 子状态，标书审核人下载投标文件 → 允许
        // （阶段仍是 DRAFTING，submit-review 不推进阶段）
        ProjectDocument doc = ProjectDocument.builder()
                .id(3105L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileType("pdf")
                .fileUrl("doc-insight://bid/file.pdf")
                .documentCategory("BID_DOCUMENT")
                .build();
        when(projectDocumentRepository.findById(3105L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.DRAFTING);
        when(fileStorage.load("doc-insight://bid/file.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://bid/file.pdf",
                        null,
                        "application/pdf",
                        "投标内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile file = downloadService.getProjectDocumentFile(1001L, 3105L);

        assertThat(file.fileName()).isEqualTo("投标文件.pdf");
    }

}
