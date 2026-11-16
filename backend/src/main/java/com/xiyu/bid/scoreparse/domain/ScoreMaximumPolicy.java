package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则文本分值上限解析（最高/最多/满分 X 分）。
 *
 * <p>子项上限（如「每多一个仓库满分5分」）不能当成整项满分，因此区分单次声明与整项声明。
 */
public final class ScoreMaximumPolicy {

    private static final Pattern DECLARED = Pattern.compile(
            "(?:最高|最多|满分)[^。；;\\n]{0,8}?([0-9]+(?:\\.[0-9]+)?)\\s*分");
    private static final Pattern MENTION = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*分");
    private static final Pattern FIXED_AWARD = Pattern.compile("得\\s*([0-9]+(?:\\.[0-9]+)?)\\s*分");
    private static final Pattern NUMBERED_CLAUSE = Pattern.compile(
            "(?:^|[。；;\\n])\\s*[（(]?[0-9一二三四五六七八九十]+[）).、]");

    private ScoreMaximumPolicy() {}

    /** 首个满分声明；无声明返回 null。 */
    public static BigDecimal declared(String text) {
        if (text == null) return null;
        Matcher matcher = DECLARED.matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    /** 整项满分：排除多个编号子项，仅在声明唯一且不小于其余分值时返回。 */
    public static BigDecimal overall(String text) {
        if (text == null) return null;
        Matcher clauses = NUMBERED_CLAUSE.matcher(text);
        if (clauses.find() && clauses.find()) return null;
        Matcher declared = DECLARED.matcher(text);
        if (!declared.find()) return null;
        BigDecimal maximum = new BigDecimal(declared.group(1));
        if (declared.find()) return null;
        Matcher scores = MENTION.matcher(text);
        while (scores.find()) {
            if (new BigDecimal(scores.group(1)).compareTo(maximum) > 0) return null;
        }
        return maximum;
    }

    /**
     * 单次固定得分：规则未声明最高/最多/满分，且全文只有一个「得X分」分值时，该分值即本项上限。
     *
     * <p>逐项累计规则（含「每」）和多子项规则不在此列，仍按表格权重计分。
     */
    public static BigDecimal fixedAward(String text) {
        if (text == null || text.contains("每")) return null;
        if (DECLARED.matcher(text).find()) return null;
        Matcher award = FIXED_AWARD.matcher(text);
        if (!award.find()) return null;
        Matcher mention = MENTION.matcher(text);
        if (!mention.find() || mention.find()) return null;
        return new BigDecimal(award.group(1));
    }
}
