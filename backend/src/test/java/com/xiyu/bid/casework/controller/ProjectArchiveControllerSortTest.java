package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.ProjectArchiveDetailService;
import com.xiyu.bid.casework.application.ProjectArchiveExportService;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.StreamingZipPackager;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CO-418: 项目档案列表按归档日期倒序排列 — 验证 Controller 传给 service 的 Pageable 带 createdAt DESC。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CO-418 项目档案列表按归档日期倒序排列")
class ProjectArchiveControllerSortTest {

    @Mock private ProjectArchiveWorkflowService workflowService;
    @Mock private ProjectArchiveDetailService detailService;
    @Mock private ProjectArchiveExportService archiveExportService;
    @Mock private StreamingZipPackager streamingZipPackager;
    @Mock private ArchiveFileRepository archiveFileRepository;

    private ProjectArchiveController controller() {
        return new ProjectArchiveController(workflowService, detailService,
                archiveExportService, streamingZipPackager, archiveFileRepository);
    }

    @Test
    @DisplayName("查询档案列表时 Pageable 带 createdAt DESC 排序（最新归档在前）")
    void queryProjectArchives_sortsByCreatedAtDesc() {
        Page<ProjectArchiveResponse> emptyPage = new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(workflowService.queryProjectArchives(any(ProjectArchiveQuery.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        ResponseEntity<Page<ProjectArchiveResponse>> resp = controller().queryProjectArchives(new ProjectArchiveQuery(), 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(workflowService).queryProjectArchives(any(ProjectArchiveQuery.class), captor.capture());
        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        Sort sort = pageable.getSort();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("翻页时排序参数保持 createdAt DESC")
    void queryProjectArchives_page2_keepsSort() {
        Page<ProjectArchiveResponse> emptyPage = new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(workflowService.queryProjectArchives(any(ProjectArchiveQuery.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        controller().queryProjectArchives(new ProjectArchiveQuery(), 2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(workflowService).queryProjectArchives(any(ProjectArchiveQuery.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
