// Input: 项目ID与项目文档/标讯元数据
// Output: 验证 TenderDocumentLoader 三级文件定位与穿透回退（TENDER分类 → 文件名模糊 → tender.sourceDocumentFileUrl）
// Pos: compliance/service/ - 招标文件加载器单元测试
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.SidecarParseOutcome;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderDocumentLoaderTest {

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private BidDocumentStorageResolver storageResolver;
    @Mock
    private SidecarStreamDocumentAdapter sidecarAdapter;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;

    private TenderDocumentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TenderDocumentLoader(
                projectDocumentRepository, storageResolver, sidecarAdapter, projectRepository, tenderRepository);
    }

    @Test
    @DisplayName("一级直查：project_documents 存在 TENDER 类别文档时直接使用")
    void testFindLatest_DirectTenderCategory() {
        Long projectId = 100L;
        ProjectDocument doc = ProjectDocument.builder()
                .id(1L)
                .projectId(projectId)
                .documentCategory("TENDER")
                .name("标准招标文件.pdf")
                .fileUrl("oss://bid/tender1.pdf")
                .build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER", null, null))
                .thenReturn(List.of(doc));

        Optional<ProjectDocument> found = loader.findLatestTenderDocument(projectId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("标准招标文件.pdf");
        assertThat(found.get().getFileUrl()).isEqualTo("oss://bid/tender1.pdf");
    }

    @Test
    @DisplayName("二级模糊：project_documents 类别为空但文件名包含招标文件特征")
    void testFindLatest_FilenameHeuristics() {
        Long projectId = 101L;
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER", null, null))
                .thenReturn(Collections.emptyList());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER_FILE", null, null))
                .thenReturn(Collections.emptyList());

        ProjectDocument doc = ProjectDocument.builder()
                .id(2L)
                .projectId(projectId)
                .documentCategory("OTHER")
                .name("某集团数智化采购项目招标文件.docx")
                .fileUrl("oss://bid/tender2.docx")
                .build();
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(doc));

        Optional<ProjectDocument> found = loader.findLatestTenderDocument(projectId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).contains("招标文件");
    }

    @Test
    @DisplayName("三级穿透：project_documents 无文件时，向上穿透回退到 tender.source_document_file_url")
    void testFindLatest_FallbackToTenderSourceDocumentFileUrl() {
        Long projectId = 102L;
        Long tenderId = 555L;

        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER", null, null))
                .thenReturn(Collections.emptyList());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER_FILE", null, null))
                .thenReturn(Collections.emptyList());
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(Collections.emptyList());

        Project project = Project.builder()
                .id(projectId)
                .name("测试项目")
                .tenderId(tenderId)
                .build();
        Tender tender = Tender.builder()
                .id(tenderId)
                .title("测试标讯")
                .sourceDocumentName("立项自带招标文件.pdf")
                .sourceDocumentFileUrl("oss://bid/tender-source-555.pdf")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));

        Optional<ProjectDocument> found = loader.findLatestTenderDocument(projectId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("立项自带招标文件.pdf");
        assertThat(found.get().getFileUrl()).isEqualTo("oss://bid/tender-source-555.pdf");
        assertThat(found.get().getLinkedEntityId()).isEqualTo(tenderId);
    }

    @Test
    @DisplayName("无任何文件源：返回空 DocumentContent")
    void testLoadTenderDocument_NoSource_ReturnsEmpty() {
        Long projectId = 103L;
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER", null, null))
                .thenReturn(Collections.emptyList());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER_FILE", null, null))
                .thenReturn(Collections.emptyList());
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(Collections.emptyList());
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        DocumentContent result = loader.loadTenderDocument(projectId);

        assertThat(result).isEqualTo(DocumentContent.empty());
    }

    @Test
    @DisplayName("Sidecar 解析失败（非 OK）→ 容忍降级为空文档，不抛错")
    void testLoadTenderDocument_SidecarFailure_DegradesToEmpty() {
        Long projectId = 104L;
        ProjectDocument doc = ProjectDocument.builder()
                .id(4L)
                .projectId(projectId)
                .documentCategory("TENDER")
                .name("大招标文件.pdf")
                .fileUrl("bid-agent://tender-documents/p104/tender.pdf")
                .build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "TENDER", null, null))
                .thenReturn(List.of(doc));
        when(storageResolver.resolvePhysicalFile(doc.getFileUrl(), "大招标文件.pdf", "招标文件"))
                .thenReturn(Path.of("/tmp/xiyu-nonexistent-tender-104.pdf"));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("大招标文件.pdf")))
                .thenReturn(SidecarParseOutcome.timeout("解析未在等待上限（645s）内完成"));

        DocumentContent result = loader.loadTenderDocument(projectId);

        assertThat(result).isEqualTo(DocumentContent.empty());
    }
}
