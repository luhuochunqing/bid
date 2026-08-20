// Input: 项目/标讯上下文 + 合规检查目标配置
// Output: 加载后的合规检查目标集合（生产存储真实取流 + Sidecar 解析，禁止降级）
// Pos: compliance/service/ - 合规检查目标加载服务
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class ComplianceTargetLoader {

    /** 项目仓库. */
    private final ProjectRepository projectRepository;
    /** 标书仓库. */
    private final TenderRepository tenderRepository;
    /** 项目文档仓库. */
    private final ProjectDocumentRepository projectDocumentRepository;
    /** Sidecar 适配器. */
    private final SidecarStreamDocumentAdapter sidecarAdapter;
    /** 投标文件存储分派解析器（三前缀真实取流，spec 049 US1）. */
    private final BidDocumentStorageResolver storageResolver;

    @Autowired
    public ComplianceTargetLoader(
            final ProjectRepository projectRepository,
            final TenderRepository tenderRepository,
            final ProjectDocumentRepository projectDocumentRepository,
            final SidecarStreamDocumentAdapter sidecarAdapter,
            final BidDocumentStorageResolver storageResolver) {
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.sidecarAdapter = sidecarAdapter;
        this.storageResolver = storageResolver;
    }

    /**
     * 针对旧单元测试的兼容构造函数.
     */
    public ComplianceTargetLoader(
            final ProjectRepository projectRepository,
            final TenderRepository tenderRepository) {
        this(projectRepository, tenderRepository, null, null, null);
    }

    /**
     * 根据ID获取项目.
     *
     * @param projectId 项目ID
     * @return 项目实体
     */
    public Project requireProject(final Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found with id: " + projectId));
    }

    /**
     * 根据ID获取标书.
     *
     * @param tenderId 标书ID
     * @return 标书实体
     */
    public Tender requireTender(final Long tenderId) {
        return tenderRepository.findById(tenderId)
                .orElseThrow(() -> new RuntimeException(
                        "Tender not found with id: " + tenderId));
    }

    /**
     * 加载项目关联的最新真实投标文件结构化内容 (DocumentContent).
     *
     * <p>spec 049 第一步：经 {@link BidDocumentStorageResolver} 按
     * {@code ProjectDocumentStorageType} 三前缀分派真实取流，取到物理文件后
     * 交 Sidecar 解析；禁止元数据降级（research.md R1）。
     *
     * @param projectId 项目ID
     * @return 投标文件结构化内容；无上传文档时返回空文档（trigger 前置校验负责 4xx）
     */
    public DocumentContent loadBidDocument(final Long projectId) {
        requireProject(projectId);
        ProjectDocument bidDoc = findLatestBidDocument(projectId);
        if (bidDoc == null || bidDoc.getFileUrl() == null || bidDoc.getFileUrl().isBlank()) {
            log.warn("项目 {} 无投标文件，返回空文档内容", projectId);
            return DocumentContent.empty();
        }

        String fileName = bidDoc.getName() != null ? bidDoc.getName() : "bid_document.pdf";
        Path physicalFile = storageResolver.resolvePhysicalFile(bidDoc.getFileUrl(), fileName);
        try {
            return sidecarAdapter.convertDocument(physicalFile, fileName);
        } finally {
            deleteQuietly(physicalFile);
        }
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

    public ProjectDocument findLatestBidDocument(final Long projectId) {
        if (projectDocumentRepository == null) {
            return null;
        }
        try {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "BID", null, null);
            if (docs != null && !docs.isEmpty()) {
                return docs.get(0);
            }
            List<ProjectDocument> allDocs = projectDocumentRepository
                    .findByProjectIdOrderByCreatedAtDesc(projectId);
            if (allDocs != null && !allDocs.isEmpty()) {
                return allDocs.get(0);
            }
        } catch (RuntimeException e) {
            log.warn("查询 ProjectDocument 异常: {}", e.getMessage());
        }
        return null;
    }
}
