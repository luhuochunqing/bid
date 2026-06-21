package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
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
                bindingGateway
        );
        downloadService = new ProjectDocumentDownloadService(guardService, fileStorage);

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder().id(1001L).status(Project.Status.BIDDING).build()));
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
    void deleteProjectDocument_asAdmin_shouldSucceed() {
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("adminuser");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        com.xiyu.bid.entity.RoleProfile roleProfile = com.xiyu.bid.entity.RoleProfile.builder()
                .code("bid_admin")
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
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("regularuser");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        com.xiyu.bid.entity.RoleProfile roleProfile = com.xiyu.bid.entity.RoleProfile.builder()
                .code("staff")
                .build();
        com.xiyu.bid.entity.User user = com.xiyu.bid.entity.User.builder()
                .username("regularuser")
                .roleProfile(roleProfile)
                .build();
        when(userRepository.findByUsername("regularuser")).thenReturn(Optional.of(user));

        ProjectDocument doc = ProjectDocument.builder()
                .id(9001L)
                .projectId(1001L)
                .name("test.pdf")
                .build();
        when(projectDocumentRepository.findById(9001L)).thenReturn(Optional.of(doc));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteProjectDocument(1001L, 9001L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("权限不足，仅管理员允许删除文档");

        verify(projectDocumentRepository, org.mockito.Mockito.never()).delete(any());

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
