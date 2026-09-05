package com.xiyu.bid.compliance.domain.policy;

import java.util.List;

/**
 * 招标文件对营业执照/主体资格证明的结构化要求契约（纯领域模型，FP-Java不可变Record）.
 */
public record BusinessLicenseRequirement(
        boolean required,
        List<String> allowCertificates,
        String requireOriginalOrCopy,
        String description
) {

    public BusinessLicenseRequirement {
        if (allowCertificates == null) {
            allowCertificates = List.of();
        }
    }

    public static BusinessLicenseRequirement standard() {
        return new BusinessLicenseRequirement(true, List.of("营业执照", "事业单位法人证书"), "加盖公章", null);
    }

    public static BusinessLicenseRequirement empty() {
        return new BusinessLicenseRequirement(true, List.of(), null, null);
    }

    public static BusinessLicenseRequirement fuse(
            final BusinessLicenseRequirement primary, final BusinessLicenseRequirement fallback) {
        if (primary == null) {
            return fallback != null ? fallback : empty();
        }
        if (fallback == null) {
            return primary;
        }
        boolean req = primary.required() || fallback.required();
        List<String> certs = primary.allowCertificates().isEmpty() ? fallback.allowCertificates() : primary.allowCertificates();
        String copyReq = primary.requireOriginalOrCopy() != null ? primary.requireOriginalOrCopy() : fallback.requireOriginalOrCopy();
        String desc = primary.description() != null ? primary.description() : fallback.description();
        return new BusinessLicenseRequirement(req, certs, copyReq, desc);
    }

    /**
     * 双轨交叉融合：确定性规则从原文正则提取的硬事实优先于大模型生成内容.
     */
    public static BusinessLicenseRequirement fuseWithDeterministic(
            final BusinessLicenseRequirement llmReq, final BusinessLicenseRequirement detReq) {
        return fuse(detReq, llmReq);
    }
}
