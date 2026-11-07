// Input: 招标文件解析后的文本内容（String）
// Output: 确定性提取的投标保证金要求模型（TenderRequirements.DepositRequirement）
// Pos: compliance/domain/policy/ - 招标保证金要求确定性提取纯核心策略（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标投标保证金/担保要求确定性提取策略（纯核心纯函数，无 Spring 依赖）.
 *
 * <p>基于中国招投标法律法规与标准招标文件规范（前附表、投标人须知）：
 * 1. 识别免收/不涉及保证金与投标担保的显式特征（前附表格单元格、标准自然语言声明）；
 * 2. 识别需缴纳保证金/担保的金额（支持阿拉伯数字与中文大写金额）与形式；
 * 3. 杜绝“全文缺词即判免收”的武断假设（缺词时返回 null 交由上下文与大模型语义裁决）；
 * 4. 作为 LLM 招标要求抽取的确定性双轨融合与交叉检验基准.
 */
public final class DeterministicDepositExtractor {

    /** 明确免收/不涉及自然语言短语列表（覆盖保证金、投标担保及政采响应担保同义词）. */
    private static final List<String> EXPLICIT_NOT_REQUIRED_PHRASES = List.of(
            "免收投标保证金",
            "不收取投标保证金",
            "免交投标保证金",
            "不要求提交投标保证金",
            "不要求递交投标保证金",
            "免收保证金",
            "无需缴纳保证金",
            "无需缴纳投标保证金",
            "不收取保证金",
            "免缴纳投标保证金",
            "免缴纳保证金",
            "不涉及投标保证金",
            "不涉及保证金",
            "本项目免收保证金",
            "本项目不收取投标保证金",
            "本项目不要求缴纳投标保证金",
            "免收投标担保",
            "不收取投标担保",
            "无需提交投标担保",
            "无需递交投标担保",
            "不涉及投标担保",
            "免交投标担保",
            "不要求提交投标担保",
            "不要求递交投标担保",
            "不要求递交响应担保",
            "不要求提交响应担保",
            "免收响应担保",
            "不收取响应担保",
            "无需提交响应担保",
            "无需递交响应担保",
            "无需缴纳响应担保",
            "不涉及响应担保",
            "免交响应担保",
            "本项目免收响应担保",
            "本项目不收取响应担保",
            "本项目不要求递交响应担保",
            "本项目不要求提交响应担保",
            "免收响应保证金",
            "不收取响应保证金",
            "无需缴纳响应保证金",
            "不涉及响应保证金",
            "免交响应保证金",
            "不要求提交响应保证金",
            "不要求递交响应保证金"
    );

    /**
     * 前附表或条目式免收正则（如：投标保证金：无 / 投标担保：不适用 / 响应担保：不要求递交 / 投标保证：0元）.
     * 覆盖 Markdown 表格、文本表格及冒号键值对.
     */
    private static final Pattern NOT_REQUIRED_CELL_PATTERN = Pattern.compile(
            "(?:投标保证金|投标担保|投标保证|响应担保|响应保证金|担保金|保证金|保函)[\\s:：|丨]*(?:[（(](?:元|万元|人民币|形式|比例)[)）][\\s:：|丨]*)?"
                    + "(?:无|不适用|免收|不收取|免交|不需要|无需|(?<![0-9])0元|(?<![0-9])0|(?<![0-9])零元|不涉及|未要求|未作要求|不要求递交|不要求提交|无需递交|无需提交|(?<![0-9])0\\.00元|(?<![0-9])0.00|不采用|无要求|免缴纳)"
                    + "(?:[\\s,，。；;）)|丨]|$)"
    );

    /**
     * 需缴纳保证金/担保匹配正则（提取明确金额：支持阿拉伯数字金额与中文大写金额）.
     * 例：投标保证金：10万元、投标保证金金额为人民币壹拾万元整、响应担保金额：50,000元、投标保证金：2%.
     */
    private static final Pattern REQUIRED_AMOUNT_PATTERN = Pattern.compile(
            "(?:投标保证金|投标担保|投标保证|响应担保|响应保证金|保证金金额|担保金额|缴纳金额)[\\s:：为是|丨]*(?:人民币|¥|￥)?\\s*"
                    + "([0-9,.]+\\s*(?:万?元|%)|[零壹贰叁肆伍陆柒捌玖拾佰仟万亿元整]+)"
    );

    private DeterministicDepositExtractor() {
    }

    /**
     * 从招标文件全文中提取保证金/担保要求.
     *
     * @param tenderText 招标文件文本内容
     * @return 提取到的保证金要求，若无法确定则返回 null（交由 LLM 或上层策略裁决，绝不武断断言免收）
     */
    public static DepositRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        // 1. 命中明确要求缴纳的金额（硬事实优先：包含阿拉伯数字与中文大写金额，优先于模糊单元格）
        Matcher amountMatcher = REQUIRED_AMOUNT_PATTERN.matcher(tenderText);
        if (amountMatcher.find()) {
            String amount = amountMatcher.group(1).trim();
            // 排除匹配到 0元、零元 等误判
            if (!amount.equals("0元") && !amount.equals("0") && !amount.equals("零元") && !amount.equals("0.00元")) {
                return DepositRequirement.required(amount, null, "招标文件要求缴纳投标保证金/担保：" + amount);
            }
        }

        // 2. 命中显式免收自然语言短语（高置信度）
        for (String phrase : EXPLICIT_NOT_REQUIRED_PHRASES) {
            if (tenderText.contains(phrase)) {
                return DepositRequirement.notRequired("招标文件明确说明：" + phrase);
            }
        }

        // 3. 命中前附表格/清单式免收表达式（如 投标保证金：无、投标担保：不适用）
        Matcher cellMatcher = NOT_REQUIRED_CELL_PATTERN.matcher(tenderText);
        if (cellMatcher.find()) {
            String match = cellMatcher.group().replaceAll("\\s+", " ").trim();
            return DepositRequirement.notRequired("招标文件条款规定：" + match);
        }

        // 4. 若无确凿证据（无论全文是否包含保证金/担保字眼），坚决返回 null，交由 LLM 或人工复核，杜绝无词直接判免收的危险假设
        return null;
    }

    /**
     * 判断招标文件是否明确未要求缴纳保证金（免收/不涉及）.
     */
    public static boolean isDepositNotRequired(final String tenderText) {
        DepositRequirement req = extract(tenderText);
        return req != null && Boolean.FALSE.equals(req.required());
    }

    /**
     * 判断具备实质性长度（>= 100字）的招标文件全文是否完全不涉及保证金/投标担保/响应担保/退款要求.
     */
    public static boolean isSubstantiveTenderWithoutDepositMention(final String tenderText) {
        return isDepositNotRequired(tenderText)
                || (tenderText != null && tenderText.length() >= 100
                    && !tenderText.contains("保证金") && !tenderText.contains("投标担保")
                    && !tenderText.contains("响应担保") && !tenderText.contains("退款"));
    }
}
