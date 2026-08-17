package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.entity.TenderEvaluationRecommendation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationGapFilesSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部标讯集成评估数据服务。
 * 负责评估数据的保存和转换。
 */
@Service
public class TenderEvaluationIntegrationService {

    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderEvaluationIntegrationMapper mapper;
    private final TenderEvaluationGapFilesSync gapFilesSync;

    public TenderEvaluationIntegrationService(TenderEvaluationRepository tenderEvaluationRepository,
                                              TenderEvaluationIntegrationMapper mapper,
                                              ProjectDocumentRepository projectDocumentRepository) {
        this.tenderEvaluationRepository = tenderEvaluationRepository;
        this.mapper = mapper;
        this.gapFilesSync = new TenderEvaluationGapFilesSync(projectDocumentRepository);
    }

    /**
     * 保存评估数据（支持创建和更新）。
     */
    @Transactional
    public void saveEvaluation(Long tenderId, EvaluationBasicDTO evaluationBasic,
                               List<Map<String, Object>> evaluationCustomerInfos,
                               EvaluationRecommendationDTO evaluationRecommendation) {
        TenderEvaluation evalEntity = tenderEvaluationRepository.findByTenderId(tenderId)
                .orElseGet(() -> {
                    TenderEvaluation ne = new TenderEvaluation();
                    ne.setTenderId(tenderId);
                    ne.setEvaluationStatus(TenderEvaluation.EvaluationStatus.DRAFT);
                    ne.setReviewStatus(TenderEvaluation.ReviewStatus.PENDING);
                    ne.setEvaluationRound(1);
                    return ne;
                });

        if (evaluationBasic != null) {
            applyEvaluationBasic(evalEntity, evaluationBasic);
        }

        if (evaluationCustomerInfos != null) {
            applyCustomerInfos(evalEntity, evaluationCustomerInfos);
        }

        if (evaluationRecommendation != null) {
            applyRecommendation(evalEntity, evaluationRecommendation);
        }

        tenderEvaluationRepository.save(evalEntity);

        // CO-262: 将 CRM 推送的 GAP 附件引用（外部 URL）持久化到 project_documents 表，
        // 与 TenderEvaluationSubmissionService 保持一致的替换语义。
        gapFilesSync.applyGapFiles(tenderId, evaluationBasic);
    }

    private void applyEvaluationBasic(TenderEvaluation evalEntity, EvaluationBasicDTO b) {
        TenderEvaluationBasic basic = evalEntity.getBasic();
        if (basic == null) {
            basic = new TenderEvaluationBasic();
            basic.setEvaluation(evalEntity);
        }
        if (b.plannedShortlistedCount() != null) basic.setPlannedShortlistedCount(b.plannedShortlistedCount());
        if (b.mroOfficeFlowAmount() != null) basic.setMroOfficeFlowAmount(b.mroOfficeFlowAmount());
        if (b.unfavorableItems() != null) basic.setUnfavorableItems(b.unfavorableItems());
        if (b.riskAssessment() != null) basic.setRiskAssessment(b.riskAssessment());
        if (b.contingencyPlan() != null) basic.setContingencyPlan(b.contingencyPlan());
        if (b.processKnowledge() != null) basic.setProcessKnowledge(b.processKnowledge());
        if (b.supportNotes() != null) basic.setSupportNotes(b.supportNotes());
        // 2026-08-17 需求 3：GAP 文本字段停写（DB 列保留）；GAP 附件回填链路 CO-262 不变
        if (b.customerRevenue() != null) basic.setCustomerRevenue(b.customerRevenue());
        evalEntity.setBasic(basic);
    }

    private void applyCustomerInfos(TenderEvaluation evalEntity, List<Map<String, Object>> evaluationCustomerInfos) {
        if (evalEntity.getCustomerInfos() == null) {
            evalEntity.setCustomerInfos(new ArrayList<>());
        }
        if (!evalEntity.getCustomerInfos().isEmpty()) {
            evalEntity.getCustomerInfos().clear();
            tenderEvaluationRepository.saveAndFlush(evalEntity);
        }

        boolean isEavFormat = !evaluationCustomerInfos.isEmpty()
                && evaluationCustomerInfos.get(0).containsKey("infoKey")
                && evaluationCustomerInfos.get(0).containsKey("value");

        if (isEavFormat) {
            applyEavFormat(evalEntity, evaluationCustomerInfos);
        } else {
            applyFlatFormat(evalEntity, evaluationCustomerInfos);
        }
    }

    private void applyEavFormat(TenderEvaluation evalEntity, List<Map<String, Object>> evaluationCustomerInfos) {
        int roleIndex = 1;
        for (Map<String, Object> row : evaluationCustomerInfos) {
            String roleKey = (String) row.get("roleKey");
            if (roleKey == null || roleKey.isBlank()) {
                roleKey = "EXTERNAL_ROLE_" + roleIndex;
            }
            roleIndex++;
            String infoKey = mapper.normalizeCustomerInfoKey((String) row.get("infoKey"));
            Object value = row.get("value");
            if (infoKey == null || infoKey.isBlank() || value == null) continue;

            TenderEvaluationCustomerInfo entity = new TenderEvaluationCustomerInfo();
            entity.setEvaluation(evalEntity);
            entity.setRoleKey(roleKey);
            entity.setInfoKey(infoKey);
            Object valueType = row.get("valueType");
            entity.setCellValue(mapper.normalizeCustomerInfoValue(infoKey, value));
            entity.setValueType(mapper.parseCustomerInfoValueType(infoKey, valueType == null ? null : valueType.toString()));
            evalEntity.getCustomerInfos().add(entity);
        }
    }

    private void applyFlatFormat(TenderEvaluation evalEntity, List<Map<String, Object>> evaluationCustomerInfos) {
        int roleIndex = 1;
        for (Map<String, Object> roleData : evaluationCustomerInfos) {
            String roleKey = (String) roleData.get("roleKey");
            if (roleKey == null || roleKey.isBlank()) {
                roleKey = "EXTERNAL_ROLE_" + roleIndex;
            }
            roleIndex++;
            for (Map.Entry<String, Object> entry : roleData.entrySet()) {
                if ("roleKey".equals(entry.getKey()) || entry.getValue() == null) continue;
                String infoKey = mapper.normalizeCustomerInfoKey(entry.getKey());
                TenderEvaluationCustomerInfo row = new TenderEvaluationCustomerInfo();
                row.setEvaluation(evalEntity);
                row.setRoleKey(roleKey);
                row.setInfoKey(infoKey);
                row.setCellValue(mapper.normalizeCustomerInfoValue(infoKey, entry.getValue()));
                row.setValueType(mapper.expectedCustomerInfoValueType(infoKey));
                evalEntity.getCustomerInfos().add(row);
            }
        }
    }

    private void applyRecommendation(TenderEvaluation evalEntity, EvaluationRecommendationDTO r) {
        TenderEvaluationRecommendation rec = evalEntity.getRecommendation();
        if (rec == null) {
            rec = new TenderEvaluationRecommendation();
            rec.setEvaluation(evalEntity);
            rec.setEvaluationId(evalEntity.getId());
        }
        if (r.shouldBid() != null) rec.setShouldBid(r.shouldBid());
        if (r.reason() != null) rec.setReason(r.reason());
        evalEntity.setRecommendation(rec);
    }
}
