package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.notification.DocumentChangeNotificationService;
import com.xiyu.bid.project.notification.DocumentOperationType;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private ProjectStageService projectStageService;
    private DocumentChangeNotificationService documentChangeNotificationService;
    private BidDocumentReviewRepository bidDocumentReviewRepository;
    private ProjectArchiveWorkflowService projectArchiveWorkflowService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        projectDocumentRepository = mock(ProjectDocumentRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        userRepository = mock(UserRepository.class);
        bindingGateway = mock(ProjectDocumentBindingGateway.class);
        fileStorage = mock(ProjectDocumentFileStorage.class);
        currentUserResolver = mock(CurrentUserResolver.class);
        projectStageService = mock(ProjectStageService.class);
        documentChangeNotificationService = mock(DocumentChangeNotificationService.class);
        bidDocumentReviewRepository = mock(BidDocumentReviewRepository.class);
        // spec 039: 归档依赖上提到 createProjectDocument，测试中 mock 该依赖验证调用
        projectArchiveWorkflowService = mock(ProjectArchiveWorkflowService.class);

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
                viewAssembler,
                bindingGateway,
                currentUserResolver,
                documentChangeNotificationService,
                bidDocumentReviewRepository,
                projectArchiveWorkflowService
        );
        downloadService = new ProjectDocumentDownloadService(guardService, fileStorage, projectStageService,
                mock(com.xiyu.bid.file.application.ObsShareUrlSigner.class));

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder().id(1001L).status(Project.Status.BIDDING).build()));
        when(projectRepository.findById(1002L)).thenReturn(Optional.of(Project.builder().id(1002L).status(Project.Status.WON).build()));
        when(currentUserResolver.getCurrentRoleCode()).thenReturn("admin");
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(1L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("admin").build())
                        .build());
        org.mockito.Mockito.lenient().when(currentUserResolver.resolveEffectiveRoleCode(any(com.xiyu.bid.entity.User.class)))
                .thenAnswer(inv -> inv.<com.xiyu.bid.entity.User>getArgument(0).getRoleCode());
        // CO-558: 默认无审核记录（未提交审核），不拦截删除——维持现有删除用例语义。
        // 需要审核状态守卫的用例在自身方法内覆写此 stub。
        org.mockito.Mockito.lenient().when(bidDocumentReviewRepository.findByProjectId(any(Long.class)))
                .thenReturn(Optional.empty());
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
        // BID_RESULT_NOTICE 是业务耦合分类（BidResultProjectDocumentBindingGateway 按此精确匹配触发绑定），
        // 归一化策略原样保留，不归一化为 OTHER
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
        // CO-420: RETROSPECTIVE_REPORT 非标准枚举名，归一化为 OTHER
        assertThat(dto.getDocumentCategory()).isEqualTo("OTHER");
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

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3003L);

        assertThat(result.redirectUrl()).isNull();
        ProjectDocumentDownloadFile file = result;
        assertThat(file.fileName()).isEqualTo("任务附件.docx");
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

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3004L);
        ProjectDocumentDownloadFile file = result;

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
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(9101L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9101L);

        verify(projectDocumentRepository).delete(doc);
        verify(bindingGateway).onDocumentDeleted(doc);
    }

    @Test
    void deleteProjectDocument_inTerminalStatus_shouldThrowBusinessExceptionWithFriendlyMessage() {
        // CO-487: 项目已结项（WON/LOST/FAILED/ABANDONED 均为终态）时删除文档，
        // 必须抛 BusinessException（业务异常）并透传友好 message，
        // 而不是 IllegalStateException（会被全局 handler 吞成"系统状态冲突，请刷新后重试"）。
        // 1002L 在 setUp 中已 mock 为 WON（终态）。
        Long terminalProjectId = 1002L;

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.deleteProjectDocument(terminalProjectId, 9101L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目已结项，不可删除文件");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
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
                .documentCategory("BID")
                .uploaderId(1L)
                .build();
        when(projectDocumentRepository.findById(9102L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9102L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
    }

    // ============ CO-381: 投标文件阶段只读守卫 ============
    // 需求：BID 类型（投标文件）在 DRAFTING 阶段可下载；推进到 EVALUATING/CLOSED 等后续阶段后只读不可下载。
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
                .documentCategory("BID")
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

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3101L);
        ProjectDocumentDownloadFile file = result;

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
                .documentCategory("BID")
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
    void getProjectDocumentFile_BidDocument_inClosedStage_succeeds() throws Exception {
        // CO-442: 项目已结项，下载投标文件作为知识库积累 → 允许
        ProjectDocument doc = ProjectDocument.builder()
                .id(3103L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileType("pdf")
                .fileUrl("doc-insight://bid/file.pdf")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(3103L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.CLOSED);
        when(fileStorage.load("doc-insight://bid/file.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://bid/file.pdf",
                        null,
                        "application/pdf",
                        "投标内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile resultR1 = downloadService.getProjectDocumentFile(1001L, 3103L);
        ProjectDocumentDownloadFile file = resultR1;

        assertThat(file.fileName()).isEqualTo("投标文件.pdf");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("投标内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getProjectDocumentFile_nonBidDocument_inEvaluatingStage_succeeds() throws Exception {
        // 守卫只针对 BID 类型（投标文件），其他类型文档（如中标通知书/复盘报告）在任意阶段都能下载
        ProjectDocument doc = ProjectDocument.builder()
                .id(3104L)
                .projectId(1001L)
                .name("中标通知书.pdf")
                .fileType("pdf")
                .fileUrl("doc-insight://result/notice.pdf")
                .documentCategory("BID_RESULT_NOTICE")
                .build();
        when(projectDocumentRepository.findById(3104L)).thenReturn(Optional.of(doc));
        // 不需要 stub projectStageService.currentStage，因为非 BID 类型不会调用
        when(fileStorage.load("doc-insight://result/notice.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://result/notice.pdf",
                        null,
                        "application/pdf",
                        "中标".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile resultR4 = downloadService.getProjectDocumentFile(1001L, 3104L);
        ProjectDocumentDownloadFile file = resultR4;

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
                .documentCategory("BID")
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

        ProjectDocumentDownloadFile resultR5 = downloadService.getProjectDocumentFile(1001L, 3105L);
        ProjectDocumentDownloadFile file = resultR5;

        assertThat(file.fileName()).isEqualTo("投标文件.pdf");
    }

    // ============ CO-481: 恢复 605ace4a5 设计 — bid-otherDept 通过任务关联获得项目访问权后可查看/下载文档 ============
    // 设计原则（memory 工程约定）：项目子资源访问需通过 ProjectAccessScopeService 校验权限，而非仅依赖 legacy role 白名单。
    // ProjectAccessScopeService 已聚合 9 条权限路径（含任务执行人），bid-otherDept 被分配任务后获得项目访问权，
    // 即应能查看/下载项目文档以完成协作。CO-474 把这当成"漏洞"堵死，导致 09118 用户报 403。
    // 对称性：canUploadProjectDocument 本就包含 bid-otherDept（§24），view/download 移除闸门后三者一致。

    @Test
    void getProjectDocuments_asBidOtherDept_shouldSucceedWhenProjectAccessible() {
        // CO-481: bid-otherDept 通过 ProjectAccessScopeService 闸门后，应能查看项目文档
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(900L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-otherDept").build())
                        .build());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null
        )).thenReturn(List.of(ProjectDocument.builder()
                .id(3002L)
                .projectId(1001L)
                .name("任务附件.docx")
                .fileType("docx")
                .fileUrl("doc-insight://task/file.docx")
                .createdAt(LocalDateTime.of(2026, 7, 3, 10, 0))
                .build()));

        List<ProjectDocumentDTO> documents = service.getProjectDocuments(1001L, null, null, null);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getName()).isEqualTo("任务附件.docx");
        verify(projectDocumentRepository).findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null);
    }

    @Test
    void getProjectDocumentFile_asBidOtherDept_shouldSucceedWhenProjectAccessible() throws Exception {
        // CO-481: bid-otherDept 通过 ProjectAccessScopeService 闸门后，应能下载项目文档
        ProjectDocument doc = ProjectDocument.builder()
                .id(3003L)
                .projectId(1001L)
                .name("任务附件.docx")
                .fileUrl("doc-insight://task/file.docx")
                .build();
        when(projectDocumentRepository.findById(3003L)).thenReturn(Optional.of(doc));
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(900L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-otherDept").build())
                        .build());
        when(fileStorage.load("doc-insight://task/file.docx"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://task/file.docx",
                        null,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "附件内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile resultR3 = downloadService.getProjectDocumentFile(1001L, 3003L);
        ProjectDocumentDownloadFile file = resultR3;

        assertThat(file.fileName()).isEqualTo("任务附件.docx");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("附件内容".getBytes(StandardCharsets.UTF_8));
    }

    // ============ CO-481 防复发：投标专员（bid-Team）作为任务指派人应能查看/下载项目文档 ============
    // 10208 真实场景：投标专员被分配了 task 2950（assignee_id=10208），收到"标书审核"通知后
    // 点击进入项目 147，但 CO-474 引入的第二层 view 闸门（canViewProjectDocuments）拒绝了
    // "仅项目负责人可查看项目文档"。CO-481 移除该闸门后，权限统一走 ProjectAccessScopeService，
    // 该服务通过 taskRepository.findDistinctProjectIdsByAssigneeId 路径覆盖任务指派人场景。
    // 此测试防止未来重新引入针对 bid-Team 的第二层角色白名单闸门。

    @Test
    void getProjectDocuments_asBidTeamAssignee_shouldSucceedWhenProjectAccessible() {
        // CO-481 防复发：bid-Team 是任务指派人但不是项目负责人时，应能查看项目文档
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(7220L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-Team").build())
                        .build());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null
        )).thenReturn(List.of(ProjectDocument.builder()
                .id(3002L)
                .projectId(1001L)
                .name("标书审核材料.docx")
                .fileType("docx")
                .fileUrl("doc-insight://task/file.docx")
                .createdAt(LocalDateTime.of(2026, 7, 3, 18, 6))
                .build()));

        List<ProjectDocumentDTO> documents = service.getProjectDocuments(1001L, null, null, null);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getName()).isEqualTo("标书审核材料.docx");
        // 关键断言：不再有第二层角色白名单闸门拒绝 bid-Team
        verify(projectDocumentRepository).findByProjectIdAndFiltersOrderByCreatedAtDesc(
                1001L, null, null, null);
    }

    @Test
    void getProjectDocumentFile_asBidTeamAssignee_shouldSucceedWhenProjectAccessible() throws Exception {
        // CO-481 防复发：bid-Team 是任务指派人但不是项目负责人时，应能下载项目文档
        ProjectDocument doc = ProjectDocument.builder()
                .id(3003L)
                .projectId(1001L)
                .name("标书审核材料.docx")
                .fileUrl("doc-insight://task/file.docx")
                .build();
        when(projectDocumentRepository.findById(3003L)).thenReturn(Optional.of(doc));
        when(currentUserResolver.requireCurrentUser()).thenReturn(
                com.xiyu.bid.entity.User.builder()
                        .id(7220L)
                        .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-Team").build())
                        .build());
        when(fileStorage.load("doc-insight://task/file.docx"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://task/file.docx",
                        null,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "审核内容".getBytes(StandardCharsets.UTF_8)
                )));

        ProjectDocumentDownloadFile resultR6 = downloadService.getProjectDocumentFile(1001L, 3003L);
        ProjectDocumentDownloadFile file = resultR6;

        assertThat(file.fileName()).isEqualTo("标书审核材料.docx");
        assertThat(file.resource().getContentAsByteArray()).isEqualTo("审核内容".getBytes(StandardCharsets.UTF_8));
    }

    // ============ 蓝图 §消息中心-系统通知 序号 5：文档变更通知 ============
    // 上传/删除项目文档时，应触发 DOCUMENT_CHANGE 通知项目团队成员（排除操作人自己）。
    // 通知通过 ProjectNotificationService.notifyDocumentChanged 实现（不走 EntityChangedEvent 订阅扇出）。

    @Test
    void createProjectDocument_ShouldNotifyDocumentChangedWithUploadOperation() {
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3401L);
            document.setCreatedAt(LocalDateTime.of(2026, 7, 8, 10, 0));
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("中标通知书.pdf")
                .fileType("pdf")
                .uploaderName("王工（1001）")
                .documentCategory("BID_RESULT_NOTICE")
                .fileUrl("https://files.example.com/notice.pdf")
                .build());

        verify(bindingGateway).onDocumentCreated(any(ProjectDocument.class));
        verify(documentChangeNotificationService).notifyDocumentChanged(
                eq(1001L),
                eq(3401L),
                eq("中标通知书.pdf"),
                eq("BID_RESULT_NOTICE"),
                eq("王工（1001）"),
                eq(DocumentOperationType.UPLOAD),
                any()
        );
    }

    @Test
    void deleteProjectDocument_ShouldNotifyDocumentChangedWithDeleteOperationBeforeDelete() {
        // CO-383: 上传者本人可删除自己上传的文件
        Long uploaderId = 500L;
        com.xiyu.bid.entity.User uploader = com.xiyu.bid.entity.User.builder()
                .id(uploaderId)
                .fullName("王工")
                .roleProfile(com.xiyu.bid.entity.RoleProfile.builder().code("bid-projectLeader").build())
                .build();
        when(currentUserResolver.requireCurrentUser()).thenReturn(uploader);

        ProjectDocument doc = ProjectDocument.builder()
                .id(3402L)
                .projectId(1001L)
                .name("招标文件.pdf")
                .documentCategory("TENDER_DOCUMENT")
                .uploaderId(uploaderId)
                .build();
        when(projectDocumentRepository.findById(3402L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 3402L);

        // 关键断言：通知在删除前调用（document 信息完整可用）
        verify(documentChangeNotificationService).notifyDocumentChanged(
                eq(1001L),
                eq(3402L),
                eq("招标文件.pdf"),
                eq("TENDER_DOCUMENT"),
                eq("王工"),
                eq(DocumentOperationType.DELETE),
                eq(uploaderId)
        );
        verify(projectDocumentRepository).delete(doc);
        verify(bindingGateway).onDocumentDeleted(doc);
    }

    @Test
    void createProjectDocument_WithNullUploaderName_ShouldFallbackToUnassigned() {
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3403L);
            return document;
        });

        // uploaderId/uploaderName 均为 null，且无当前用户 → 走"未分配"兜底
        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("测试文档.pdf")
                .fileType("pdf")
                .documentCategory("OTHER")
                .fileUrl("https://files.example.com/test.pdf")
                .build());

        verify(documentChangeNotificationService).notifyDocumentChanged(
                eq(1001L), eq(3403L), eq("测试文档.pdf"),
                eq("OTHER"), eq("未分配"), eq(DocumentOperationType.UPLOAD), eq(null)
        );
    }

    // ============ CO-558: 投标文件审核中/已通过不可删除（BID 类文档审核状态守卫） ============
    // 需求：投标负责人提交标书审核后，审核中（REVIEWING）/已通过（APPROVED）时投标文件不可删除。
    // 仅对 documentCategory=BID 生效；REJECTED 或无审核记录不拦截（允许驳回后修改重传）。
    // Service 层守卫是真闸门，防绕过前端直接调 API。

    private BidDocumentReviewEntity reviewWithStatus(Long projectId, String status) {
        return BidDocumentReviewEntity.builder()
                .projectId(projectId)
                .reviewerId(999L)
                .submittedBy(1L)
                .status(status)
                .build();
    }

    @Test
    void deleteProjectDocument_bidDocUnderReview_shouldThrowBusinessException() {
        // BID 文档 + 审核中（REVIEWING）→ 409 拒绝删除
        when(bidDocumentReviewRepository.findByProjectId(1001L))
                .thenReturn(Optional.of(reviewWithStatus(1001L, "REVIEWING")));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9301L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(9301L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9301L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核中")
                .hasMessageContaining("不可删除");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteProjectDocument_bidDocApproved_shouldThrowBusinessException() {
        // BID 文档 + 已通过（APPROVED）→ 409 拒绝删除
        when(bidDocumentReviewRepository.findByProjectId(1001L))
                .thenReturn(Optional.of(reviewWithStatus(1001L, "APPROVED")));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9302L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(9302L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9302L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已通过审核")
                .hasMessageContaining("不可删除");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteProjectDocument_bidDocRejected_shouldSucceed() {
        // BID 文档 + 被驳回（REJECTED）→ 允许删除（可修改后重传）
        when(bidDocumentReviewRepository.findByProjectId(1001L))
                .thenReturn(Optional.of(reviewWithStatus(1001L, "REJECTED")));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9303L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(9303L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9303L);

        verify(projectDocumentRepository).delete(doc);
    }

    @Test
    void deleteProjectDocument_bidDocNoReviewRecord_shouldSucceed() {
        // BID 文档 + 无审核记录（未提交审核）→ 允许删除
        // setUp 默认 stub 已返回 Optional.empty()，此处显式再写一次以表达用例意图
        when(bidDocumentReviewRepository.findByProjectId(1001L)).thenReturn(Optional.empty());

        ProjectDocument doc = ProjectDocument.builder()
                .id(9304L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(9304L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9304L);

        verify(projectDocumentRepository).delete(doc);
    }

    @Test
    void deleteProjectDocument_nonBidDocUnderReview_shouldSucceed() {
        // 非 BID 文档（TENDER）即使审核中也不受守卫影响 → 允许删除（验证作用域仅限 BID）
        when(bidDocumentReviewRepository.findByProjectId(1001L))
                .thenReturn(Optional.of(reviewWithStatus(1001L, "REVIEWING")));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9305L)
                .projectId(1001L)
                .name("招标文件.pdf")
                .documentCategory("TENDER")
                .build();
        when(projectDocumentRepository.findById(9305L)).thenReturn(Optional.of(doc));

        service.deleteProjectDocument(1001L, 9305L);

        verify(projectDocumentRepository).delete(doc);
    }

    // ============ spec 039: OBS 直传文档归档统一触发 ============
    // 归档逻辑从 multipart 路径（ProjectDocumentUploadWorkflowService）上提到
    // createProjectDocument 末尾，确保 JSON API 创建路径（OBS 直传）也能触发归档。
    // FR-010: 归档失败 try-catch 不阻断主流程。

    @Test
    void createProjectDocument_ShouldAttachFileToArchive() {
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3501L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("投标文件.pdf")
                .size("2MB")
                .fileType("pdf")
                .uploaderId(500L)
                .uploaderName("王工")
                .documentCategory("BID")
                .fileUrl("obs-direct:abc123")
                .build());

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L),
                eq("投标文件.pdf"),
                eq("BID"),
                eq("obs-direct:abc123"),
                eq(0L),  // ARCHIVE_FILE_SIZE_UNKNOWN
                eq(500L),
                eq("王工")
        );
    }

    @Test
    void createProjectDocument_ShouldNormalizeCategoryBeforeArchiving() {
        // 传入历史别名 BID_DOCUMENT，归一化为 BID 后再传给 attachFileToArchive
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3502L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("标书.pdf")
                .fileType("pdf")
                .documentCategory("BID_DOCUMENT")
                .fileUrl("obs-direct:def456")
                .build());

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L), eq("标书.pdf"), eq("BID"), eq("obs-direct:def456"),
                any(), any(), any()
        );
    }

    @Test
    void createProjectDocument_ShouldFallbackToOtherWhenCategoryNull() {
        // documentCategory=null 时，createProjectDocument 把 null 传给 attachFileToArchive
        // attachFileToArchive 内部会兜底为 OTHER（DocumentCategoryNormalizer.normalize(null) 返回 null，
        // 再由 ProjectArchiveWorkflowService 显式兜底为 "OTHER"），无需外层重复兜底
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3503L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("其他文档.pdf")
                .fileType("pdf")
                .fileUrl("obs-direct:ghi789")
                .build());

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L), eq("其他文档.pdf"), eq(null), eq("obs-direct:ghi789"),
                any(), any(), any()
        );
    }

    @Test
    void createProjectDocument_WithArchiveSource_ShouldArchivePhysicalPathAndRealSize() {
        // multipart 路径：透传 DocumentArchiveSource 时，归档 file_path 用本地物理路径
        // （保证 CO-430 档案下载链路可用），file_size 用真实字节数而非 ARCHIVE_FILE_SIZE_UNKNOWN
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3504L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("招标文件.pdf")
                .fileType("pdf")
                .documentCategory("TENDER")
                .fileUrl("doc-insight://tender-file/1001/abc.pdf")
                .build(),
                new DocumentArchiveSource("/tmp/xiyu-doc-insight-uploads/tender-file/1001/abc.pdf", 204800L));

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L), eq("招标文件.pdf"), eq("TENDER"),
                eq("/tmp/xiyu-doc-insight-uploads/tender-file/1001/abc.pdf"),
                eq(204800L), any(), any()
        );
    }

    @Test
    void createProjectDocument_WithoutArchiveSource_ShouldFallbackToFileUrlAndUnknownSize() {
        // OBS 直传 JSON 路径（archiveSource=null）：归档降级为 fileUrl（obs-direct: 伪协议，
        // 下载由 ArchiveFileResponseFactory 302 签发预签名 URL）+ ARCHIVE_FILE_SIZE_UNKNOWN
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3506L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("file.pdf")
                .fileType("pdf")
                .documentCategory("BID")
                .fileUrl("obs-direct:xyz999")
                .build());

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L), eq("file.pdf"), eq("BID"), eq("obs-direct:xyz999"),
                eq(0L), any(), any()
        );
    }

    @Test
    void createProjectDocument_ShouldNotFailWhenArchiveThrows() {
        // FR-010: 归档失败 try-catch 不阻断主流程，createProjectDocument 仍返回 DTO
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3505L);
            return document;
        });
        doThrow(new RuntimeException("模拟归档失败")).when(projectArchiveWorkflowService)
                .attachFileToArchive(any(), any(), any(), any(), any(), any(), any());

        ProjectDocumentDTO dto = service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("容错测试.pdf")
                .fileType("pdf")
                .documentCategory("BID")
                .fileUrl("obs-direct:err001")
                .build());

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("容错测试.pdf");
    }

    @ParameterizedTest
    @CsvSource({
            "BID, 投标文件.pdf",
            "TENDER, 招标文件.pdf",
            "OPEN_LIST, 开标一览表.pdf",
            "WIN_NOTICE, 中标通知书.pdf",
            "DEPOSIT_RECEIPT, 保证金银行回单.pdf"
    })
    void createProjectDocument_ShouldArchiveByCategory(String category, String fileName) {
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3510L);
            return document;
        });

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name(fileName)
                .documentCategory(category)
                .fileUrl("obs-direct:cat001")
                .build());

        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L), eq(fileName), eq(category), eq("obs-direct:cat001"),
                any(), any(), any()
        );
    }

    // ============ Google Code Review: resolveDisplayName OSS 用户场景回归 ============

    @Test
    void createProjectDocument_ShouldResolveOssUserDisplay_WhenEmployeeNumberNullButUsernameIsEmpNo() {
        // OSS 同步用户 username=工号、employeeNumber=null
        // 行为修正（refactor commit 5fd2bd6a1）: 工号取 getDisplayEmployeeNumber()（回退到 username），
        // 之前用 getEmployeeNumber() 会导致 OSS 用户只显示姓名不显示工号
        when(projectDocumentRepository.save(any(ProjectDocument.class))).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(3600L);
            return document;
        });
        com.xiyu.bid.entity.User ossUser = com.xiyu.bid.entity.User.builder()
                .id(500L)
                .username("06234")           // OSS 同步用户 username=工号
                .fullName("郑蓉蓉")
                .employeeNumber(null)         // employee_number 空（OSS 同步未补全）
                .build();
        when(userRepository.findById(500L)).thenReturn(Optional.of(ossUser));

        service.createProjectDocument(1001L, ProjectDocumentCreateRequest.builder()
                .name("OBS直传文档.pdf")
                .fileType("pdf")
                .uploaderId(500L)             // 传 uploaderId 触发 resolveDisplayName
                .uploaderName("06234")        // fallback（应被覆盖）
                .documentCategory("BID")
                .fileUrl("obs-direct:oss001")
                .build());

        // 关键断言：attachFileToArchive 收到的 uploaderName 应为"姓名（工号）"，工号从 username 回退
        verify(projectArchiveWorkflowService).attachFileToArchive(
                eq(1001L),
                eq("OBS直传文档.pdf"),
                eq("BID"),
                eq("obs-direct:oss001"),
                any(),
                eq(500L),
                eq("郑蓉蓉（06234）"));
    }

}
