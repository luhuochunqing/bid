// Input: 招标文件解析后的文本内容（String）
// Output: 确定性提取的投标函/报价函格式要求模型（TenderRequirements.BidLetterRequirement）
// Pos: compliance/domain/policy/ - 投标函格式要求确定性提取纯核心策略（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件投标函/报价函/响应函格式要求确定性提取策略（纯核心纯函数，无 Spring 依赖）.
 *
 * <p>基于中国招投标法律法规与标准招标文件规范（前附表、投标人须知、合同条款）：
 * 1. 识别招标文件要求的函件类型（投标函 / 报价函 / 谈判响应函 / 磋商响应函）；
 * 2. 识别招标文件指定的抬头/采购人/招标人称谓（如“张家口银行股份有限公司”）；
 * 3. 识别招标明确要求的投标/响应文件有效期天数（如“90日历天”）；
 * 4. 作为 LLM 招标要求抽取的确定性双轨融合与交叉检验基准（以确定性原文硬事实为准）.
 */
public final class DeterministicBidLetterExtractor {

    /** 投标/响应文件有效期天数正则（支持表格竖线、冒号、键值对及修饰词：90日历天、60天、自开标起90天）. */
    private static final Pattern VALIDITY_DAYS_PATTERN = Pattern.compile(
            "(?:投标有效期|响应文件有效期|报价有效期)[^0-9\\n]{0,30}([0-9]{1,4})\\s*(?:个?日历天|个?工作日|天|日)"
    );

    /** 招标人/采购人全称匹配正则（支持前附表、公告、抬头声明）. */
    private static final Pattern BUYER_RECIPIENT_PATTERN = Pattern.compile(
            "(?:招标人|采购人|买方|招标代理机构|项目业主)[\\s:：名称为是|丨]*([\\u4e00-\\u9fa5（）()]{4,40}(?:公司|集团|中心|局|处|院|指挥部|委员会|学校|医院|银行|部|馆|所|厂))"
    );

    /** 竞争性谈判/磋商/单一来源报价函专属正则. */
    private static final Pattern QUOTATION_LETTER_PATTERN = Pattern.compile(
            "(?:谈判采购|竞争性谈判|竞争性磋商|询价采购|单一来源)[^\\n]{0,100}(?:《报价函》|《响应函》|《谈判响应函》|提交报价函|提交响应函)"
    );

    private DeterministicBidLetterExtractor() {
    }

    /**
     * 从招标文件全文中提取投标函/报价函要求.
     *
     * @param tenderText 招标文件文本内容
     * @return 提取到的函件要求，若无法确定则返回 null（交由 LLM 或标书默认公文格式核验，绝不武断臆造）
     */
    public static TenderRequirements.BidLetterRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        // 1. 提取投标有效期天数
        Integer validityDays = null;
        Matcher validityMatcher = VALIDITY_DAYS_PATTERN.matcher(tenderText);
        if (validityMatcher.find()) {
            validityDays = parseDigitsSafely(validityMatcher.group(1).trim());
        }

        // 2. 提取招标人/采购人全称
        String recipientName = null;
        Matcher buyerMatcher = BUYER_RECIPIENT_PATTERN.matcher(tenderText);
        if (buyerMatcher.find()) {
            recipientName = buyerMatcher.group(1).trim();
        }

        // 3. 识别函件类型（默认为投标函，若为谈判/磋商采购且明确要求报价函则为报价函）
        String letterType = null;
        if (QUOTATION_LETTER_PATTERN.matcher(tenderText).find() || tenderText.contains("《报价函》")
                || (tenderText.contains("竞争性谈判") && tenderText.contains("报价函"))) {
            letterType = "报价函";
        } else if (tenderText.contains("《谈判响应函》") || tenderText.contains("谈判响应函")) {
            letterType = "谈判响应函";
        } else if (tenderText.contains("《磋商响应函》") || tenderText.contains("磋商响应函")) {
            letterType = "磋商响应函";
        } else if (tenderText.contains("《投标响应函》") || tenderText.contains("投标响应函")) {
            letterType = "投标响应函";
        } else if (tenderText.contains("《投标函》") || tenderText.contains("投标函格式") || tenderText.contains("投标函")) {
            letterType = "投标函";
        }

        // 若三大要素完全未提取到任何实质特征，返回 null
        if (validityDays == null && recipientName == null && letterType == null) {
            return null;
        }

        String description = "招标文件条款提取："
                + (letterType != null ? "函件类型=" + letterType + "；" : "")
                + (recipientName != null ? "抬头称谓=" + recipientName + "；" : "")
                + (validityDays != null ? "有效期=" + validityDays + "天" : "");

        return new TenderRequirements.BidLetterRequirement(letterType, recipientName, validityDays, description);
    }

    private static Integer parseDigitsSafely(final String digits) {
        if (digits == null || digits.isEmpty()) {
            return null;
        }
        int val = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            val = val * 10 + (c - '0');
        }
        return val;
    }
}
