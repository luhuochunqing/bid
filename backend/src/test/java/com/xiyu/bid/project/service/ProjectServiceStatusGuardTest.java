package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceStatusGuardTest {

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
                null, // ProjectImportService
                null, // ProjectQueryService
                null, // ProjectListQueryService
                null, // ProjectLeadAssignmentRepository
                new CustomFieldsCodec(new com.fasterxml.jackson.databind.ObjectMapper())
        );
    }

    @Test
    void updateProject_shouldThrowException_whenRevertingFromInitiatedToPendingInitiation() {
        Project existing = Project.builder()
                .id(1364L)
                .name("测试项目")
                .tenderId(2273L)
                .managerId(101L)
                .status(Project.Status.INITIATED)
                .stage("DRAFTING")
                .teamMembers(List.of(101L))
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(10))
                .build();
        when(projectRepository.findById(1364L)).thenReturn(Optional.of(existing));

        ProjectDTO updates = ProjectDTO.builder()
                .name("测试项目")
                .tenderId(2273L)
                .managerId(101L)
                .status(Project.Status.PENDING_INITIATION)
                .build();

        assertThatThrownBy(() -> projectService.updateProject(1364L, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot revert project status to PENDING_INITIATION once initiated");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_shouldAllowUpdatingOtherFields_withoutChangingStatus() {
        Project existing = Project.builder()
                .id(1364L)
                .name("测试项目")
                .tenderId(2273L)
                .managerId(101L)
                .status(Project.Status.INITIATED)
                .stage("DRAFTING")
                .teamMembers(List.of(101L))
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(10))
                .build();
        when(projectRepository.findById(1364L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectDTO updates = ProjectDTO.builder()
                .name("新项目名称")
                .tenderId(2273L)
                .managerId(101L)
                .build();

        ProjectDTO result = projectService.updateProject(1364L, updates);

        assertThat(result.getName()).isEqualTo("新项目名称");
        assertThat(existing.getStatus()).isEqualTo(Project.Status.INITIATED);
        verify(projectRepository).save(existing);
    }
}
