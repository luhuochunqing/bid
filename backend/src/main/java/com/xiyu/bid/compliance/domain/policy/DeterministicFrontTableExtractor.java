// Input: 招标文件 DocumentContent
// Output: 确定性规则提取的前附表关键条款要求 FrontTableRequirement（硬事实兜底）
// Pos: compliance/domain/policy/ - 确定性前附表提取器（纯核心规则）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性前附表关键条款提取器（纯核心规则）.
 */
public final class DeterministicFrontTableExtractor {

    private static final Pattern DELIVERY_PATTERN = Pattern.compile(
            "(?:供货周期|工期|交货期|供货期|服务期限|建设工期)(?:要求)?[：:\\s]+([^;\\n，。]{2,50})");
    private static final Pattern WARRANTY_PATTERN = Pattern.compile(
            "(?:质保期|保修期|质量保证期)(?:要求)?[：:\\s]+([^;\\n，。]{2,50})");
    private static final Pattern VALIDITY_PATTERN = Pattern.compile(
            "(?:投标有效期|承诺有效期)(?:要求)?[：:\\s]+([^;\\n，。]{2,50})");
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?:交货地点|实施地点|服务地点|履约地点)(?:要求)?[：:\\s]+([^;\\n，。]{2,50})");
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(
            "(?:付款方式|支付方式|资金结算|资金支付)(?:要求)?[：:\\s]+([^;\\n。]{2,80})");
    private static final Pattern QUALITY_PATTERN = Pattern.compile(
            "(?:质量标准|质量要求|质量目标)(?:要求)?[：:\\s]+([^;\\n，。]{2,50})");
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile(
            "(?:招标项目名称|项目名称)[：:\\s]+([^;\\n，。]{2,60})");
    private static final Pattern PROJECT_NO_PATTERN = Pattern.compile(
            "(?:招标编号|项目编号)[：:\\s]+([A-Za-z0-9_\\-#]{3,40})");

    private DeterministicFrontTableExtractor() {
    }

    /**
     * 从招标文件提取前附表关键条款要求.
     */
    public static FrontTableRequirement extract(final DocumentContent tenderDoc) {
        if (tenderDoc == null || tenderDoc.fullText() == null || tenderDoc.fullText().isBlank()) {
            return FrontTableRequirement.empty();
        }
        String text = tenderDoc.fullText();
        String delivery = matchFirst(DELIVERY_PATTERN, text);
        String warranty = matchFirst(WARRANTY_PATTERN, text);
        String validity = matchFirst(VALIDITY_PATTERN, text);
        String location = matchFirst(LOCATION_PATTERN, text);
        String payment = matchFirst(PAYMENT_PATTERN, text);
        String quality = matchFirst(QUALITY_PATTERN, text);
        String projectName = matchFirst(PROJECT_NAME_PATTERN, text);
        String projectNo = matchFirst(PROJECT_NO_PATTERN, text);

        boolean hasAny = delivery != null || warranty != null || validity != null
                || location != null || payment != null || quality != null;
        if (!hasAny) {
            return FrontTableRequirement.empty();
        }

        Map<String, String> keyClauses = new LinkedHashMap<>();
        if (delivery != null) {
            keyClauses.put("工期/交货期", delivery);
        }
        if (warranty != null) {
            keyClauses.put("质保期", warranty);
        }
        if (validity != null) {
            keyClauses.put("投标有效期", validity);
        }
        if (location != null) {
            keyClauses.put("交货地点", location);
        }
        if (payment != null) {
            keyClauses.put("付款方式", payment);
        }
        if (quality != null) {
            keyClauses.put("质量标准", quality);
        }

        return new FrontTableRequirement(
                true, projectName, projectNo, delivery, warranty, validity, location, payment, quality,
                keyClauses, "确定性前附表关键条款提取");
    }

    private static String matchFirst(final Pattern pattern, final String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
