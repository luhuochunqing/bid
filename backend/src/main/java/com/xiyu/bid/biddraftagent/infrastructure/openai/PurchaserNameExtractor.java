// Input: 招标文件候选文本
// Output: 正则兜底提取的招标主体名称（AI 漏抽时使用）
// Pos: biddraftagent/infrastructure/openai — 招标主体正则兜底提取（单一职责）
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 招标主体（purchaserName）正则兜底提取器。
 *
 * <p>设计动机：{@code 招 标 人：张家口银行股份有限公司} 这类"标签+冒号+机构名"是
 * 正则可稳定命中的结构化格式，却因交给 AI 语义判断而在干扰强（招标人/代理机构共现多次）
 * 的文档中被漏识别。遵循 regex-vs-llm-structured-text 原则：结构化标签行走正则，
 * 仅在 AI 未能返回 purchaserName 时兜底。
 */
final class PurchaserNameExtractor {

    /** 空白与不可见字符归一化正则，与 {@link TenderIntakeTextProcessor} 保持一致。 */
    private static final String WHITESPACE_PATTERN =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+";

    /** 招标主体机构名有效长度下限，用于过滤噪声。 */
    private static final int MIN_NAME_LENGTH = 4;

    private PurchaserNameExtractor() {
    }

    /**
     * 从候选文本中正则兜底提取招标主体名称。
     *
     * <p>对候选文本按行归一化后，用 {@link PurchaserAliases#ALL} 标签匹配「标签紧跟冒号」
     * 的标签行，取机构名，多标签取出现次数最多者。
     *
     * <p>排除规则：
     * <ul>
     *   <li>含"代理"的行（代理机构/招标代理）不作为招标主体</li>
     *   <li>叙事性行（标签后未紧跟冒号，如"招标人不予受理"）自然不匹配</li>
     *   <li>机构名为空或过短（&lt;4 字）视为无效</li>
     * </ul>
     *
     * @return 提取到的招标主体名称；无法提取时返回空字符串
     */
    static String extractPurchaserName(String candidateText) {
        if (candidateText == null || candidateText.isBlank()) {
            return "";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String rawLine : candidateText.split("\\R")) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            String normalized = normalize(rawLine);
            if (normalized.contains("代理")) {
                continue;
            }
            String name = matchPurchaserLabel(normalized);
            if (name != null && !name.isBlank() && name.length() >= MIN_NAME_LENGTH) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll(WHITESPACE_PATTERN, "");
    }

    /**
     * 从归一化后的整行中匹配"招标主体标签+冒号+机构名"，返回机构名。
     * 标签必须紧跟半角或全角冒号（排除"招标人不予受理"等叙事性行）。
     */
    private static String matchPurchaserLabel(String normalizedLine) {
        for (String label : PurchaserAliases.ALL) {
            int idx = normalizedLine.indexOf(label);
            if (idx < 0) {
                continue;
            }
            int afterLabel = idx + label.length();
            if (afterLabel >= normalizedLine.length()) {
                continue;
            }
            char sep = normalizedLine.charAt(afterLabel);
            if (sep != ':' && sep != '：') {
                continue;
            }
            String name = normalizedLine.substring(afterLabel + 1).trim();
            int cut = name.indexOf('。');
            if (cut >= 0) {
                name = name.substring(0, cut);
            }
            return name;
        }
        return null;
    }
}
