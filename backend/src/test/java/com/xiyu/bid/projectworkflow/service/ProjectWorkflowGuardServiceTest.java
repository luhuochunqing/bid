package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkflowGuardServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @InjectMocks
    private ProjectWorkflowGuardService projectWorkflowGuardService;

    @Test
    void requireProject_ShouldAllowReadForInitiatedProject() {
        Project project = Project.builder()
                .id(1L)
                .status(Project.Status.INITIATED)
                .build();
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        Project result = projectWorkflowGuardService.requireProject(1L);

        assertThat(result.getStatus()).isEqualTo(Project.Status.INITIATED);
    }

    @Test
    void requireProject_ShouldAllowReadForTerminalProject() {
        Project project = Project.builder()
                .id(1L)
                .status(Project.Status.WON)
                .build();
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        Project result = projectWorkflowGuardService.requireProject(1L);

        assertThat(result.getStatus()).isEqualTo(Project.Status.WON);
    }

    @Test
    void requireWorkflowMutationProject_ShouldAllowInitiatedProject() {
        Project project = Project.builder()
                .id(1L)
                .status(Project.Status.INITIATED)
                .build();
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        Project result = projectWorkflowGuardService.requireWorkflowMutationProject(1L);

        assertThat(result.getStatus()).isEqualTo(Project.Status.INITIATED);
    }

    @Test
    void requireWorkflowMutationProject_ShouldRejectTerminalProject() {
        Project project = Project.builder()
                .id(1L)
                .status(Project.Status.WON)
                .build();
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        // CO-565: 改为 BusinessException(409)，避免 Sentry 噪声上报
        assertThatThrownBy(() -> projectWorkflowGuardService.requireWorkflowMutationProject(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("终态")
                .hasMessageContaining("已中标");
    }

    @Test
    void shouldReturnProjectWhenStatusIsBidding() {
        Project project = Project.builder()
                .id(1L)
                .status(Project.Status.BIDDING)
                .build();
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        Project result = projectWorkflowGuardService.requireProject(1L);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }
}
