// Input: TenderEvaluation 实体和 DTO 对象
// Output: DTO ↔ Entity 双向映射
// Pos: Service/转换层 - 不依赖 Spring / JPA 运行时状态
// 维护声明: 仅承载映射逻辑；不携带业务规则或编排。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.entity.TenderEvaluationRecommendation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标讯项目评估表 DTO ↔ Entity 映射（V130 三段式 + V1026 字段重构）。
 * <p>包级可见，被 {@link TenderEvaluationSubmissionService} 使用。
 * 无状态，可安全复用。
 */
public class TenderEvaluationSubmissionMapper {

    /** 创建新的草稿实体。 */
    TenderEvaluation newEntity(Long tenderId, User evaluator) {
        return TenderEvaluation.builder()
                .tenderId(tenderId)
                .evaluatorId(evaluator.getId())
                .evaluatorName(evaluator.getUsername())
                .evaluationStatus(TenderEvaluation.EvaluationStatus.DRAFT)
                .reviewStatus(TenderEvaluation.ReviewStatus.PENDING)
                .requiresReview(false)
                .evaluationRound(1)
                .customerInfos(new ArrayList<>())
                .build();
    }

    /** 将请求数据应用到实体（含三段式映射）。 */
    void applyRequest(TenderEvaluation entity, TenderEvaluationSubmitRequest req) {
        entity.setBidRecommendation(req.bidRecommendation());

        applyBasic(entity, req.evaluationBasic());
        // CO-266: 客户信息段的 clear() + addAll() 由 Service 层负责，
        // 避免 INSERT-before-DELETE flush 顺序撞 uk_eval_role_info 唯一约束。
        applyRecommendation(entity, req.evaluationRecommendation());
    }

    private void applyBasic(TenderEvaluation entity, EvaluationBasicDTO basic) {
        if (basic == null) {
            entity.setBasic(null);
            return;
        }
        TenderEvaluationBasic b = entity.getBasic();
        if (b == null) {
            b = TenderEvaluationBasic.builder().evaluation(entity).build();
        }
        b.setPlannedShortlistedCount(basic.plannedShortlistedCount());
        b.setMroOfficeFlowAmount(basic.mroOfficeFlowAmount());
        b.setUnfavorableItems(basic.unfavorableItems());
        b.setRiskAssessment(basic.riskAssessment());
        b.setContingencyPlan(basic.contingencyPlan());
        b.setProcessKnowledge(basic.processKnowledge());
        b.setSupportNotes(basic.supportNotes());
        b.setProjectPlanGap(basic.projectPlanGap());
        b.setCustomerRevenue(basic.customerRevenue());
        entity.setBasic(b);
    }

