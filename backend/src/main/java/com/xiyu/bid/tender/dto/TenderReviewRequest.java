package com.xiyu.bid.tender.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 标讯审核请求 DTO
 * 投标部管理员审核时使用
 */
public record TenderReviewRequest(

    /**
     * 是否通过审核（true=投标，false=弃标）
     */
    @NotNull(message = "审核结果不能为空")
    Boolean approved,

    /**
     * 弃标原因（approved=false时必填）
     */
    @Size(max = 1000, message = "弃标原因不能超过1000字符")
    String abandonmentReason,

    /**
     * 审核意见
     */
    @Size(max = 500, message = "审核意见不能超过500字符")
    String reviewComment
) {}
