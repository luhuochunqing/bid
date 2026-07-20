package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.ArchiveFileListService;
import com.xiyu.bid.casework.application.ArchiveFileResponseFactory;
import com.xiyu.bid.casework.application.ProjectArchiveDetailService;
import com.xiyu.bid.casework.application.ProjectArchiveExportService;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.StreamingZipPackager;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目档案导出按勾选 projectIds 过滤 — Controller 层验证。
 *
 * <p>确保 export-excel / export-zip 收到 projectIds 后，
 * 通过 workflowService.getRawArchives 查询并把最终可导出的项目 ID 集传给 export service。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("项目档案导出按勾选 projectIds 过滤")
class ProjectArchiveControllerExportSelectionTest {

    @Mock private ProjectArchiveWorkflowService workflowService;
    @Mock private ProjectArchiveDetailService detailService;
    @Mock private ProjectArchiveExportService archiveExportService;
    @Mock private StreamingZipPackager streamingZipPackager;
    @Mock private ArchiveFileRepository archiveFileRepository;
    @Mock private ArchiveFileListService archiveFileListService;
    @Mock private ArchiveFileResponseFactory archiveFileResponseFactory;

    private ProjectArchiveController controller() {
        return new ProjectArchiveController(workflowService, detailService,
                archiveExportService, streamingZipPackager, archiveFileRepository, archiveFileListService,
                archiveFileResponseFactory);
    }

    private ProjectArchive archive(Long id, Long projectId, String projectName) {
        ProjectArchive a = new ProjectArchive();
        a.setId(id);
        a.setProjectId(projectId);
        a.setProjectName(projectName);
        a.setArchiveStatus("ACTIVE");
        return a;
    }

    @Test
    @DisplayName("导出台账时只导出勾选的项目")
    void exportExcel_withProjectIds_exportsOnlySelectedProjects() throws IOException {
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setProjectIds(List.of(1L, 2L));

        when(workflowService.getRawArchives(query))
                .thenReturn(List.of(archive(10L, 1L, "项目 A"), archive(20L, 2L, "项目 B")));
        when(archiveExportService.resolveExportableProjectIds()).thenReturn(null); // admin
        when(archiveExportService.exportProjectArchives(any()))
                .thenReturn(new ProjectArchiveExportService.ArchiveExportResult(new byte[]{1, 2, 3}, 2));

        ResponseEntity<byte[]> resp = controller().exportExcel(query);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<Set<Long>> captor = ArgumentCaptor.forClass(Set.class);
        verify(archiveExportService).exportProjectArchives(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("导出文件包时只导出勾选的项目")
    void exportZip_withProjectIds_exportsOnlySelectedProjects() throws IOException {
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setProjectIds(List.of(1L, 2L));

        when(workflowService.getRawArchives(query))
                .thenReturn(List.of(archive(10L, 1L, "项目 A"), archive(20L, 2L, "项目 B")));
        when(archiveExportService.resolveExportableProjectIds()).thenReturn(null); // admin
        when(archiveExportService.exportProjectArchives(any(), eq((Set<String>) null)))
                .thenReturn(new ProjectArchiveExportService.ArchiveExportResult(new byte[]{1, 2, 3}, 2));
        when(streamingZipPackager.buildZipBytes(any(), any(Path.class), eq((Set<String>) null)))
                .thenReturn(new byte[]{0x50, 0x4B});

        ResponseEntity<byte[]> resp = controller().exportZip(query, null);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<Set<Long>> captor = ArgumentCaptor.forClass(Set.class);
        verify(archiveExportService).exportProjectArchives(captor.capture(), eq((Set<String>) null));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }
}
