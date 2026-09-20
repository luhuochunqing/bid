// Input: 候选标题字符串（可能为 null / 空白 / 正文句 / 无编号小标题 / 只有编号没有标题文字 / 字母列表标记 A. B、）
// Output: 标题分类判定的布尔值（句子型伪标题 / 已验证章节标题）
// Pos: Backend/casework 模块应用层纯函数——标题分类契约的唯一判据来源
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题分类契约（纯函数，无框架依赖）：Markdown 标题解析与「原文所在标题」溯源共用同一判据。
 *
 * <p>两级门，逐级收紧：
 * <ol>
 *   <li>{@link #isSentenceLikeTitle(String)} 句子门：PDF→markdown 转换器会把加粗正文句输出成
 *       {@code ## 正文句子}，正文句一律不可作为标题。命中即不作为章节边界。</li>
 *   <li>{@link #isChapterTitle(String)} 章节号契约：在句子门之上再要求串首是可识别编号，
 *       <b>且编号之后还有标题文字</b>。只有"章节号 + 标题文字"形式的标题才是<b>已验证章节</b>，
 *       才可充当对外展示/写回的「原文所在标题」。两类不达标者一律不给来源：
 *       无编号的加粗小标题（如 {@code ## 项目实施}）可能是真实排版层级但没有序号可核对；
 *       只有编号没有文字的标题（如 {@code ## 第一章}）同样无法自证指向哪一节。</li>
 * </ol>
 *
 * <p>两条规则分别与前端 {@code caseDetailFormatter.isSentenceLikeTitle} /
 * {@code caseDetailFormatter.isVerifiedChapterTitle} 同规则，由共享夹具
 * {@code casework/sentence-like-title-cases.json} 与
 * {@code casework/chapter-title-contract-cases.json} 双向锚定。
 * <b>改规则必须同时改两侧 + 夹具</b>（两侧测试共读同一文件，单侧漂移即失败）。</p>
 *
 * <p>两侧的空白/分隔符字符类必须<b>逐字一致</b>：Java 的 {@code \s} 与 JS 的 {@code \s} Unicode
 * 范围不同，故全角空格（U+3000）与不换行空格（U+00A0）在两侧都显式列出，由夹具中的
 * U+3000 边界用例锚定。</p>
 */
public final class MarkdownTitleRules {

    private static final Pattern SENTENCE_PUNCTUATION_PATTERN = Pattern.compile("[。！？!?；;]");

    private static final Pattern COMMA_PATTERN = Pattern.compile("[，,]");

    /**
     * 章节号前缀：章节标题必须以可识别的编号开头。编号形式清单见
     * {@link #isChapterTitle(String)}，此处不再重复。
     */
    private static final Pattern CHAPTER_NUMBER_PREFIX_PATTERN = Pattern.compile(
            "^(?:第[一二三四五六七八九十百千万0-9]+[章节篇卷部分条]"
                    + "|\\d+(?:\\.\\d+)+[、.．]?"
                    + "|\\d+[、.．]"
                    + "|[一二三四五六七八九十百]+[、.．]"
                    + "|[（(](?:\\d+|[一二三四五六七八九十百]+)[）)]"
                    + "|[①-⑩])");

    /**
     * 编号两侧的空白与分隔符（与前端 {@code TITLE_SEPARATOR_CLASS} 逐字一致）：用于剥掉首尾噪声，
     * 以及判断"编号之后是否还有标题文字"。
     */
    private static final Pattern EDGE_SEPARATOR_PATTERN =
            Pattern.compile("^[\\s　 、.．,，:：]+|[\\s　 、.．,，:：]+$");

    private static final Pattern LEADING_SEPARATOR_PATTERN =
            Pattern.compile("^[\\s　 、.．,，:：]+");

    private static final int MAX_TITLE_LENGTH = 80;

    private static final int MAX_COMMA_TITLE_LENGTH = 30;

    private MarkdownTitleRules() {}

    /**
     * 句子型伪标题识别：正文句子不可作为章节标题，必须拒绝。
     *
     * <p>判定规则：含句读（。！？!?；;）→ 伪标题；长度 &gt; 80 → 伪标题；
     * 含逗号且长度 &gt; 30 → 伪标题；null / 空白 → 判定为不可用（返回 true）。</p>
     */
    public static boolean isSentenceLikeTitle(String title) {
        if (title == null) {
            return true;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (SENTENCE_PUNCTUATION_PATTERN.matcher(trimmed).find()) {
            return true;
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            return true;
        }
        return COMMA_PATTERN.matcher(trimmed).find() && trimmed.length() > MAX_COMMA_TITLE_LENGTH;
    }

    /**
     * 章节标题契约：只有"章节号 + 标题文字"形式的标题才是可对外展示/写回的<b>已验证章节</b>。
     *
     * <p>判定规则：先过 {@link #isSentenceLikeTitle} 门（正文句一律不是章节）；再剥掉首尾空白与
     * 分隔符噪声，要求以章节号开头（第X章/第X节、1.2.3、1.、、一、、（1）、①），编号位于串首；
     * 最后要求编号之后仍留有标题文字——{@code 1.}、{@code 第一章}、{@code （1）}、{@code ①} 这类
     * 只有编号的串不是章节。**字母列表标记不算章节号**：{@code A. 附录清单}、{@code B、服务方案}
     * 这类字母枚举与正文里的字母列表从字面上无从区分，因此不能自证为数值章节序号。</p>
     */
    public static boolean isChapterTitle(String title) {
        if (isSentenceLikeTitle(title)) {
            return false;
        }
        String normalized = EDGE_SEPARATOR_PATTERN.matcher(title).replaceAll("");
        Matcher matcher = CHAPTER_NUMBER_PREFIX_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        String rest = normalized.substring(matcher.end());
        return !LEADING_SEPARATOR_PATTERN.matcher(rest).replaceFirst("").isEmpty();
    }
}
