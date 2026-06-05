package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private ProjectDocumentWorkflowService service;

    @BeforeEach
    void setUp() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        projectDocumentRepository = mock(ProjectDocumentRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        bindingGateway = mock(ProjectDocumentBindingGateway.class);

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

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder().id(1001L).build()));
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
}
