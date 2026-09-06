// Input: LLM 结构化提取或规则提取的前附表关键条款要求
// Output: 前附表结构化契约 record（工期/质保期/有效期/交货地点/付款方式等）
// Pos: compliance/domain/policy/ - 前附表结构化契约（spec 049 阶段 2 契约）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 招标文件投标人须知前附表关键条款要求契约（纯核心 record）.
 */
public record FrontTableRequirement(
        Boolean required,
        String projectName,
        String tenderProjectNo,
        String deliveryPeriod,
        String warrantyPeriod,
        String validityPeriod,
        String deliveryLocation,
        String paymentTerms,
        String qualityStandard,
        Map<String, String> keyClauses,
        String description) {

    public FrontTableRequirement {
        keyClauses = keyClauses == null ? Map.of() : Map.copyOf(keyClauses);
    }

    public static FrontTableRequirement empty() {
        return new FrontTableRequirement(null, null, null, null, null, null, null, null, null, Map.of(), null);
    }

    /**
     * 融合确定性提取与 LLM 提取的前附表要求（硬事实优先）.
     */
    public static FrontTableRequirement fuse(final FrontTableRequirement existing, final FrontTableRequirement incoming) {
        if (incoming == null) {
            return existing;
        }
        if (existing == null) {
            return incoming;
        }
        return new FrontTableRequirement(
                incoming.required() != null ? incoming.required() : existing.required(),
                isNotBlank(incoming.projectName()) ? incoming.projectName() : existing.projectName(),
                isNotBlank(incoming.tenderProjectNo()) ? incoming.tenderProjectNo() : existing.tenderProjectNo(),
                isNotBlank(incoming.deliveryPeriod()) ? incoming.deliveryPeriod() : existing.deliveryPeriod(),
                isNotBlank(incoming.warrantyPeriod()) ? incoming.warrantyPeriod() : existing.warrantyPeriod(),
                isNotBlank(incoming.validityPeriod()) ? incoming.validityPeriod() : existing.validityPeriod(),
                isNotBlank(incoming.deliveryLocation()) ? incoming.deliveryLocation() : existing.deliveryLocation(),
                isNotBlank(incoming.paymentTerms()) ? incoming.paymentTerms() : existing.paymentTerms(),
                isNotBlank(incoming.qualityStandard()) ? incoming.qualityStandard() : existing.qualityStandard(),
                fuseKeyClauses(existing.keyClauses(), incoming.keyClauses()),
                isNotBlank(incoming.description()) ? incoming.description() : existing.description());
    }

    private static boolean isNotBlank(final String s) {
        return s != null && !s.isBlank();
    }

    private static Map<String, String> fuseKeyClauses(final Map<String, String> m1, final Map<String, String> m2) {
        if ((m1 == null || m1.isEmpty()) && (m2 == null || m2.isEmpty())) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        if (m1 != null) {
            merged.putAll(m1);
        }
        if (m2 != null) {
            merged.putAll(m2);
        }
        return Map.copyOf(merged);
    }
}
