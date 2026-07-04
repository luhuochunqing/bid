package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ArchiveFileListItem;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CO-496: 文档分类下载文件视图列表查询测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CO-496 文档分类下载文件视图列表")
class ArchiveFileListServiceTest {

    @Mock private ProjectArchiveWorkflowService workflowService;
    @Mock private ArchiveFileRepository fileRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectInitiationDetailsRepository initiationDetailsRepository;

    private ArchiveFileListService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveFileListService(workflowService, fileRepository,
                projectRepository, tenderRepository, initiationDetailsRepository);
    }

    @Test
    @DisplayName("admin 可查看所有项目的归档文件")
    void queryFiles_adminCanSeeAllFiles() {
        ProjectArchive archive = archive(1L, 100L, "项目A", "ACTIVE");
        ArchiveFile file = file(1L, 1L, "招标文件.pdf", "TENDER", 1024L,
                "张三", LocalDateTime.of(2026, 7, 1, 10, 0));
        Project project = project(100L, 10L);
        Tender tender = tender(10L, "办公", "李四", "招标主体A");
        ProjectInitiationDetails details = initiationDetails(100L, "王五");

        when(workflowService.getRawArchives(any(ProjectArchiveQuery.class)))
                .thenReturn(List.of(archive));
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(List.of(file));
        lenient().when(projectRepository.findAllById(any())).thenReturn(List.of(project));
        lenient().when(tenderRepository.findAllById(any())).thenReturn(List.of(tender));
        lenient().when(initiationDetailsRepository.findByProjectIdIn(any()))
                .thenReturn(List.of(details));

        Page<ArchiveFileListItem> page = service.queryFiles(new ProjectArchiveQuery(),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        ArchiveFileListItem item = page.getContent().get(0);
        assertThat(item.fileName()).isEqualTo("招标文件.pdf");
        assertThat(item.documentCategory()).isEqualTo("TENDER");
        assertThat(item.projectName()).isEqualTo("项目A");
        assertThat(item.projectType()).isEqualTo("办公");
        assertThat(item.projectManager()).isEqualTo("李四");
        assertThat(item.bidManager()).isEqualTo("王五");
        assertThat(item.uploaderName()).isEqualTo("张三");
        assertThat(item.fileSize()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("无可见项目时返回空页")
    void queryFiles_noAllowedProjects_returnsEmptyPage() {
        when(workflowService.getRawArchives(any(ProjectArchiveQuery.class)))
                .thenReturn(Collections.emptyList());

        Page<ArchiveFileListItem> page = service.queryFiles(new ProjectArchiveQuery(),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("文档分类筛选精确到文件行")
    void queryFiles_filterByDocumentCategory() {
        ProjectArchive archive = archive(1L, 100L, "项目A", "ACTIVE");
        ArchiveFile tenderFile = file(1L, 1L, "招标文件.pdf", "TENDER", 1024L,
                "张三", LocalDateTime.of(2026, 7, 1, 10, 0));
        ArchiveFile bidFile = file(2L, 1L, "投标文件.pdf", "BID", 2048L,
                "张三", LocalDateTime.of(2026, 7, 2, 10, 0));

        when(workflowService.getRawArchives(any(ProjectArchiveQuery.class)))
                .thenReturn(List.of(archive));
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(List.of(tenderFile, bidFile));
        lenient().when(projectRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(tenderRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(initiationDetailsRepository.findByProjectIdIn(any())).thenReturn(List.of());

        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setDocumentCategories(List.of("BID"));
        Page<ArchiveFileListItem> page = service.queryFiles(query, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).fileName()).isEqualTo("投标文件.pdf");
    }

    @Test
    @DisplayName("上传时间筛选精确到文件行")
    void queryFiles_filterByUploadTime() {
        ProjectArchive archive = archive(1L, 100L, "项目A", "ACTIVE");
        ArchiveFile earlyFile = file(1L, 1L, "早期文件.pdf", "TENDER", 1024L,
                "张三", LocalDateTime.of(2026, 6, 28, 10, 0));
        ArchiveFile lateFile = file(2L, 1L, "晚期文件.pdf", "TENDER", 2048L,
                "张三", LocalDateTime.of(2026, 7, 3, 10, 0));

        when(workflowService.getRawArchives(any(ProjectArchiveQuery.class)))
                .thenReturn(List.of(archive));
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(List.of(earlyFile, lateFile));
        lenient().when(projectRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(tenderRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(initiationDetailsRepository.findByProjectIdIn(any())).thenReturn(List.of());

        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setUploadTimeStart("2026-07-01");
        query.setUploadTimeEnd("2026-07-05");
        Page<ArchiveFileListItem> page = service.queryFiles(query, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).fileName()).isEqualTo("晚期文件.pdf");
    }

    @Test
    @DisplayName("分页正确返回子集")
    void queryFiles_paginationWorks() {
        ProjectArchive archive = archive(1L, 100L, "项目A", "ACTIVE");
        List<ArchiveFile> files = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(i -> file((long) i, 1L, "文件" + i + ".pdf", "TENDER", (long) i,
                        "张三", LocalDateTime.of(2026, 7, 1, 10, i % 60)))
                .toList();

        when(workflowService.getRawArchives(any(ProjectArchiveQuery.class)))
                .thenReturn(List.of(archive));
        when(fileRepository.findByArchiveIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(files);
        lenient().when(projectRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(tenderRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(initiationDetailsRepository.findByProjectIdIn(any())).thenReturn(List.of());

        Page<ArchiveFileListItem> page = service.queryFiles(new ProjectArchiveQuery(),
                PageRequest.of(1, 10));

        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    private ProjectArchive archive(Long id, Long projectId, String projectName, String status) {
        ProjectArchive a = new ProjectArchive();
        a.setId(id);
        a.setProjectId(projectId);
        a.setProjectName(projectName);
        a.setArchiveStatus(status);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    private ArchiveFile file(Long id, Long archiveId, String fileName, String category,
                              Long size, String uploaderName, LocalDateTime createdAt) {
        ArchiveFile f = new ArchiveFile();
        f.setId(id);
        f.setArchiveId(archiveId);
        f.setFileName(fileName);
        f.setDocumentCategory(category);
        f.setFileSize(size);
        f.setUploadUserName(uploaderName);
        f.setUploadUserId(0L);
        f.setFilePath("/tmp/" + fileName);
        f.setCreatedAt(createdAt);
        return f;
    }

    private Project project(Long id, Long tenderId) {
        Project p = new Project();
        p.setId(id);
        p.setTenderId(tenderId);
        p.setStatus(Project.Status.INITIATED);
        return p;
    }

    private Tender tender(Long id, String projectType, String projectManagerName, String purchaserName) {
        Tender t = new Tender();
        t.setId(id);
        t.setProjectType(projectType);
        t.setProjectManagerName(projectManagerName);
        t.setPurchaserName(purchaserName);
        return t;
    }

    private ProjectInitiationDetails initiationDetails(Long projectId, String biddingLeaderName) {
        ProjectInitiationDetails d = new ProjectInitiationDetails();
        d.setProjectId(projectId);
        d.setBiddingLeaderName(biddingLeaderName);
        return d;
    }
}
