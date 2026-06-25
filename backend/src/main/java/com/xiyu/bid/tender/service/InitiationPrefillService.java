// Input: projectId + tenderId + TenderEvaluation（basic + customerInfos + GAP 附件）+ Tender（ownerUnit/customerType）
// Output: 副作用——建 ProjectInitiationDetails（评估数据 + evalPrefilled=true）+ 拷贝 GAP 附件
// Pos: tender/service/ - CO-323 标讯评估表 → 项目立项预填（命令式外壳）
package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CO-323: 标讯评估表 → 项目立项预填服务。
 * <p>{@code TenderEvaluationService.proceedToBid} 创建项目后调用，把评估数据（basic +
 * 客户信息 EAV + GAP 附件）以及标讯基础字段（ownerUnit/customerType）带入 {@link ProjectInitiationDetails}，
 * 并标记 {@code evalPrefilled=true}（前端据此将带入字段设为只读，保证金/招标文件除外）。
 * 幂等（已存在则补全缺失必填字段）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitiationPrefillService {

    private final ProjectInitiationDetailsRepository initiationRepository;
    private final ObjectMapper objectMapper;
    private final ProjectDocumentRepository projectDocumentRepository;

    /**
     * 把评估数据和标讯基础字段带入项目立项。评估表不存在/为空则跳过评估带入（FR-005），
     * 但标讯的 ownerUnit/customerType 仍会带入（必填项），不阻塞 proceedToBid。
     *
     * @param projectId  新创建的项目 id
     * @param tenderId   标讯 id（评估表 GAP 附件的 linkedEntityId）
     * @param evaluation 标讯评估表（可为 null）
     * @param tender     标讯实体（用于带入 ownerUnit/customerType，可为 null）
     */
    @Transactional
    public void prefillFromEvaluation(Long projectId, Long tenderId, TenderEvaluation evaluation, Tender tender) {
        var existingOpt = initiationRepository.findByProjectId(projectId);
        ProjectInitiationDetails details;
        if (existingOpt.isPresent()) {
            // 幂等：已存在则补全缺失的必填字段（如 prefill 曾部分失败）
            details = existingOpt.get();
            boolean updated = false;
            if (details.getOwnerUnit() == null && tender != null
                    && tender.getPurchaserName() != null && !tender.getPurchaserName().isBlank()) {
                details.setOwnerUnit(tender.getPurchaserName());
                updated = true;
            }
            if (details.getCustomerType() == null && tender != null) {
                String mapped = EvaluationToInitiationMapper.mapCustomerType(tender.getCustomerType());
                if (mapped != null) {
                    details.setCustomerType(mapped);
                    updated = true;
                }
            }
            if (updated) {
                initiationRepository.save(details);
                log.info("CO-323: backfilled missing required fields for project {}", projectId);
            } else {
                log.info("CO-323: ProjectInitiationDetails already exists for project {}, skip prefill", projectId);
            }
            return;
        }
        details = ProjectInitiationDetails.builder()
                .projectId(projectId)
                .evalPrefilled(Boolean.TRUE)
                .reviewStatus("DRAFT")
                .build();
        // 标讯基础字段 → 立项 ownerUnit/customerType（必填项，必须带入否则提交立项校验失败）
        EvaluationToInitiationMapper.applyTenderFields(details, tender);
        if (evaluation != null) {
            // CO-323: basic 和 customerInfos 解耦——basic 可为空，有 customerInfos 也要带入
            if (evaluation.getBasic() != null) {
                EvaluationToInitiationMapper.applyEvaluationBasic(details, evaluation.getBasic());
            }
            List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(evaluation.getCustomerInfos());
            try {
                details.setCustomerInfoJson(objectMapper.writeValueAsString(rows));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("CO-323: serialize customer info rows failed", ex);
            }
        }
        initiationRepository.save(details);
        copyGapAttachments(projectId, tenderId);
        log.info("CO-323: prefilled ProjectInitiationDetails for project {} from evaluation", projectId);
    }

    /**
     * CO-323: 拷贝评估表 GAP 附件到新项目（project_documents）。
     * <p>评估表 GAP 附件 {@code linkedEntityType=EVALUATION_GAP}、{@code linkedEntityId=tenderId}；
     * 拷贝后归属本项目（projectId + linkedEntityId 均改为新项目 id），供立项页回填展示。
     * 幂等：上层 {@code prefillFromEvaluation} 已保证仅在首次创建 details 时进入。
     */
    private void copyGapAttachments(Long projectId, Long tenderId) {
        if (tenderId == null) {
            return;
        }
        try {
            List<ProjectDocument> gapFiles = projectDocumentRepository
                    .findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
                            TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP, tenderId);
            if (gapFiles.isEmpty()) {
                return;
            }
            List<ProjectDocument> copies = gapFiles.stream()
                    .map(doc -> ProjectDocument.builder()
                            .projectId(projectId)
                            .name(doc.getName())
                            .size(doc.getSize())
                            .fileType(doc.getFileType())
                            .fileUrl(doc.getFileUrl())
                            .uploaderId(doc.getUploaderId())
                            .uploaderName(doc.getUploaderName() != null ? doc.getUploaderName() : "评估表带入")
                            .documentCategory(TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP)
                            .linkedEntityType(TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP)
                            .linkedEntityId(projectId)
                            .build())
                    .toList();
            projectDocumentRepository.saveAll(copies);
            log.info("CO-323: copied {} gap attachment(s) from tender {} to project {}",
                    copies.size(), tenderId, projectId);
        } catch (RuntimeException ex) {
            // CO-323: 附件拷贝失败不阻塞投标主流程（ProjectInitiationDetails 已在同事务保存）。
            // 此处吞异常，避免外层事务被标记 rollback-only 进而抛 UnexpectedRollbackException；
            // 代价是该次附件未拷贝（不影响投标，可后续重同步补齐）。
            log.warn("CO-323: copy gap attachments failed for tender {}, non-blocking", tenderId, ex);
        }
    }
}
