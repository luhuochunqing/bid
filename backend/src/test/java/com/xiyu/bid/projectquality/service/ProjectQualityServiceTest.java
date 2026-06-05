package com.xiyu.bid.projectquality.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectquality.dto.ProjectQualityCheckResponse;
import com.xiyu.bid.projectquality.entity.ProjectQualityCheck;
import com.xiyu.bid.projectquality.entity.ProjectQualityIssue;
import com.xiyu.bid.projectquality.repository.ProjectQualityCheckRepository;
import com.xiyu.bid.projectquality.repository.ProjectQualityIssueRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQualityServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @Mock
    private ProjectQualityCheckRepository projectQualityCheckRepository;

    @Mock
    private ProjectQualityIssueRepository projectQualityIssueRepository;

    @InjectMocks
    private ProjectQualityService projectQualityService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(12L)
                .name("测试项目")
                .tenderId(99L)
                .managerId(1L)
                .status(Project.Status.BIDDING)
                .build();
    }

    @Test
    void runQualityCheckShouldReturnEmptyStateWhenNoDocumentsExist() {
        when(projectRepository.findById(12L)).thenReturn(Optional.of(project));
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(12L)).thenReturn(List.of());
        when(projectQualityCheckRepository.save(any(ProjectQualityCheck.class))).thenAnswer(invocation -> {
            ProjectQualityCheck check = invocation.getArgument(0);
            check.setId(81L);
            check.setCheckedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
            return check;
        });

        ProjectQualityCheckResponse response = projectQualityService.runQualityCheck(12L);

        assertNotNull(response);
        assertTrue(response.isEmpty());
        assertEquals("EMPTY", response.getStatus());
        assertTrue(response.getIssues().isEmpty());
        verify(projectQualityIssueRepository, never()).saveAll(anyList());
    }

    @Test
    void adoptIssueShouldUpdateIssueFlags() {
        ProjectQualityCheck check = ProjectQualityCheck.builder()
                .id(81L)
                .projectId(12L)
                .documentId(55L)
                .documentName("投标文书初稿.docx")
                .status("COMPLETED")
                .empty(false)
                .summary("已完成质量检查")
                .checkedAt(LocalDateTime.of(2026, 4, 21, 10, 0))
                .build();
        ProjectQualityIssue issue = ProjectQualityIssue.builder()
                .id(9001L)
                .checkId(81L)
                .type("grammar")
                .originalText("原文")
                .suggestionText("建议")
                .locationLabel("摘要")
                .adopted(false)
                .ignored(false)
                .build();

        when(projectQualityCheckRepository.findById(81L)).thenReturn(Optional.of(check));
        when(projectQualityIssueRepository.findById(9001L)).thenReturn(Optional.of(issue));
        when(projectQualityIssueRepository.findByCheckIdOrderByIdAsc(81L)).thenReturn(List.of(issue));

        ProjectQualityCheckResponse response = projectQualityService.adoptIssue(12L, 81L, 9001L);

        ArgumentCaptor<ProjectQualityIssue> captor = ArgumentCaptor.forClass(ProjectQualityIssue.class);
        verify(projectQualityIssueRepository).save(captor.capture());
        assertTrue(captor.getValue().isAdopted());
        assertFalse(captor.getValue().isIgnored());
        assertEquals(1, response.getIssues().size());
    }

    @Test
    void ignoreIssueShouldUpdateIssueFlags() {
        ProjectQualityCheck check = ProjectQualityCheck.builder()
                .id(81L)
                .projectId(12L)
                .documentId(55L)
                .documentName("投标文书初稿.docx")
                .status("COMPLETED")
                .empty(false)
                .summary("已完成质量检查")
                .checkedAt(LocalDateTime.of(2026, 4, 21, 10, 0))
                .build();
        ProjectQualityIssue issue = ProjectQualityIssue.builder()
                .id(9002L)
                .checkId(81L)
                .type("format")
                .originalText("原文2")
                .suggestionText("建议2")
                .locationLabel("正文")
                .adopted(false)
                .ignored(false)
                .build();

        when(projectQualityCheckRepository.findById(81L)).thenReturn(Optional.of(check));
        when(projectQualityIssueRepository.findById(9002L)).thenReturn(Optional.of(issue));
        when(projectQualityIssueRepository.findByCheckIdOrderByIdAsc(81L)).thenReturn(List.of(issue));

        ProjectQualityCheckResponse response = projectQualityService.ignoreIssue(12L, 81L, 9002L);

        ArgumentCaptor<ProjectQualityIssue> captor = ArgumentCaptor.forClass(ProjectQualityIssue.class);
        verify(projectQualityIssueRepository).save(captor.capture());
        assertFalse(captor.getValue().isAdopted());
        assertTrue(captor.getValue().isIgnored());
        assertEquals(1, response.getIssues().size());
    }
}
