// Input: 项目/标讯上下文 + 合规检查目标配置
// Output: 加载后的合规检查目标集合（生产存储真实取流 + Sidecar 解析，禁止降级；多册投标文件逐份取流后合并）
// Pos: compliance/service/ - 合规检查目标加载服务
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.Comparator;
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
     * 加载项目关联的全部真实投标文件结构化内容 (DocumentContent)，多册合并为单个逻辑文档.
     *
     * <p>spec 049 第一步：经 {@link BidDocumentStorageResolver} 按
     * {@code ProjectDocumentStorageType} 三前缀分派真实取流，取到物理文件后
     * 交 Sidecar 解析；禁止元数据降级（research.md R1）。
     *
     * <p>多文件质量核查：项目按价格/商务/技术分册上传多份 BID 文件时，逐份取流解析，
     * 经 {@link DocumentContent#merge} 合并——25 项引擎与证据链继续消费单个文档，零改动。
     * 任一册解析失败则整体失败（宁可报错不可假查），错误信息指名该册文件。
     *
     * @param projectId 项目ID
     * @return 合并后的投标文件结构化内容；无上传文档时返回空文档（trigger 前置校验负责 4xx）
     */
    public DocumentContent loadBidDocument(final Long projectId) {
        requireProject(projectId);
        List<ProjectDocument> bidDocs = findAllBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            log.warn("项目 {} 无投标文件，返回空文档内容", projectId);
            return DocumentContent.empty();
        }

        List<DocumentContent> parsedDocuments = new ArrayList<>(bidDocs.size());
        for (ProjectDocument bidDoc : bidDocs) {
            // 展示名与解析名统一兜底：null/空白名以 bid_document.pdf 口径进入合并文件名，
            // 与 trigger 侧 joinBidFileNames 的兜底规则一致，避免两侧展示口径漂移
            String fileName = bidDoc.getName() != null && !bidDoc.getName().isBlank()
                    ? bidDoc.getName() : "bid_document.pdf";
            String parseFailMessage = "投标文件解析失败：" + fileName + "，请确认文件可正常打开后重新上传";
            Path physicalFile = storageResolver.resolvePhysicalFile(bidDoc.getFileUrl(), fileName);
            DocumentContent parsed;
            try {
                parsed = sidecarAdapter.convertDocument(physicalFile, fileName);
            } catch (RuntimeException e) {
                throw new BusinessException(422, parseFailMessage, e);
            } finally {
                deleteQuietly(physicalFile);
            }
            // Sidecar 适配器内部吞异常、失败时静默返回空文本册（真实失败形态），
            // 必须在此显式拦截：空册并入合并文档 = 用 2/3 内容出"合格"报告的假查
            if (parsed.fullText().isBlank() && parsed.pages().isEmpty()) {
                throw new BusinessException(422, parseFailMessage);
            }
            parsedDocuments.add(parsed);
        }
        return DocumentContent.merge(parsedDocuments);
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

    /**
     * 查询项目全部有效投标文件（BID 类、fileUrl 非空），按上传顺序（id 升序）排列.
     *
     * <p>id 为数据库自增主键，升序即上传顺序，保证多册合并顺序与聚合哈希确定性。
     * fileUrl 为空的脏记录被过滤（log 提示），不参与核查也不阻断其余册。
     * 不再兜底取其他类别文档：把招标文件当投标文件核查属于假查。
     */
    public List<ProjectDocument> findAllBidDocuments(final Long projectId) {
        if (projectDocumentRepository == null) {
            return List.of();
        }
        try {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "BID", null, null);
            if (docs == null) {
                return List.of();
            }
            return docs.stream()
                    .filter(doc -> {
                        if (doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
                            log.warn("项目 {} 投标文件记录 {}（id={}）fileUrl 为空，跳过不参与核查",
                                    projectId, doc.getName(), doc.getId());
                            return false;
                        }
                        return true;
                    })
                    .sorted(Comparator.comparing(ProjectDocument::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("查询 ProjectDocument 异常: {}", e.getMessage());
        }
        return List.of();
    }
}
