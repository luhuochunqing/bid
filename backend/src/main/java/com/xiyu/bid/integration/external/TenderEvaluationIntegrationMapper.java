package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部标讯集成评估数据 Mapper。
 * 负责评估相关的 DTO 转换。
 */
@Component
@RequiredArgsConstructor
public class TenderEvaluationIntegrationMapper {

    private final TenderEvaluationRepository tenderEvaluationRepository;
    private final TenderEvaluationSubmissionMapper submissionMapper;

    /**
     * 查询评估表并构建 DTO（customerInfos 展平为按角色聚合的格式）。
     */
    Object buildEvaluationDTO(Long tenderId, Tender tender) {
        return tenderEvaluationRepository.findByTenderId(tenderId)
                .map(e -> {
                    TenderEvaluationDTO dto = submissionMapper.toDTO(e, tender, false, false);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("evaluationStatus", dto.evaluationStatus() != null ? dto.evaluationStatus().name() : null);
                    result.put("bidRecommendation", dto.bidRecommendation() != null ? dto.bidRecommendation().name() : null);
                    result.put("submittedAt", dto.submittedAt());
                    result.put("evaluatorId", dto.evaluatorId());
                    result.put("evaluatorName", dto.evaluatorName());
                    result.put("evaluatedAt", dto.evaluatedAt());
                    result.put("reviewStatus", e.getReviewStatus() != null ? e.getReviewStatus().name() : null);
                    result.put("reviewerName", e.getReviewerName());
                    result.put("reviewedAt", e.getReviewedAt());
                    result.put("reviewComment", e.getReviewComment());
                    result.put("evaluationRound", dto.evaluationRound());
                    result.put("canFillEvaluation", dto.canFillEvaluation());
                    result.put("canDecideBid", dto.canDecideBid());
                    result.put("requiresReview", dto.requiresReview());
                    result.put("lastReviewedBy", dto.lastReviewedBy());
                    result.put("lastReviewedAt", dto.lastReviewedAt());
                    result.put("evaluationBasic", dto.evaluationBasic());
                    result.put("evaluationCustomerInfos", flattenCustomerInfos(dto.evaluationCustomerInfos()));
                    result.put("evaluationRecommendation", dto.evaluationRecommendation());
                    return result;
                })
                .orElse(null);
    }

    /**
     * 将 EAV 格式的 customerInfos 展平为按角色聚合的数组（排除已删除的三列）。
     */
    List<Map<String, Object>> flattenCustomerInfos(List<EvaluationCustomerInfoDTO> eavRows) {
        if (eavRows == null || eavRows.isEmpty()) return null;
        Map<String, Map<String, Object>> byRole = new LinkedHashMap<>();
        for (EvaluationCustomerInfoDTO row : eavRows) {
            if ("ROLE_NAME".equals(row.infoKey())
                    || "KEY_TARGET".equals(row.infoKey())
                    || "HIGH_LEVEL_EXCHANGE".equals(row.infoKey())) {
                continue;
            }
            byRole.computeIfAbsent(row.roleKey(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("roleKey", k);
                return m;
            });
            byRole.get(row.roleKey()).put(row.infoKey(), row.value());
        }
        return new ArrayList<>(byRole.values());
    }

    /**
     * 将 CRM 推送的字段名标准化为前端矩阵使用的 infoKey。
     */
    String normalizeCustomerInfoKey(String infoKey) {
        if (infoKey == null) return null;
        return switch (infoKey) {
            case "CONTACT" -> "CONTACT_INFO";
            case "EVALUATION_BASIS" -> "INFO_TENDENCY_BASIS";
            default -> infoKey;
        };
    }

    /**
     * 解析客户信息值类型。
     */
    TenderEvaluationCustomerInfo.ValueType parseCustomerInfoValueType(String valueType) {
        if (valueType == null || valueType.isBlank()) return TenderEvaluationCustomerInfo.ValueType.TEXT;
        try {
            return TenderEvaluationCustomerInfo.ValueType.valueOf(valueType);
        } catch (IllegalArgumentException ex) {
            return TenderEvaluationCustomerInfo.ValueType.TEXT;
        }
    }
}
