// Input: EvaluationBasicDTO（含 CRM 回填的 GAP 附件引用）、tenderId、ProjectDocumentRepository
// Output: 持久化后的 ProjectDocument 列表（用于回填 DTO）
// Pos: Service/业务编排层 - 不依赖 Spring / JPA 运行时状态
// 维护声明: 仅承载 GAP 附件同步逻辑；不携带其他业务规则。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * CO-262: 评估表 GAP 附件同步器。
 *
 * <p>负责将 CRM 商机关联回填的 GAP 附件引用（外部 URL）持久化到
 * project_documents 表（linkedEntityType=EVALUATION_GAP）。
 *
 * <p>策略（CO-262 P1-3 语义）：
 * <ul>
 *   <li>{@code basic == null} 或 {@code projectPlanGapFiles == null}：保留已有附件（不删除、不新增）</li>
 *   <li>{@code projectPlanGapFiles} 为空列表：明确清空，删除已有附件</li>
 *   <li>{@code projectPlanGapFiles} 非空：替换，先删除已有后重建</li>
 * </ul>
 *
 * <p>仅处理外部 URL 引用（fileName + fileUrl），不处理用户上传的 MultipartFile
 * （后者由 {@link TenderEvaluationDocumentService#uploadDocument} 负责）。
 */
class TenderEvaluationGapFilesSync {

    private final ProjectDocumentRepository projectDocumentRepository;

    TenderEvaluationGapFilesSync(ProjectDocumentRepository projectDocumentRepository) {
        this.projectDocumentRepository = projectDocumentRepository;
    }

    /**
     * 同步 GAP 附件：根据请求中的 projectPlanGapFiles 重建 project_documents 记录。
     *
     * <p>CO-262 P1-3 语义：
     * <ul>
     *   <li>{@code basic == null} → 请求未携带 basic 段，保留已有附件（不删除、不新增）</li>
     *   <li>{@code basic.projectPlanGapFiles() == null} → basic 段存在但未提供 gapFiles 字段，保留已有附件</li>
     *   <li>{@code basic.projectPlanGapFiles()} 为空列表 → 明确清空，删除已有附件</li>
     *   <li>{@code basic.projectPlanGapFiles()} 非空 → 替换：删除已有后重建</li>
     * </ul>
     *
     * @return 持久化后的 GAP 附件列表（用于回填 DTO）；若未做任何变更则返回当前已有列表
     */
    List<ProjectDocument> applyGapFiles(Long tenderId, EvaluationBasicDTO basic) {
        // P1-3: basic 为 null 或 projectPlanGapFiles 为 null 时，保留已有附件
        if (basic == null || basic.projectPlanGapFiles() == null) {
            return loadExisting(tenderId);
        }

        // projectPlanGapFiles 为空列表：明确清空，删除已有附件
        if (basic.projectPlanGapFiles().isEmpty()) {
            List<ProjectDocument> existing = loadExisting(tenderId);
            if (!existing.isEmpty()) {
                projectDocumentRepository.deleteAll(existing);
            }
            return List.of();
        }

        // 删除已有 GAP 附件（CRM 关联时替换手动上传的附件）
        List<ProjectDocument> existing = loadExisting(tenderId);
        if (!existing.isEmpty()) {
            projectDocumentRepository.deleteAll(existing);
            projectDocumentRepository.flush();
        }

        // 重建 GAP 附件记录
        List<ProjectDocument> saved = new ArrayList<>();
        for (EvaluationBasicDTO.GapFileRef ref : basic.projectPlanGapFiles()) {
            if (ref == null || ref.fileUrl() == null || ref.fileUrl().isBlank()) {
                continue;
            }
            ProjectDocument doc = ProjectDocument.builder()
                    .projectId(tenderId)
                    .name(ref.fileName() != null && !ref.fileName().isBlank() ? ref.fileName() : "GAP附件")
                    .documentCategory(TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP)
                    .linkedEntityType(TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP)
                    .linkedEntityId(tenderId)
                    .fileUrl(ref.fileUrl())
                    .uploaderName("CRM-同步")
                    .build();
            ProjectDocument persisted = projectDocumentRepository.save(doc);
            // 使用持久化后的实体（含 id）；若 save 返回 null（mock 环境），回退到原构造实体
            saved.add(persisted != null ? persisted : doc);
        }
        return saved;
    }

    /** 加载当前 tenderId 下的所有 GAP 附件（按创建时间倒序）。 */
    private List<ProjectDocument> loadExisting(Long tenderId) {
        return projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
                TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP, tenderId);
    }
}
