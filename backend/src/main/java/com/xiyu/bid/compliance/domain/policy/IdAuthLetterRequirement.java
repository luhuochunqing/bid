package com.xiyu.bid.compliance.domain.policy;

/**
 * 招标文件对法定代表人及委托代理人身份证明/身份证附件的结构化要求契约（纯领域模型，FP-Java不可变Record）.
 */
public record IdAuthLetterRequirement(
        boolean required,
        boolean requireFrontAndBack,
        boolean requireSocialSecurity,
        String requireOriginalOrCopy,
        String description
) {

    public static IdAuthLetterRequirement standard() {
        return new IdAuthLetterRequirement(true, true, false, "复印件加盖公章", null);
    }

    public static IdAuthLetterRequirement empty() {
        return new IdAuthLetterRequirement(true, false, false, null, null);
    }

    public static IdAuthLetterRequirement fuse(
            final IdAuthLetterRequirement primary, final IdAuthLetterRequirement fallback) {
        if (primary == null) {
            return fallback != null ? fallback : empty();
        }
        if (fallback == null) {
            return primary;
        }
        boolean req = primary.required() || fallback.required();
        boolean frontBack = primary.requireFrontAndBack() || fallback.requireFrontAndBack();
        boolean socialSec = primary.requireSocialSecurity() || fallback.requireSocialSecurity();
        String copyReq = primary.requireOriginalOrCopy() != null ? primary.requireOriginalOrCopy() : fallback.requireOriginalOrCopy();
        String desc = primary.description() != null ? primary.description() : fallback.description();
        return new IdAuthLetterRequirement(req, frontBack, socialSec, copyReq, desc);
    }

    /**
     * 双轨交叉融合：确定性规则从原文正则提取的硬事实优先于大模型生成内容.
     */
    public IdAuthLetterRequirement fuseWithDeterministic(final IdAuthLetterRequirement deterministic) {
        if (deterministic == null) {
            return this;
        }
        boolean mergedFrontBack = deterministic.requireFrontAndBack() || this.requireFrontAndBack();
        boolean mergedSocialSec = deterministic.requireSocialSecurity() || this.requireSocialSecurity();
        String mergedCopyReq = deterministic.requireOriginalOrCopy() != null
                ? deterministic.requireOriginalOrCopy()
                : this.requireOriginalOrCopy();
        String mergedDesc = deterministic.description() != null
                ? deterministic.description()
                : this.description();
        return new IdAuthLetterRequirement(
                this.required() || deterministic.required(),
                mergedFrontBack,
                mergedSocialSec,
                mergedCopyReq,
                mergedDesc
        );
    }
}
