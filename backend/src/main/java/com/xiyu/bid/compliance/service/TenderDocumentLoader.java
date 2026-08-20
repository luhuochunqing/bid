// Input: 项目ID
// Output: 项目最新真实招标文件结构化内容（TENDER/TENDER_FILE 文档定位 + 存储分派 + Sidecar 解析）
// Pos: compliance/service/ - 招标文件加载器（spec 049 US2 / T027，research.md R1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 招标文件真实取流加载器（spec 049 第二步）.
 *
 * <p>按 TENDER/TENDER_FILE 类别直查 {@link ProjectDocumentRepository} 定位
 * 最新招标文件（与 scoreparse 同款查询，避免 compliance→scoreparse 模块环），
 * 随后走与投标文件相同的三前缀存储分派
 * （{@link BidDocumentStorageResolver}）取物理文件并交 Sidecar 解析出分页内容；
 * 替代旧的 Tender.description 元数据文本.
 */
@Component
@Slf4j
public class TenderDocumentLoader {

    /** 招标文件文档类别（与立项招标解析器保持一致）. */
    private static final List<String> TENDER_CATEGORIES = List.of("TENDER", "TENDER_FILE");

    /** 项目文档仓储（TENDER/TENDER_FILE 类别定位）. */
    private final ProjectDocumentRepository projectDocumentRepository;
    /** 项目文档存储分派解析器（三前缀真实取流）. */
    private final BidDocumentStorageResolver storageResolver;
    /** Sidecar 适配器. */
    private final SidecarStreamDocumentAdapter sidecarAdapter;

    public TenderDocumentLoader(
            final ProjectDocumentRepository projectDocumentRepository,
            final BidDocumentStorageResolver storageResolver,
            final SidecarStreamDocumentAdapter sidecarAdapter) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.storageResolver = storageResolver;
        this.sidecarAdapter = sidecarAdapter;
    }

    /**
     * 加载项目关联的最新真实招标文件结构化内容.
     *
     * @param projectId 项目ID
     * @return 招标文件结构化内容；未上传招标文件时返回空文档（提取环节降级 missing）
     */
    public DocumentContent loadTenderDocument(final Long projectId) {
        Optional<ProjectDocument> tenderDoc = findLatestTenderDocument(projectId);
        if (tenderDoc.isEmpty() || tenderDoc.get().getFileUrl() == null
                || tenderDoc.get().getFileUrl().isBlank()) {
            log.warn("项目 {} 未上传招标文件，返回空文档内容", projectId);
            return DocumentContent.empty();
        }
        ProjectDocument doc = tenderDoc.get();
        String fileName = doc.getName() != null ? doc.getName() : "tender_document.pdf";
        Path physicalFile = storageResolver.resolvePhysicalFile(doc.getFileUrl(), fileName, "招标文件");
        try {
            return sidecarAdapter.convertDocument(physicalFile, fileName);
        } finally {
            deleteQuietly(physicalFile);
        }
    }

    /**
     * 定位项目最新的 TENDER/TENDER_FILE 类文档（与 scoreparse 立项招标定位语义一致）.
     */
    private Optional<ProjectDocument> findLatestTenderDocument(final Long projectId) {
        for (String category : TENDER_CATEGORIES) {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, category, null, null);
            if (!docs.isEmpty()) {
                return docs.stream().findFirst();
            }
        }
        return Optional.empty();
    }

    private static void deleteQuietly(final Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理临时文件失败 {}: {}", path, e.getMessage());
        }
    }
}
