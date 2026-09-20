package com.xiyu.bid.scoreparse.dto;

import java.util.List;

/**
 * 知识库五类匹配通用响应骨架（spec 041 contracts/knowledge-match-api.md）。
 *
 * @param tier        FULL / PARTIAL / NONE
 * @param matchRatio  0-100 整数
 * @param matched     命中记录摘要（各类型独立 record：CertMatchedItem 等）
 * @param matchDetail 命中说明（含降级/过期标注）
 */
public record KnowledgeMatchResult(
        String tier,
        int matchRatio,
        List<?> matched,
        String matchDetail,
        List<RecordProof> proofs
) {

    public KnowledgeMatchResult(String tier, int matchRatio, List<?> matched, String matchDetail) {
        this(tier, matchRatio, matched, matchDetail, buildProofs(matched));
    }

    public KnowledgeMatchResult {
        matched = matched == null ? List.of() : List.copyOf(matched);
        proofs = proofs == null ? List.of() : List.copyOf(proofs);
    }

    public static KnowledgeMatchResult empty(String matchDetail) {
        return new KnowledgeMatchResult("NONE", 0, List.of(), matchDetail, List.of());
    }

    private static List<RecordProof> buildProofs(List<?> rows) {
        if (rows == null) return List.of();
        return rows.stream().map(row -> {
            if (row instanceof CertMatchedItem cert) {
                return new RecordProof("CERT", String.valueOf(cert.id()), cert.name(), List.of(
                        condition("证书名称", cert.name()), condition("证书等级", cert.level()),
                        condition("有效期", cert.expiryDate()), new ConditionProof("有效状态",
                        cert.expired() ? "FAIL" : "PASS", cert.expired() ? "证书已过期" : "证书在有效期内")));
            }
            if (row instanceof ProjectMatchedItem project) {
                return new RecordProof("PROJECT", String.valueOf(project.id()), project.contractName(), List.of(
                        condition("合同名称", project.contractName()), condition("业务类别", project.projectType()),
                        condition("签约日期", project.signingDate()), condition("合同金额", project.contractAmount())));
            }
            return new RecordProof(row == null ? "UNKNOWN" : row.getClass().getSimpleName(), "", String.valueOf(row), List.of());
        }).toList();
    }

    private static ConditionProof condition(String name, Object value) {
        return new ConditionProof(name, value == null || String.valueOf(value).isBlank() ? "MISSING_EVIDENCE" : "PASS",
                value == null ? "知识记录未提供该字段" : String.valueOf(value));
    }

    public record RecordProof(String recordType, String recordId, String recordName,
                              List<ConditionProof> conditions) {}
    public record ConditionProof(String condition, String status, String evidence) {}
}
