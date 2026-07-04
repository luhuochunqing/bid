package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.ArchiveFileListService;
import com.xiyu.bid.casework.application.ProjectArchiveDetailService;
import com.xiyu.bid.casework.application.ProjectArchiveExportService;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.StreamingZipPackager;
import com.xiyu.bid.casework.dto.ArchiveFileListItem;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CO-496: 文档分类下载文件视图 Controller 测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CO-496 文档分类下载文件视图 Controller")
class ArchiveFileListControllerTest {

    @Mock private ProjectArchiveWorkflowService workflowService;
    @Mock private ProjectArchiveDetailService detailService;
    @Mock private ProjectArchiveExportService archiveExportService;
    @Mock private StreamingZipPackager streamingZipPackager;
    @Mock private ArchiveFileRepository archiveFileRepository;
    @Mock private ArchiveFileListService archiveFileListService;

    private ProjectArchiveController controller() {
        return new ProjectArchiveController(workflowService, detailService,
                archiveExportService, streamingZipPackager, archiveFileRepository, archiveFileListService);
    }

    @Test
    @DisplayName("文件视图接口复用列表查询并传递带 createdAt DESC 的 Pageable")
    void queryArchiveFiles_sortsByCreatedAtDesc() {
        ArchiveFileListItem item = new ArchiveFileListItem(
                1L, 100L, "项目A", "办公", "INITIATED",
                "招标文件.pdf", "TENDER", "李四", "王五", "张三",
                1024L, LocalDateTime.of(2026, 7, 1, 10, 0));
        Page<ArchiveFileListItem> page = new PageImpl<>(List.of(item));
        when(archiveFileListService.queryFiles(any(ProjectArchiveQuery.class), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Page<ArchiveFileListItem>> resp = controller().queryArchiveFiles(
                new ProjectArchiveQuery(), 0, 10);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getContent()).hasSize(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(archiveFileListService).queryFiles(any(ProjectArchiveQuery.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
