// Input: 项目ID
// Output: 项目最新真实招标文件结构化内容（TENDER/TENDER_FILE 文档定位 + 向上穿透 Tender 回退 + 存储分派 + Sidecar 解析）
// Pos: compliance/service/ - 招标文件加载器（spec 049 US2 / T027，自动穿透 Tender.source_document_file_url）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 招标文件真实取流加载器（spec 049 第二步）.
 *
 * <p>按 TENDER/TENDER_FILE 类别直查 {@link ProjectDocumentRepository} 定位最新招标文件；
 * 若未找到，检查项目内名称含"招标/标书"的文档；
 * 若仍未找到，向上穿透回退到 {@link Tender#getSourceDocumentFileUrl()}（立项自带招标文件），
 * 随后走与投标文件相同的三前缀存储分派（{@link BidDocumentStorageResolver}）
 * 取物理文件并交 Sidecar 解析出分页内容。
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
    /** 项目实体仓储（用于向上回退标讯）. */
    private final ProjectRepository projectRepository;
    /** 标讯实体仓储（用于向上取 sourceDocumentFileUrl）. */
    private final TenderRepository tenderRepository;

    @Autowired
    public TenderDocumentLoader(
            final ProjectDocumentRepository projectDocumentRepository,
            final BidDocumentStorageResolver storageResolver,
            final SidecarStreamDocumentAdapter sidecarAdapter,
            @Autowired(required = false) final ProjectRepository projectRepository,
            @Autowired(required = false) final TenderRepository tenderRepository) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.storageResolver = storageResolver;
        this.sidecarAdapter = sidecarAdapter;
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
    }

    public TenderDocumentLoader(
            final ProjectDocumentRepository projectDocumentRepository,
            final BidDocumentStorageResolver storageResolver,
            final SidecarStreamDocumentAdapter sidecarAdapter) {
        this(projectDocumentRepository, storageResolver, sidecarAdapter, null, null);
    }

    /**
     * 加载项目关联的最新真实招标文件结构化内容.
     *
     * @param projectId 项目ID
     * @return 招标文件结构化内容；未上传或无有效源时返回空文档（提取环节降级 missing）
     */
    public DocumentContent loadTenderDocument(final Long projectId) {
        ProjectDocument doc = findLatestTenderDocument(projectId).orElse(null);
        if (doc == null || doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
            log.warn("项目 {} 未上传且未能回退到有效招标文件，返回空文档内容", projectId);
            return DocumentContent.empty();
        }
        String fileName = doc.getName() != null && !doc.getName().isBlank()
                ? doc.getName() : "tender_document.pdf";
        Path physicalFile = storageResolver.resolvePhysicalFile(doc.getFileUrl(), fileName, "招标文件");
        try {
            return sidecarAdapter.convertDocument(physicalFile, fileName);
        } finally {
            deleteQuietly(physicalFile);
        }
    }

    /**
     * 定位项目最新的招标文件文档（含多级穿透回退）.
     */
    public Optional<ProjectDocument> findLatestTenderDocument(final Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }

        // 1. 首选：按 TENDER / TENDER_FILE 类别直接查询
        for (String category : TENDER_CATEGORIES) {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, category, null, null);
            Optional<ProjectDocument> usable = docs.stream().filter(TenderDocumentLoader::isDocumentUsable).findFirst();
            if (usable.isPresent()) {
                return usable;
            }
        }

        // 2. 次选：查询项目所有文档，按关联实体或文件名模糊匹配
        List<ProjectDocument> allDocs = projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (ProjectDocument doc : allDocs) {
            if (isDocumentUsable(doc) && (
                    "TENDER".equalsIgnoreCase(doc.getLinkedEntityType())
                    || nameLooksLikeTender(doc.getName()))) {
                log.info("项目 {} 通过文件名/关联属性命中招标文件: id={}, name={}", projectId, doc.getId(), doc.getName());
                return Optional.of(doc);
            }
        }

        // 3. 终极兜底：向上穿透到 tender.source_document_file_url（立项自带招标文件）
        if (projectRepository != null && tenderRepository != null) {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null && project.getTenderId() != null) {
                Long tenderId = project.getTenderId();
                Tender tender = tenderRepository.findById(tenderId).orElse(null);
                if (tender != null) {
                    String fileUrl = tender.getSourceDocumentFileUrl();
                    if (fileUrl != null && !fileUrl.isBlank()) {
                        String name = tender.getSourceDocumentName() != null && !tender.getSourceDocumentName().isBlank()
                                ? tender.getSourceDocumentName() : "招标文件.pdf";
                        log.info("项目 {} 从关联标讯 {} 向上穿透回退招标文件: {}", projectId, tenderId, name);
                        return Optional.of(ProjectDocument.builder()
                                .projectId(projectId)
                                .name(name)
                                .documentCategory("TENDER")
                                .linkedEntityType("TENDER")
                                .linkedEntityId(tenderId)
                                .fileUrl(fileUrl)
                                .uploaderName("系统穿透回退")
                                .build());
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isDocumentUsable(final ProjectDocument doc) {
        return doc != null && doc.getFileUrl() != null && !doc.getFileUrl().isBlank();
    }

    private static boolean nameLooksLikeTender(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase();
        if (lower.contains("投标文件") || lower.contains("投标书") || lower.contains("报价") || lower.contains("开标")) {
            return false;
        }
        return lower.contains("招标") || lower.contains("标书")
                || lower.contains("采购文件") || lower.contains("谈判文件") || lower.contains("询价文件");
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
