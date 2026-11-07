// Input: 文本与关键词命中区间
// Output: 局部规则引用与当前敏感词的完整直接事实/否认；复杂极性人工，不继承条目首谓语
// Pos: compliance/domain/policy/ - 否决/负偏离语境判定
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只豁免局部条件规则，不以全篇出现规则词为自身违规开脱。 */
final class RuleCitationContext {
    private static final Pattern RULE = Pattern.compile("评标委员会|评审委员会|应当否决|将否决|予以否决|作无效|无效投标|否决其投标|进行处罚|予以处罚|追究责任");
    private static final Pattern SCORING_CONDITION = Pattern.compile("(?:(?:不满足|不符合)(?:要求|上述要求|上述条件|(?:任意)?[0-9一二三四五六七八九十]+项)?(?:或未提供(?:证明文件|材料))?(?:者|的)?|未提供(?:证明文件|材料)(?:者|的)?|缺少(?:一项|任意一项))[,，]?(?:不得分|不予计分)");
    private static final Pattern CONDITION = Pattern.compile("(?:若|如|一旦|否则)|(?:有|存在|出现|发生).{0,90}(?:的|时)|投标人不得");
    private static final Pattern OWN_FACT = Pattern.compile("(?:我司|本公司|我方|本次投标|本项目实际).{0,50}(?:已|存在|采用|进行|发生|负偏离|不满足)|(?:经核实|实际情况)[：:]");

    private static final Pattern DENIED_LIST_HEADER = Pattern.compile("(?:我方|我司|本公司|本单位|西域)?[^。；;\\n]{0,12}承诺不存在(?:以下|下列)情形(?:之一)?[：:]");

    enum SensitiveContext { AFFIRMED, DENIED, REFERENCE, UNKNOWN }
    private static final String SELF = "(?:我方|我司|本公司|本单位|本次投标|本项目实际)";
    private static final Pattern DIRECT_FACT = Pattern.compile("(?:(?:实际情况|经核实)[：:])?(?:但|但是)?" + SELF
            + "(?:确实|实际)?(?:已(?:进行|参与|采用|发生|提交|与其他投标人)?|正在(?:进行|参与)?|采用|存在|拟(?:进行|采用)?)|本项目部分技术将进行");
    private static final Pattern DIRECT_DENIAL = Pattern.compile(
            "(?:" + SELF + "(?:承诺)?)?(?:承诺不|严禁|不得|不进行|不含|不存在|没有|无任何|杜绝|防范|禁止|坚决抵制|零|未|无|不)"
                    + "(?:进行|发生|参与|实施|采用)?(?:与|及|和|、)*$");
    private static final Pattern SELF_SUBJECT = Pattern.compile(SELF);

    private RuleCitationContext() { }

    static boolean isRuleCitation(final String text, final int start, final int end) {
        if (isDeniedListEntry(text, start) && !SELF_SUBJECT.matcher(localClause(text, start, end)).find()) return true;
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

    /** 只对当前命中之前的完整直接动作裁决；不跨分句继承主体或极性。 */
    static SensitiveContext sensitiveContext(String text, int start, int end, Pattern sensitiveWords) {
        int left = clauseStart(text, start);
        String prefix = text.substring(left, start).replaceAll("\\s+", "")
                .replaceFirst("^[0-9一二三四五六七八九十]+[.、）)]", "");
        var header = DENIED_LIST_HEADER.matcher(prefix);
        if (header.find()) prefix = prefix.substring(header.end()).replaceFirst("^[0-9一二三四五六七八九十]+[.、）)]", "");
        if (DIRECT_FACT.matcher(prefix).matches()) return SensitiveContext.AFFIRMED;
        String clause = localClause(text, start, end);
        // 主题标题后的自述不能反向证明该关键词的实际事实。
        if (SELF_SUBJECT.matcher(text.substring(end, left + clause.length())).find()) return SensitiveContext.UNKNOWN;
        if (DIRECT_DENIAL.matcher(sensitiveWords.matcher(prefix).replaceAll("")).matches()) return SensitiveContext.DENIED;
        if (SELF_SUBJECT.matcher(clause).find()) return SensitiveContext.UNKNOWN;
        return isRuleCitation(text, start, end) ? SensitiveContext.REFERENCE : SensitiveContext.UNKNOWN;
    }

    private static int clauseStart(String text, int start) {
        int left = start;
        while (left > 0 && "，,。；;！!？?\n".indexOf(text.charAt(left - 1)) < 0) left--;
        return left;
    }

    private static String localClause(String text, int start, int end) {
        int right = end;
        while (right < text.length() && "，,。；;！!？?\n".indexOf(text.charAt(right)) < 0) right++;
        return text.substring(clauseStart(text, start), right);
    }

    private static boolean isDeniedListEntry(String text, int start) {
        Matcher header = DENIED_LIST_HEADER.matcher(text);
        int end = -1;
        while (header.find() && header.end() <= start) end = header.end();
        if (end < 0) return false;
        String scope = text.substring(end, start);
        for (String line : scope.split("\\R")) {
            if (!line.isBlank() && !line.trim().matches("[0-9一二三四五六七八九十]+[.、）)].*")) return false;
        }
        return !scope.isBlank();
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
