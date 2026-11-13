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

    private ScoreMaximumPolicy() {}

    /** 首个满分声明；无声明返回 null。 */
    public static BigDecimal declared(String text) {
        if (text == null) return null;
        Matcher matcher = DECLARED.matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    /** 整项满分：仅在满分声明唯一且不小于其余分值时返回；多个声明即使同值也可能是独立子项上限。 */
    public static BigDecimal overall(String text) {
        if (text == null) return null;
        Matcher declared = DECLARED.matcher(text);
        BigDecimal maximum = null;
        while (declared.find()) {
            if (maximum != null) return null;
            maximum = new BigDecimal(declared.group(1));
        }
        if (maximum == null) return null;
        Matcher scores = MENTION.matcher(text);
        while (scores.find()) {
            if (new BigDecimal(scores.group(1)).compareTo(maximum) > 0) return null;
        }
        return maximum;
    }
}
