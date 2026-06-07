package com.xiyu.bid.tender.dto;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.entity.TenderEvaluation.BidRecommendation;
import com.xiyu.bid.tender.entity.TenderEvaluation.EvaluationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯项目评估详情 DTO（V130 三段式 + V1026 字段重构）。
 * <p>纯值载体，不在此类承载任何转换逻辑（由 service / mapper 负责）。
 * <p>V130 新增：三段式数据（evaluationBasic、evaluationCustomerInfos、evaluationRecommendation）
 * 以及审核跟踪字段（requiresReview、lastReviewedBy、lastReviewedAt、evaluationRound）。
 */
public record TenderEvaluationDTO(

    Long tenderId,
    String tenderTitle,
    Tender.Status tenderStatus,

    // ---------- 评估表状态 + 建议 ----------
    EvaluationStatus evaluationStatus,
    BidRecommendation bidRecommendation,
    LocalDateTime submittedAt,

    // ---------- 审核 / 评估人 元数据 ----------
    Long evaluatorId,
    String evaluatorName,
    LocalDateTime evaluatedAt,

    // ---------- 实例级权限（当前调用方相对该标讯的判定） ----------
    boolean canFillEvaluation,
    boolean canDecideBid,

    // ---------- V130 三段式数据 ----------
    /** 基础信息段（9 字段，V1026 重构）。 */
    EvaluationBasicDTO evaluationBasic,

    /** 客户信息段 EAV 行列表。 */
    List<EvaluationCustomerInfoDTO> evaluationCustomerInfos,

    /** 投标负责人建议段。 */
    EvaluationRecommendationDTO evaluationRecommendation,

    // ---------- V130 审核跟踪字段 ----------
    /** 是否需要重新审核。 */
    Boolean requiresReview,

    /** 最后审核人 ID。 */
    String lastReviewedBy,

    /** 最后审核时间。 */
    LocalDateTime lastReviewedAt,

    /** 评估轮次。 */
    Integer evaluationRound
) {}
