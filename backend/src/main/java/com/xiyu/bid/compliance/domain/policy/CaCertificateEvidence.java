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
        if (relatedPlatforms.equalsIgnoreCase(platform)) {
            return true;
        }
        String cleanReq = normalizePlatform(platform);
        for (String p : relatedPlatforms.split("[,，、;；]")) {
            String rawP = p.trim();
            if (rawP.isEmpty()) {
                continue;
            }
            if (rawP.equalsIgnoreCase(platform)) {
                return true;
            }
            String cleanP = normalizePlatform(rawP);
            if (cleanP.isEmpty() || cleanReq.isEmpty()) {
                continue;
            }
            if (cleanP.equals(cleanReq) || cleanP.contains(cleanReq) || cleanReq.contains(cleanP)) {
                return true;
            }
            if (isPlatformAliasMatch(cleanReq, cleanP) || isPlatformAliasMatch(platform, rawP)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizePlatform(final String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String s = input.replaceAll("[\\s()（）\\[\\]【】_—\\-]+", "").toLowerCase();
        s = s.replaceAll("(?:公共资源交易中心|公共资源交易平台|公共资源交易服务平台|电子招投标交易平台|招投标交易平台|电子商务平台|供应链统一服务平台|公共资源交易|电子招投标|电子商务|采购平台|服务平台|交易中心|交易平台|数字证书|数字认证|电子签章|电子印章|电子标|采购网|系统|平台|中心|企业|认证|证书|锁|ca)", "");
        if (s.length() > 2 && (s.endsWith("省") || s.endsWith("市"))) {
            s = s.substring(0, s.length() - 1);
        }
        return s.trim();
    }

    private static boolean isPlatformAliasMatch(final String a, final String b) {
        boolean aZhengcai = a.contains("政采") || a.contains("政府采购");
        boolean bZhengcai = b.contains("政采") || b.contains("政府采购");
        if (aZhengcai && bZhengcai) {
            String[] regions = {"北京", "天津", "上海", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西", "甘肃", "青海", "内蒙古", "广西", "西藏", "宁夏", "新疆"};
            String aReg = null;
            String bReg = null;
            for (String reg : regions) {
                if (a.contains(reg)) aReg = reg;
                if (b.contains(reg)) bReg = reg;
            }
            if (aReg != null && bReg != null && !aReg.equals(bReg)) {
                return false;
            }
            return true;
        }
        return (a.contains("国网") && b.contains("国家电网"))
                || (b.contains("国网") && a.contains("国家电网"))
                || (a.contains("南网") && b.contains("南方电网"))
                || (b.contains("南网") && a.contains("南方电网"));
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
