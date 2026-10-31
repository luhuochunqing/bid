// Input: 文本与关键词命中区间
// Output: 该命中是否属于招标规则条件引用（纯函数）
// Pos: compliance/domain/policy/ - 否决/负偏离语境判定
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只豁免局部条件规则，不以全篇出现规则词为自身违规开脱。 */
final class RuleCitationContext {
    private static final Pattern RULE = Pattern.compile("评标委员会|评审委员会|应当否决|将否决|予以否决|作无效|无效投标|否决其投标");
    private static final Pattern SCORING_CONDITION = Pattern.compile("(?:(?:不满足|不符合)(?:要求|上述要求|上述条件|(?:任意)?[0-9一二三四五六七八九十]+项)?(?:或未提供(?:证明文件|材料))?(?:者|的)?|未提供(?:证明文件|材料)(?:者|的)?|缺少(?:一项|任意一项))[,，]?(?:不得分|不予计分)");
    private static final Pattern CONDITION = Pattern.compile("(?:若|如|一旦)|(?:有|存在|出现|发生).{0,90}(?:的|时)|投标人不得");
    private static final Pattern OWN_FACT = Pattern.compile("(?:我司|本公司|我方|本次投标|本项目实际).{0,50}(?:已|存在|采用|进行|发生|负偏离|不满足)|(?:经核实|实际情况)[：:]");

    private RuleCitationContext() { }

    static boolean isRuleCitation(final String text, final int start, final int end) {
        int left = start;
        while (left > 0 && start - left < 350 && !isSentenceEnd(text.charAt(left - 1))) left--;
        int right = end;
        while (right < text.length() && right - end < 200 && !isSentenceEnd(text.charAt(right))) right++;
        String context = text.substring(left, right).replaceAll("\\s+", "");
        String fromHit = text.substring(start, right).replaceAll("\\s+", "");
        String prefix = text.substring(left, start).replaceAll("\\s+", "");
        int separator = Math.max(Math.max(prefix.lastIndexOf('，'), prefix.lastIndexOf(',')),
                Math.max(prefix.lastIndexOf('；'), prefix.lastIndexOf(';')));
        String lead = prefix.substring(separator + 1);
        boolean ruleLead = lead.matches("(?:[（(]?[0-9一二三四五六七八九十]+[）).、])?")
                || lead.matches("(?:评审标准|评分标准|评分规则|评审细则|评审索引)[：:]?(?:[0-9]+[）).、])?")
                || lead.matches("不得分[0-9]+[）).、]");
        Matcher scoring = SCORING_CONDITION.matcher(context);
        boolean withinScoringCondition = false;
        while (scoring.find()) {
            String scoringLead = context.substring(0, scoring.start());
            if (prefix.length() >= scoring.start() && prefix.length() < scoring.end()
                    && scoringLead.matches("(?:评审标准|评分标准|评分规则|评审细则|评审索引)?[：:]?")) {
                withinScoringCondition = true;
            }
        }
        return withinScoringCondition || (ruleLead && SCORING_CONDITION.matcher(fromHit).lookingAt()
                || RULE.matcher(context).find() && CONDITION.matcher(context).find())
                && !OWN_FACT.matcher(context).find();
    }

    /** 门槛中的否定义务与投标人自述“低于要求”不是同一事实。 */
    static boolean isMinimumThreshold(final String text, final int start, final String keyword) {
        if (!"低于".equals(keyword)) return false;
        String prefix = text.substring(Math.max(0, start - 32), start).replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
        return prefix.matches("(?s).*(?:不得|不应|不应当|不能|不允许|不可以|不可|须不|应不|不)$");
    }

    static boolean isActualLow(final String text, final int start) {
        return DeviationEvidencePolicy.isOwnLow(text, start);
    }

    static boolean isConditionalLow(final String text, final int start) {
        if (isActualLow(text, start)) return false;
        String prefix = text.substring(Math.max(0, start - 30), start).replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
        String suffix = text.substring(start, Math.min(text.length(), start + 80)).replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
        return prefix.matches("(?s).*(?:如|若|如果|一旦)$")
                || suffix.matches("(?s).*(?:不得分|不予计分|视为不合格|予以否决|不得通过).*");
    }

    private static boolean isSentenceEnd(final char c) {
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?';
    }
}
