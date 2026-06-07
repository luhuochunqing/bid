package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceDemoModeTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private DemoModeService demoModeService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                projectAccessScopeService,
                demoModeService,
                new DemoDataProvider(),
                new DemoFusionService(),
                null,
                null,
                null
        );
    }

    @Test
    void getProjectById_shouldReturnDemoProjectWhenE2eAndNegativeId() {
        when(demoModeService.isEnabled()).thenReturn(true);

        var project = projectService.getProjectById(-101L);

        assertThat(project).isNotNull();
        assertThat(project.getId()).isEqualTo(-101L);
        assertThat(project.getName()).isNotBlank();
    }

    @Test
    void updateProject_shouldRejectDemoMutation() {
        when(demoModeService.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> projectService.updateProject(-101L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void getProjectById_shouldThrowWhenDemoIdUnknown() {
        when(demoModeService.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> projectService.getProjectById(-9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createProject_shouldReuseExistingProjectForSameTender() {
        Project existingProject = Project.builder()
                .id(21L)
                .name("既有立项")
                .tenderId(88001L)
                .status(Project.Status.INITIATED)
                .managerId(2L)
                .teamMembers(List.of(2L))
                .build();
        List<Project> existingProjects = List.of(existingProject);
        when(projectRepository.findByTenderId(88001L)).thenReturn(existingProjects);
        when(projectAccessScopeService.filterAccessibleProjects(existingProjects)).thenReturn(existingProjects);

        ProjectDTO result = projectService.createProject(ProjectDTO.builder()
                .name("重复立项")
                .tenderId(88001L)
                .managerId(2L)
                .teamMembers(List.of(2L))
                .build());

        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getName()).isEqualTo("既有立项");
        verify(projectRepository, never()).save(any(Project.class));
    }
}
