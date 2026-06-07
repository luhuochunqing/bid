package com.xiyu.bid.tender.dto;

/**
 * 投标负责人建议段 DTO（V130 三段式）。
 *
 * <p>对应 TenderEvaluationRecommendation 实体，
 * 包含是否投标建议及理由。
 */
public record EvaluationRecommendationDTO(

    /** 是否建议投标。 */
    Boolean shouldBid,

    /** 理由（不建议投标时必填）。 */
    String reason
) {}
