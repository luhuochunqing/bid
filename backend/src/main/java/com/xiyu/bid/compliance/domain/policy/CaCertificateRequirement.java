// Input: 招标电子标 CA 数字证书/签章结构化参数
// Output: 电子标 CA 要求及原文页码证据，确定性未知平台不回填模型猜测
// Pos: compliance/domain/policy/ - 招标文件 CA 证书结构化契约（FP-Java 不可变）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.List;

/**
 * 招标文件对电子标 CA 数字证书 / 电子签章的结构化要求契约（纯领域模型，FP-Java不可变Record）.
 */
public record CaCertificateRequirement(
        boolean required,
        String platformName,
        boolean requireCertificateCopy,
        boolean requireElectronicSeal,
        String description,
        List<QualityEvidence> platformEvidences
) {

    public CaCertificateRequirement {
        platformName = CaPlatformNamePolicy.clean(platformName);
        platformEvidences = platformEvidences == null ? List.of() : List.copyOf(platformEvidences);
    }

    public CaCertificateRequirement(boolean required, String platformName, boolean requireCertificateCopy,
                                    boolean requireElectronicSeal, String description) {
        this(required, platformName, requireCertificateCopy, requireElectronicSeal, description, List.of());
    }

    public static CaCertificateRequirement requiredStandard(final String platform, final String desc) {
        return new CaCertificateRequirement(true, platform, true, true, desc);
    }

    public static CaCertificateRequirement notRequired(final String desc) {
        return new CaCertificateRequirement(false, null, false, false, desc);
    }

    public static CaCertificateRequirement empty() {
        return new CaCertificateRequirement(true, null, false, false, null);
    }

    public static CaCertificateRequirement fuse(
            final CaCertificateRequirement primary, final CaCertificateRequirement fallback) {
        if (primary == null) {
            return fallback != null ? fallback : empty();
        }
        if (fallback == null) {
            return primary;
        }
        boolean req = primary.required() || fallback.required();
        String platform = primary.platformName() != null ? primary.platformName() : fallback.platformName();
        boolean copy = primary.requireCertificateCopy() || fallback.requireCertificateCopy();
        boolean seal = primary.requireElectronicSeal() || fallback.requireElectronicSeal();
        String desc = primary.description() != null ? primary.description() : fallback.description();
        return new CaCertificateRequirement(req, platform, copy, seal, desc,
                !primary.platformEvidences().isEmpty() ? primary.platformEvidences() : fallback.platformEvidences());
    }

    /**
     * 双轨交叉融合：确定性规则从原文正则提取的硬事实优先于大模型生成内容.
     */
    public static CaCertificateRequirement fuseWithDeterministic(
            final CaCertificateRequirement primary, final CaCertificateRequirement deterministic) {
        if (primary == null) {
            return deterministic;
        }
        return primary.fuseWithDeterministic(deterministic);
    }

    public CaCertificateRequirement fuseWithDeterministic(final CaCertificateRequirement deterministic) {
        if (deterministic == null) {
            return this;
        }
        String mergedPlatform = deterministic.platformName();
        boolean mergedCopy = deterministic.requireCertificateCopy() || this.requireCertificateCopy();
        boolean mergedSeal = deterministic.requireElectronicSeal() || this.requireElectronicSeal();
        String mergedDesc = deterministic.description() != null
                ? deterministic.description()
                : this.description();
        return new CaCertificateRequirement(
                deterministic.required(),
                mergedPlatform,
                mergedCopy,
                mergedSeal,
                mergedDesc,
                deterministic.platformEvidences()
        );
    }
}
