// Input: Candidate scoring code string
// Output: Boolean validity of hierarchical scoring code
// Pos: scoreparse/domain - Pure domain tokenizer for hierarchical scoring item codes
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Pattern;

/**
 * 结构化评分项编号分词与校验器 (Hierarchical Code Tokenizer).
 * <p>
 * 纯领域工具类 (FP-Java 契约: 纯函数、无状态、无框架依赖).
 * <p>
 * 精确判定文本是否为合法的结构化评分项/评审条款编号，
 * 兼顾单数字、层级数字、罗马数字、中文序号、带圈数字以及业务字母前缀，
 * 同时严格拦截包含年限、金额、数量等以数字开头的普通描述正文 (防止过拟合误伤).
 */
public final class HierarchicalCodeTokenizer {

    private HierarchicalCodeTokenizer() {}

    // 1. 独立纯数字编号: 如 "1", "7", "12", "99" (不能紧跟中文或字母)
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d{1,4}$");

    // 2. 层级数字编号: 如 "1.1", "1.1.1", "1-1", "1-2-1", "3.2.1", "3.2.2.1"
    private static final Pattern HIERARCHICAL_NUMBER = Pattern.compile("^\\d{1,3}(?:[.\\-]\\d{1,3})+$");

    // 3. 数字后接独立标点符号或括号结尾: 如 "1.", "1．", "1、", "1)", "1）", "1-", "(1)", "（1）", "1(1)", "1（1）", "1-1(1)", "1.1(2)"
    private static final Pattern NUMBER_WITH_PUNCTUATION = Pattern.compile("^(?:\\d{1,3}(?:[.\\-]\\d{1,3})*\\s*[.．、\\-)|）]|[(（]\\s*\\d{1,3}\\s*[)）][.．、]?|\\d{1,3}(?:[.\\-]\\d{1,3})*\\s*[(（]\\s*\\d{1,3}\\s*[)）])$");

    // 4. 中文序号: 如 "一、", "二、", "（一）", "(一)", "第1条", "第一条", "第一章", "条款1", "条目一"
    private static final Pattern CHINESE_ORDINAL = Pattern.compile("^(?:[一二三四五六七八九十百]+[、.．]|[（(][一二三四五六七八九十百]+[）)]|第[一二三四五六七八九十百0-9]+[条章节款项]|(?:条款|条目)[一二三四五六七八九十百0-9]+)$");

    // 5. 罗马数字: 如 "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" (及其带标点形式 "I.", "II、", "(I)")
    private static final Pattern ROMAN_ORDINAL = Pattern.compile("^(?:[(（]?(?:[IVXLCDMivxlcdm]+)[)）]?[.．、\\-]?)$");

    // 6. 带圈数字或专用序号符号: 如 "①", "②", "③", "⑴", "⑵", "⒈", "⒉"
    private static final Pattern CIRCLED_OR_SPECIAL_ORDINAL = Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳⑴⑵⑶⑷⑸⑹⑺⑻⑼⑽⒈⒉⒊⒋⒌⒍⒎⒏⒐]$");

    // 7. 业务英文字母编码: 如 "BIZ-1", "TECH_01", "PRICE_2", "A-1", "B.2", "A1", "F.1.1"
    private static final Pattern BUSINESS_PREFIX_CODE = Pattern.compile("^(?i)(?:BIZ|TECH|PRICE|[A-Z])[-_.\\d][A-Z0-9._\\-]*$");

    // 8. 常见普通描述词后缀黑名单 (以数字开头但紧跟年份、金额、数量词的普通正文，绝对不能当编号)
    private static final Pattern NON_CODE_TEXT_SUFFIX = Pattern.compile("^\\d{1,4}\\s*(?:年|月|日|度|个|家|项|笔|次|人|份|页|万|亿|元|分|%|‰).*");

    /**
     * 判断给定字符串是否为合法的评分项编号。
     *
     * @param code 候选编号字符串 (如 "1.1", "7", "2024年度业绩")
     * @return true 若为合法结构化评分项编号; false 若为空、普通正文或非编号文本
     */
    public static boolean isScoringCode(String code) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        // 强行拦截类似 "2024年度", "3年期", "5家", "100万元" 的普通描述文本
        if (NON_CODE_TEXT_SUFFIX.matcher(trimmed).matches()) {
            return false;
        }

        return PURE_NUMBER.matcher(trimmed).matches()
                || HIERARCHICAL_NUMBER.matcher(trimmed).matches()
                || NUMBER_WITH_PUNCTUATION.matcher(trimmed).matches()
                || CHINESE_ORDINAL.matcher(trimmed).matches()
                || ROMAN_ORDINAL.matcher(trimmed).matches()
                || CIRCLED_OR_SPECIAL_ORDINAL.matcher(trimmed).matches()
                || BUSINESS_PREFIX_CODE.matcher(trimmed).matches();
    }
}
