// Input: Candidate case title, response text, and optional existing quality score
// Output: Quality admission decision (QualityVerdict) and filtering
// Pos: casework/domain/policy/ - 候选池低质废料切片与纯模板过滤策略（FP-Java 无外部依赖）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 纯核心：候选案例内容质量准入策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 *
 * <p>核心职责：识别并阻断纯格式模板（如空白授权书/承诺函）、敷衍占位符（如“详见附件”）、
 * 目录与排版残片、低信息熵重复字符文本，保证进入推荐候选池的切片具有实质应答价值。</p>
 */
public final class CaseContentQualityPolicy {

    private static final int MIN_SUBSTANTIVE_TEXT_LENGTH = 6;
    private static final int MIN_QUALITY_SCORE_THRESHOLD = 40;

    /** 格式模板典型关键词（出现在标题或正文中） */
    private static final List<String> TEMPLATE_KEYWORDS = List.of(
            "授权委托书格式", "法定代表人授权委托书", "法定代表人身份证明", "授权委托书声明",
            "投标承诺函格式", "投标承诺函模板", "投标保函格式", "投标保证金格式", "履约保函格式",
            "廉政承诺书格式", "诚信投标承诺书格式", "服务承诺书格式",
            "格式自拟", "格式如下", "格式见下表", "格式附后", "格式可参考",
            "特此委托", "特此证明", "无转委托权"
    );

    /** 签字/盖章/日期等空白填空标记 */
    private static final Pattern FORM_BLANK_PATTERN = Pattern.compile(
            "(_{3,}|（盖章）：|（签字）：|签字或盖章|委托代理人（签字）|日期：\\s*_{2,}年|\\s*_{2,}年_{1,}月_{1,}日|加盖公章|法定代表人（签字或盖章）)"
    );

    /** 敷衍占位符正则（短文本且仅引用附件或空头承诺） */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)^(.*(详见|参见|见)(附件|附表|下表|技术标|商务标|投标文件).*|.*完全(满足|响应|符合)招标文件要求.*(特此|严格执行).*)$"
    );

    /** 目录点号与残片特征 */
    private static final Pattern TOC_LEADER_PATTERN = Pattern.compile(
            "(\\.{3,}|…{2,}|·{3,})"
    );

    public record QualityVerdict(
            boolean isAdmissible,
            int assessedQualityScore,
            String rejectReason
    ) {}

    private CaseContentQualityPolicy() {}

    /**
     * 对候选案例进行质量准入评估
     */
    public static QualityVerdict evaluateQuality(String title, String responseText, Integer existingQualityScore) {
        if (responseText == null || responseText.isBlank()) {
            return new QualityVerdict(false, 0, "文本过短/无实质内容");
        }

        String stripped = responseText.strip();
        if (stripped.length() < MIN_SUBSTANTIVE_TEXT_LENGTH) {
            return new QualityVerdict(false, 0, "文本过短/无实质内容");
        }

        String combined = (title != null ? title + "\n" : "") + stripped;

        // 1. 敷衍占位符拦截（80字以内且匹配敷衍占位符）
        if (stripped.length() <= 80 && PLACEHOLDER_PATTERN.matcher(stripped).find()) {
            return new QualityVerdict(false, 0, "纯占位符/敷衍应答");
        }

        // 2. 纯格式模板与空白表格标记拦截
        boolean hasTemplateMarker = TEMPLATE_KEYWORDS.stream().anyMatch(combined::contains);
        boolean hasFormBlank = FORM_BLANK_PATTERN.matcher(stripped).find();
        if (hasTemplateMarker && hasFormBlank) {
            return new QualityVerdict(false, 0, "纯格式模板/非实质方案");
        }
        if (countBlankMarkers(stripped) >= 2) {
            return new QualityVerdict(false, 0, "纯格式模板/非实质方案");
        }
        if (title != null && (title.contains("授权委托书") || title.contains("承诺函格式") || title.contains("保函格式") || title.contains("模板"))) {
            if (hasFormBlank || stripped.length() < 50) {
                return new QualityVerdict(false, 0, "纯格式模板/非实质方案");
            }
        }

        // 3. 目录与排版残片拦截
        if (TOC_LEADER_PATTERN.matcher(stripped).find() && countPunctuationRatio(stripped) > 0.25) {
            return new QualityVerdict(false, 0, "目录或排版残片");
        }

        // 4. 低信息熵/单一字符重复拦截
        if (isLowEntropyText(stripped)) {
            return new QualityVerdict(false, 0, "低信息熵/字符重复异常");
        }

        // 5. 质量分准入判定（仅在明确存在历史低质量分时拦截）
        int qualityScore = existingQualityScore != null
                ? existingQualityScore
                : (stripped.length() >= 120 ? KnowledgeCaseFeatures.qualityScore(stripped) : 60);
        if (existingQualityScore != null && existingQualityScore < MIN_QUALITY_SCORE_THRESHOLD && stripped.length() < 100) {
            return new QualityVerdict(false, qualityScore, "历史质量分过低(<40)");
        }

        return new QualityVerdict(true, qualityScore, null);
    }

    /**
     * 判断单个案例是否为低质切片
     */
    public static boolean isLowQuality(KnowledgeCaseReadModel candidate) {
        if (candidate == null) {
            return true;
        }
        return isLowQuality(candidate.getScoringPointTitle(), candidate.getResponseText(), candidate.getQualityScore());
    }

    /**
     * 判断指定标题和文本是否为低质切片
     */
    public static boolean isLowQuality(String title, String responseText, Integer existingQualityScore) {
        return !evaluateQuality(title, responseText, existingQualityScore).isAdmissible();
    }

    /**
     * 批量过滤：剔除低质废料切片，仅保留高质可准入候选
     */
    public static <T extends KnowledgeCaseReadModel> List<T> filterAdmissible(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> !isLowQuality(candidate))
                .toList();
    }

    private static int countBlankMarkers(String text) {
        var matcher = FORM_BLANK_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static double countPunctuationRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int punctCount = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch) || ch == '.' || ch == '…' || ch == '—' || ch == '_' || ch == '-') {
                punctCount++;
            }
        }
        return (double) punctCount / text.length();
    }

    private static boolean isLowEntropyText(String text) {
        if (text.length() >= 40) {
            Set<Character> uniqueChars = new HashSet<>();
            int alphanumericCount = 0;
            for (char ch : text.toCharArray()) {
                if (!Character.isWhitespace(ch)) {
                    alphanumericCount++;
                    uniqueChars.add(ch);
                }
            }
            if (alphanumericCount >= 30 && (double) uniqueChars.size() / alphanumericCount < 0.15) {
                return true;
            }
        }
        return text.matches("(?s).*(-{10,}|_{10,}|={10,}|\\*{10,}).*");
    }
}
