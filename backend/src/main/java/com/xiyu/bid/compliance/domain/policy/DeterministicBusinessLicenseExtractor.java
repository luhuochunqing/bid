package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件营业执照/主体资格要求纯核心确定性提取器（FP-Java纯函数，无Spring/无IO/无异常）.
 */
public final class DeterministicBusinessLicenseExtractor {

    private static final Pattern LICENSE_CLAUSE_PATTERN = Pattern.compile(
            "(?:营业执照|主体资格|法人资格|法人证书|执业许可)[^。\\n]{0,100}?(?:提供|须具备|应具备|具有|提交|扫描件|复印件)[^。\\n]{0,80}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CERT_TYPE_PATTERN = Pattern.compile(
            "(营业执照|事业单位法人证书|社会团体法人登记证书|民办非企业单位登记证书|执业许可证|合伙企业营业执照)");

    private static final Pattern COPY_REQUIREMENT_PATTERN = Pattern.compile(
            "(原件扫描件|复印件加盖公章|加盖公章的复印件|原件或加盖公章|电子证照|清晰扫描件)");

    private DeterministicBusinessLicenseExtractor() {
    }

    // REUSE-OK: DeterministicDepositExtractor — 营业执照确定性提取入口，业务域独立
    public static BusinessLicenseRequirement extract(final String tenderFullText) {
        if (tenderFullText == null || tenderFullText.isBlank()) {
            return null;
        }

        Matcher clauseMatcher = LICENSE_CLAUSE_PATTERN.matcher(tenderFullText);
        String matchedClause = null;
        if (clauseMatcher.find()) {
            matchedClause = clauseMatcher.group().trim();
        }

        boolean mentionsLicense = tenderFullText.contains("营业执照")
                || tenderFullText.contains("统一社会信用代码")
                || tenderFullText.contains("事业单位法人证书")
                || tenderFullText.contains("主体资格证明");

        if (!mentionsLicense && matchedClause == null) {
            return null;
        }

        List<String> certTypes = new ArrayList<>();
        String scopeText = matchedClause != null ? matchedClause : tenderFullText;
        Matcher certMatcher = CERT_TYPE_PATTERN.matcher(scopeText);
        while (certMatcher.find()) {
            String type = certMatcher.group(1);
            if (!certTypes.contains(type)) {
                certTypes.add(type);
            }
        }
        if (certTypes.isEmpty() && mentionsLicense) {
            certTypes.add("营业执照");
        }

        String copyReq = null;
        Matcher copyMatcher = COPY_REQUIREMENT_PATTERN.matcher(scopeText);
        if (copyMatcher.find()) {
            copyReq = copyMatcher.group(1);
        } else if (tenderFullText.contains("加盖公章")) {
            copyReq = "复印件加盖公章";
        }

        String description = matchedClause != null ? matchedClause : "招标文件要求提供有效营业执照或主体资格证明文件";
        return new BusinessLicenseRequirement(true, certTypes, copyReq, description);
    }
}
