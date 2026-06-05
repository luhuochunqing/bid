package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectShareLink;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectShareLinkRepository;
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

class ProjectShareLinkWorkflowServiceTest {

    private ProjectShareLinkRepository projectShareLinkRepository;
    private ProjectShareLinkWorkflowService service;

    @BeforeEach
    void setUp() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectDocumentRepository projectDocumentRepository = mock(ProjectDocumentRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        projectShareLinkRepository = mock(ProjectShareLinkRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository,
                projectAccessScopeService,
                taskRepository,
                projectDocumentRepository,
                projectScoreDraftRepository
        );
        service = new ProjectShareLinkWorkflowService(
                guardService,
                projectShareLinkRepository,
                userRepository,
                new ProjectShareLinkViewAssembler()
        );

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.INITIATED)
                .build()));
    }

    @Test
    void createProjectShareLink_ShouldPersistGeneratedUrl() {
        when(projectShareLinkRepository.save(any(ProjectShareLink.class))).thenAnswer(invocation -> {
            ProjectShareLink shareLink = invocation.getArgument(0);
            shareLink.setId(5001L);
            shareLink.setCreatedAt(LocalDateTime.of(2026, 4, 19, 11, 0));
            return shareLink;
        });

        ProjectShareLinkDTO dto = service.createProjectShareLink(1001L, ProjectShareLinkCreateRequest.builder()
                .baseUrl(" https://bid.example.com/ ")
                .createdByName(" 王工 ")
                .expiresAt(LocalDateTime.of(2026, 5, 1, 18, 0))
                .build());

        assertThat(dto.getId()).isEqualTo(5001L);
        assertThat(dto.getUrl()).startsWith("https://bid.example.com/project/1001?share=");
        assertThat(dto.getCreatedByName()).isEqualTo("王工");
    }

    @Test
    void getProjectShareLinks_ShouldReturnDtosInRepositoryOrder() {
        when(projectShareLinkRepository.findByProjectIdOrderByCreatedAtDesc(1001L)).thenReturn(List.of(
                ProjectShareLink.builder()
                        .id(5002L)
                        .projectId(1001L)
                        .token("token-1")
                        .url("https://bid.example.com/project/1001?share=token-1")
                        .createdByName("李四")
                        .createdAt(LocalDateTime.of(2026, 4, 19, 11, 30))
                        .build()
        ));

        List<ProjectShareLinkDTO> shareLinks = service.getProjectShareLinks(1001L);

        assertThat(shareLinks).hasSize(1);
        assertThat(shareLinks.getFirst().getToken()).isEqualTo("token-1");
        verify(projectShareLinkRepository).findByProjectIdOrderByCreatedAtDesc(1001L);
    }
}
