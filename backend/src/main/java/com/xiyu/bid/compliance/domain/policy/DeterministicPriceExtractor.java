// Input: 招标文件正文
// Output: 确定性报价与最高限价结构化契约（PriceRequirement）
// Pos: compliance/domain/policy/ - 确定性报价与限价提取器（FP-Java 纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯核心确定性报价要求与最高限价提取器.
 */
public final class DeterministicPriceExtractor {

    private static final String[] ANCHORS = {
            "最高投标限价", "招标控制价", "最高限价", "最高限额", "采购预算", "预算金额", "控制价", "预算价"
    };

    private static final Pattern TEN_THOUSAND_PATTERN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]{1,4})?)\\s*万(?:元)?"
    );

    private static final Pattern NUMERIC_PRICE_PATTERN = Pattern.compile(
            "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*元"
    );

    private static final Pattern CHINESE_PRICE_PATTERN = Pattern.compile(
            "(?:人民币)?([零壹贰叁肆伍陆柒捌玖拾佰仟万亿元角分整正]+元(?:整|正)?)"
    );

    private DeterministicPriceExtractor() {
    }

    public static PriceRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        BigDecimal maxPrice = null;
        String rawText = null;

        for (String anchor : ANCHORS) {
            int idx = tenderText.indexOf(anchor);
            if (idx < 0) {
                continue;
            }
            int start = idx;
            int end = Math.min(tenderText.length(), idx + 120);
            String snippet = tenderText.substring(start, end);

            // 1. 优先尝试提取万元单位
            Matcher mWan = TEN_THOUSAND_PATTERN.matcher(snippet);
            if (mWan.find()) {
                String numStr = mWan.group(1);
                maxPrice = new BigDecimal(numStr).multiply(new BigDecimal("10000"));
                rawText = snippet.substring(0, Math.min(snippet.length(), mWan.end() + 10)).trim();
                break;
            }

            // 2. 尝试提取纯数字（以元为单位）
            Matcher mNum = NUMERIC_PRICE_PATTERN.matcher(snippet);
            if (mNum.find()) {
                String numStr = mNum.group(1).replace(",", "");
                maxPrice = new BigDecimal(numStr);
                rawText = snippet.substring(0, Math.min(snippet.length(), mNum.end() + 10)).trim();
                break;
            }

            // 3. 尝试提取中文大写
            Matcher mCn = CHINESE_PRICE_PATTERN.matcher(snippet);
            if (mCn.find()) {
                String cnStr = mCn.group(1);
                BigDecimal parsed = ChineseAmountConverter.convertChineseToNumber(cnStr);
                if (parsed.compareTo(BigDecimal.ZERO) > 0) {
                    maxPrice = parsed;
                    rawText = snippet.substring(0, Math.min(snippet.length(), mCn.end() + 10)).trim();
                    break;
                }
            }
        }

        String pricingMethod = extractPricingMethod(tenderText);
        boolean requireItemized = tenderText.contains("分项报价") || tenderText.contains("报价明细") || tenderText.contains("分项明细");
        boolean requireTable = tenderText.contains("开标一览表") || tenderText.contains("投标一览表") || tenderText.contains("报价一览表");

        String description = rawText != null ? "招标文件规定：" + rawText : "招标文件未明确设定最高限价，按市场行情自主报价";

        return new PriceRequirement(
                true,
                maxPrice,
                rawText,
                pricingMethod,
                requireItemized,
                requireTable,
                description
        );
    }

    private static String extractPricingMethod(final String tenderText) {
        if (tenderText.contains("费率") || tenderText.contains("下浮率") || tenderText.contains("折扣率")) {
            return "费率报价/下浮率报价";
        }
        if (tenderText.contains("单价报价") || tenderText.contains("综合单价")) {
            return "单价报价";
        }
        return "总价报价";
    }
}
