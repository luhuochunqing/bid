// Input: 项目/标讯上下文 + 合规检查目标配置
// Output: 加载后的合规检查目标集合（文档内容解析，异常容错降级）
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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

    @Autowired
    public ComplianceTargetLoader(
            final ProjectRepository projectRepository,
            final TenderRepository tenderRepository,
            final ProjectDocumentRepository projectDocumentRepository,
            final SidecarStreamDocumentAdapter sidecarAdapter) {
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.sidecarAdapter = sidecarAdapter;
    }

    /**
     * 针对旧单元测试的兼容构造函数.
     */
    public ComplianceTargetLoader(
            final ProjectRepository projectRepository,
            final TenderRepository tenderRepository) {
        this(projectRepository, tenderRepository, null, null);
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
     * @param projectId 项目ID
     * @return 投标文件结构化内容
     */
    public DocumentContent loadBidDocument(final Long projectId) {
        Project project = requireProject(projectId);
        ProjectDocument bidDoc = findLatestBidDocument(projectId);

        if (bidDoc != null) {
            String fileName = bidDoc.getName() != null ? bidDoc.getName() : "bid_document.pdf";
            String fileUrl = bidDoc.getFileUrl();

            // 若存在本地物理文件且 Sidecar 可用，优先调用 Sidecar 解析
            if (fileUrl != null && sidecarAdapter != null) {
                Path localPath = tryResolveLocalPath(fileUrl);
                if (localPath != null && Files.exists(localPath)) {
                    return sidecarAdapter.convertDocument(localPath, fileName);
                }
            }

            // 降级：如果无本地物理文件，基于项目与文档元数据构建基础文档内容
            String fallbackText = buildFallbackDocumentText(project, bidDoc);
            String hash = SidecarStreamDocumentAdapter.computeStringSha256(fallbackText);
            return DocumentContent.fromText(fileName, hash, fallbackText);
        }

        // 若无上传文档记录，降级使用项目描述文本
        String fallbackText = loadProjectDocumentContent(projectId);
        String hash = SidecarStreamDocumentAdapter.computeStringSha256(fallbackText);
        return DocumentContent.fromText(project.getName() != null ? project.getName() : "project_bid.pdf", hash, fallbackText);
    }

    /**
     * 加载项目关联的最新招标文件结构化内容 (DocumentContent).
     */
    public DocumentContent loadTenderDocument(final Long projectId) {
        Project project = requireProject(projectId);
        String tenderText = loadProjectTenderText(projectId);
        String hash = SidecarStreamDocumentAdapter.computeStringSha256(tenderText);
        return DocumentContent.fromText("tender_document.pdf", hash, tenderText);
    }

    /**
     * 加载项目关联的标书文档文本内容（向后兼容）.
     *
     * @param projectId 项目ID
     * @return 文档文本内容
     */
    public String loadProjectDocumentContent(final Long projectId) {
        Project project = requireProject(projectId);
        StringBuilder content = new StringBuilder();
        if (project.getName() != null) {
            content.append("项目名称：").append(project.getName()).append("\n");
        }
        if (project.getDescription() != null) {
            content.append(project.getDescription()).append("\n");
        }
        return content.toString();
    }

    /**
     * 加载项目关联的招标文件文本.
     *
     * @param projectId 项目ID
     * @return 招标文本内容
     */
    public String loadProjectTenderText(final Long projectId) {
        Project project = requireProject(projectId);
        if (project.getTenderId() != null) {
            Tender tender = tenderRepository.findById(
                    project.getTenderId()).orElse(null);
            if (tender != null && tender.getDescription() != null) {
                return tender.getDescription();
            }
        }
        return project.getName() != null ? project.getName() : "";
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

    private Path tryResolveLocalPath(final String fileUrl) {
        try {
            if (fileUrl.startsWith("/") || fileUrl.contains(":\\")) {
                return Paths.get(fileUrl);
            }
            if (fileUrl.startsWith("file://")) {
                return Paths.get(fileUrl.substring(7));
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private String buildFallbackDocumentText(final Project project, final ProjectDocument bidDoc) {
        StringBuilder sb = new StringBuilder();
        sb.append("项目名称：").append(project.getName() != null ? project.getName() : "").append("\n");
        if (bidDoc.getName() != null) {
            sb.append("投标文件：").append(bidDoc.getName()).append("\n");
        }
        if (project.getDescription() != null) {
            sb.append(project.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