    /**
     * 构建客户信息段新行列表（不修改 entity.customerInfos）。
     * <p>CO-266 修复：原实现 {@code clear() + addAll()} 在同一事务内会触发
     * Hibernate INSERT-before-DELETE flush 顺序，撞 uk_eval_role_info 唯一约束。
     * 现改为只构建新行列表，由调用方（Service 层）负责先 {@code clear() + saveAndFlush()}
     * 确保 DELETE SQL 落库，再 {@code addAll(newRows)}。
     */
    List<TenderEvaluationCustomerInfo> buildCustomerInfoRows(TenderEvaluation entity, List<EvaluationCustomerInfoDTO> infos) {
        if (infos == null) {
            return Collections.emptyList();
        }
        return infos.stream()
                .map(dto -> {
                    TenderEvaluationCustomerInfo.ValueType vt = parseValueType(dto.valueType());
                    return TenderEvaluationCustomerInfo.builder()
                            .evaluation(entity)
                            .roleKey(dto.roleKey())
                            .infoKey(dto.infoKey())
                            .cellValue(dto.value())
                            .valueType(vt)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static TenderEvaluationCustomerInfo.ValueType parseValueType(String vtStr) {
        if (vtStr == null) {
            return TenderEvaluationCustomerInfo.ValueType.TEXT;
        }
        return switch (vtStr) {
            case "SWITCH" -> TenderEvaluationCustomerInfo.ValueType.SWITCH;
            case "ENUM14" -> TenderEvaluationCustomerInfo.ValueType.ENUM14;
            case "ENUM7" -> TenderEvaluationCustomerInfo.ValueType.ENUM7;
            case "DROPDOWN6" -> TenderEvaluationCustomerInfo.ValueType.DROPDOWN6;
            case "DROPDOWN" -> TenderEvaluationCustomerInfo.ValueType.DROPDOWN;
            default -> TenderEvaluationCustomerInfo.ValueType.TEXT;
        };
    }

    private void applyRecommendation(TenderEvaluation entity, EvaluationRecommendationDTO rec) {
        if (rec == null) {
            entity.setRecommendation(null);
            return;
        }
        TenderEvaluationRecommendation r = entity.getRecommendation();
        if (r == null) {
            r = TenderEvaluationRecommendation.builder()
                    .evaluation(entity)
                    .build();
        }
        r.setShouldBid(rec.shouldBid());
        r.setReason(rec.reason());
        entity.setRecommendation(r);
    }

    /** 将实体转换为完整 DTO。 */
    public TenderEvaluationDTO toDTO(TenderEvaluation e, Tender tender, boolean canFill, boolean canDecide) {
        if (e == null) {
            return null;
        }
        EvaluationBasicDTO basicDTO = null;
        if (e.getBasic() != null) {
            TenderEvaluationBasic b = e.getBasic();
            basicDTO = new EvaluationBasicDTO(
                    b.getPlannedShortlistedCount(),
                    b.getMroOfficeFlowAmount(),
                    b.getUnfavorableItems(),
                    b.getRiskAssessment(),
                    b.getContingencyPlan(),
                    b.getProcessKnowledge(),
                    b.getSupportNotes(),
                    b.getProjectPlanGap(),
                    b.getCustomerRevenue()
            );
        }

        List<EvaluationCustomerInfoDTO> customerInfoDTOs = null;
        if (e.getCustomerInfos() != null) {
            customerInfoDTOs = e.getCustomerInfos().stream()
                    .map(ci -> new EvaluationCustomerInfoDTO(
                            ci.getRoleKey(),
                            ci.getInfoKey(),
                            ci.getCellValue(),
                            ci.getValueType().name()))
                    .collect(Collectors.toList());
        }

        EvaluationRecommendationDTO recDTO = null;
        if (e.getRecommendation() != null) {
            recDTO = new EvaluationRecommendationDTO(
                    e.getRecommendation().getShouldBid(),
                    e.getRecommendation().getReason()
            );
        }

        return new TenderEvaluationDTO(
                e.getTenderId(),
                tender != null ? tender.getTitle() : null,
                tender != null ? tender.getStatus() : null,
                e.getEvaluationStatus(),
                e.getBidRecommendation(),
                e.getSubmittedAt(),
                e.getEvaluatorId(),
                e.getEvaluatorName(),
                e.getEvaluatedAt(),
                canFill,
                canDecide,
                basicDTO,
                customerInfoDTOs,
                recDTO,
                e.isRequiresReview(),
                e.getLastReviewedBy() != null ? String.valueOf(e.getLastReviewedBy()) : null,
                e.getLastReviewedAt(),
                e.getEvaluationRound()
        );
    }

    /** 创建未持久化的空白 DRAFT DTO。 */
    TenderEvaluationDTO emptyDraftDTO(Tender tender, User evaluator, boolean canFill, boolean canDecide) {
        return new TenderEvaluationDTO(
                tender.getId(),
                tender.getTitle(),
                tender.getStatus(),
                TenderEvaluation.EvaluationStatus.DRAFT,
                null,
                null,
                evaluator.getId(),
                evaluator.getUsername(),
                null,
                canFill,
                canDecide,
                null,
                null,
                null,
                false,
                null,
                null,
                1
        );
    }
}
