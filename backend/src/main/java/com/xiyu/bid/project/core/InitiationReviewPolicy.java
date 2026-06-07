package com.xiyu.bid.project.core;

import com.xiyu.bid.project.dto.InitiationApprovalRequest;
import com.xiyu.bid.project.dto.InitiationRejectionRequest;

/**
 * 立项审核规则（纯规则，无 Spring/JPA）。
 * 产品蓝图 V1.1 §4.3：
 * - 审核通过必须分配投标负责人
 * - 审核驳回必须填写原因
 * - 项目必须处于 PENDING_REVIEW 状态才能审核
 */
public final class InitiationReviewPolicy {

    private InitiationReviewPolicy() {}

    public static Decision validateApproval(InitiationReviewStatus currentStatus, InitiationApprovalRequest req) {
        if (currentStatus != InitiationReviewStatus.PENDING_REVIEW) {
            return new Decision.Deny("项目不在待审核状态: " + currentStatus);
        }
        if (req == null) {
            return new Decision.Deny("审核请求不能为空");
        }
        if (req.getPrimaryLeadUserId() == null) {
            return new Decision.Deny("必须分配投标主负责人");
        }
        return Decision.ALLOW;
    }

    public static Decision validateRejection(InitiationReviewStatus currentStatus, InitiationRejectionRequest req) {
        if (currentStatus != InitiationReviewStatus.PENDING_REVIEW) {
            return new Decision.Deny("项目不在待审核状态: " + currentStatus);
        }
        if (req == null || req.getRejectionReason() == null || req.getRejectionReason().isBlank()) {
            return new Decision.Deny("驳回原因不能为空");
        }
        return Decision.ALLOW;
    }

    public static Decision validateSubmit(InitiationReviewStatus currentStatus) {
        if (currentStatus == InitiationReviewStatus.PENDING_REVIEW) {
            return new Decision.Deny("项目已提交审核，请勿重复提交");
        }
        if (currentStatus == InitiationReviewStatus.APPROVED) {
            return new Decision.Deny("项目已通过审核，不可重新提交");
        }
        return Decision.ALLOW;
    }

    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();
        default boolean allowed() { return this instanceof Allow; }
        record Allow() implements Decision {}
        record Deny(String reason) implements Decision {}
    }
}
