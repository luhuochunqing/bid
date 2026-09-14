// Input: CA 证书基础字段（ID/平台/类型/印章/到期日/借用状态/保管人等）
// Output: 电子标 CA 证书纯领域证据模型（FP-Java 不可变 Record，支持平台匹配、过期、借用状态判定与 KnowledgeMatch 转换）
// Pos: compliance/domain/policy/ - CA 证书资源证据纯核心模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.time.LocalDate;

/**
 * 电子标 CA 数字证书资源证据纯核心模型 (FP-Java 不可变 Record).
 */
public record CaCertificateEvidence(
        Long id,
        String caType,
        String sealType,
        String issuer,
        LocalDate expiryDate,
        String relatedPlatforms,
        String borrowStatus,
        String status,
        String custodianName,
        String holderName
) {

    public boolean isExpired(final LocalDate referenceDate) {
        if ("EXPIRED".equalsIgnoreCase(status)) {
            return true;
        }
        return expiryDate != null && referenceDate != null && expiryDate.isBefore(referenceDate);
    }

    public boolean isBorrowed() {
        return "BORROWED".equalsIgnoreCase(borrowStatus) || "OVERDUE".equalsIgnoreCase(borrowStatus);
    }

    public boolean matchesPlatform(final String platform) {
        if (platform == null || platform.isBlank()) {
            return true;
        }
        if (relatedPlatforms == null || relatedPlatforms.isBlank()) {
            return false;
        }
        String cleanReq = platform.replaceAll("[电子招投标交易平台公共资源交易中心电子商务系统采购网]+", "").trim().toLowerCase();
        for (String p : relatedPlatforms.split("[,，、;；]")) {
            String cleanP = p.trim().toLowerCase();
            if (cleanP.isEmpty()) {
                continue;
            }
            if (cleanP.contains(cleanReq) || (!cleanReq.isEmpty() && cleanReq.contains(cleanP)) || p.equalsIgnoreCase(platform)) {
                return true;
            }
        }
        return false;
    }

    public KnowledgeMatch toKnowledgeMatch(final boolean matched) {
        String typeLabel = "ENTITY_CA".equalsIgnoreCase(caType) ? "实体CA" : "电子CA";
        String statusLabel = "EXPIRED".equalsIgnoreCase(status) ? "已过期"
                : ("EXPIRING".equalsIgnoreCase(status) ? "即将到期" : "有效");
        String borrowLabel = "BORROWED".equalsIgnoreCase(borrowStatus) ? "已借出"
                : ("OVERDUE".equalsIgnoreCase(borrowStatus) ? "已逾期" : "在库");
        StringBuilder sb = new StringBuilder();
        sb.append("CA证书: ").append(relatedPlatforms != null && !relatedPlatforms.isBlank() ? relatedPlatforms : "通用");
        sb.append("（").append(typeLabel);
        if (sealType != null && !sealType.isBlank()) {
            sb.append("/").append(sealType);
        }
        sb.append("，").append(statusLabel);
        if (expiryDate != null) {
            sb.append("，有效期至 ").append(expiryDate);
        }
        sb.append("，").append(borrowLabel);
        if (custodianName != null && !custodianName.isBlank()) {
            sb.append("，保管人: ").append(custodianName);
        }
        if (holderName != null && !holderName.isBlank()) {
            sb.append("，持有人: ").append(holderName);
        }
        sb.append("）");
        return new KnowledgeMatch(
                KnowledgeMatch.SOURCE_CA_CERTIFICATE,
                id != null ? String.valueOf(id) : "0",
                sb.toString(),
                matched);
    }
}
