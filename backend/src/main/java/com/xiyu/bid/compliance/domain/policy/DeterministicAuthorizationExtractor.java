// Input: 招标文件解析后的文本内容（String）
// Output: 确定性提取的授权委托书要求模型（AuthorizationRequirement）
// Pos: compliance/domain/policy/ - 授权委托书要求确定性提取纯核心策略（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件授权委托书及法定代表人证明要求确定性提取策略（纯核心纯函数，无 Spring 依赖）.
 *
 * <p>基于中国招投标法律法规与标准招标文件规范（资格审查条款、投标文件组成、格式附录）：
 * 1. 识别招标文件是否要求提供授权委托书与法定代表人身份证明；
 * 2. 识别是否允许法定代表人亲自出席/签署时豁免授权委托书；
 * 3. 识别招标文件指定的格式模板标题；
 * 4. 作为 LLM 招标要求抽取的确定性双轨融合与交叉检验基准.
 */
public final class DeterministicAuthorizationExtractor {

    /** 法定代表人亲签可免授权委托书豁免条款正则. */
    private static final Pattern LEGAL_REP_ONLY_PATTERN = Pattern.compile(
            "(?:法定代表人(?:亲自)?(?:签署|参加|投标|出席)[^。\\n]{0,30}(?:无需|不需|不用|可不|免于?)[^。\\n]{0,20}(?:授权委托书|授权书)|若由?法定代表人[^。\\n]{0,30}仅需?提供法定代表人身份证明)"
    );

    /** 模板格式标题匹配正则. */
    private static final Pattern TEMPLATE_TITLE_PATTERN = Pattern.compile(
            "(?:格式[一二三四五六七八九十\\d\\s:：、.]+|附件[一二三四五六七八九十\\d\\s:：、.]+)?(法定代表人身份证明及授权委托书|法定代表人授权委托书|法定代表人身份证明与授权委托书|法定代表人证明书及授权委托书|法定代表人授权书|授权委托书)"
    );

    /** 明确无需授权委托书声明（如自然人直接响应）. */
    private static final Pattern NOT_REQUIRED_PATTERN = Pattern.compile(
            "(?:自然人(?:投标|响应)?)[^。\\n]{0,30}(?:无需|不需|不用|不涉及|可不提供|免于?提供)(?:授权委托书|授权书)"
    );

    private DeterministicAuthorizationExtractor() {
    }

    /**
     * 从招标文件全文中提取授权委托书与法定代表人证明要求.
     *
     * @param tenderText 招标文件文本内容
     * @return 提取到的要求契约，若全文未提及相关词汇则返回 null（交上级裁决，不武断断言）
     */
    public static AuthorizationRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        // 1. 检查是否存在明确免除情形（如自然人投标）
        if (NOT_REQUIRED_PATTERN.matcher(tenderText).find()) {
            return new AuthorizationRequirement(false, true, null, "招标文件载明无需提供授权委托书（自然人投标等情形）");
        }

        boolean mentionsAuth = tenderText.contains("授权委托书")
                || tenderText.contains("法定代表人授权书")
                || tenderText.contains("委托代理人")
                || tenderText.contains("被授权人");
        boolean mentionsLegalRep = tenderText.contains("法定代表人身份证明")
                || tenderText.contains("法定代表人证明书");

        if (!mentionsAuth && !mentionsLegalRep) {
            return null;
        }

        // 2. 识别是否允许法定代表人亲自签署时免授权书
        boolean allowLegalRepOnly = LEGAL_REP_ONLY_PATTERN.matcher(tenderText).find();

        // 3. 识别指定模板标题
        String templateTitle = null;
        Matcher titleMatcher = TEMPLATE_TITLE_PATTERN.matcher(tenderText);
        if (titleMatcher.find()) {
            templateTitle = titleMatcher.group(1);
        } else if (mentionsAuth && mentionsLegalRep) {
            templateTitle = "法定代表人身份证明及授权委托书";
        } else if (mentionsAuth) {
            templateTitle = "法定代表人授权委托书";
        } else {
            templateTitle = "法定代表人身份证明";
        }

        String description = allowLegalRepOnly
                ? "需提供法定代表人身份证明；法定代表人亲自参加的无需授权委托书，委托代理人参加的须提供授权委托书"
                : "需提供法定代表人身份证明及有效授权委托书，委托代理人须经合法授权";

        return new AuthorizationRequirement(true, allowLegalRepOnly, templateTitle, description);
    }
}
