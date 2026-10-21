// Input: 评分项编号原文（"4" / "4-1" / "4.1" / "2.2.4(1)"）
// Output: 可比较的编号键（主段 + 子段）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-003
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项编号解析与比较（spec 046 FR-003）。
 * <p>支持简单编号 {@code 4 / 4-1 / 4.1}（分隔符 -、—、. 等价）与复合编号
 * {@code 2.2.4(1) / 3.1（2）}（取首段整数 + 括号内子段）；不可解析编号返回 null。
 *
 * @param major 主段数值
 * @param minor 子段数值（无子段为 -1，排序时视同纯数值最小）
 */
public record ScoreCodeKey(int major, int minor) {

    private static final Pattern SIMPLE_CODE_PATTERN = Pattern.compile("^(\\d+)(?:[-—.](\\d+))?$");
    private static final Pattern COMPOUND_CODE_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s*[（(](\\d+)[）)]$");

    /** 解析编号原文；null、空白或不可解析时返回 null */
    public static ScoreCodeKey parse(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        try {
            Matcher simple = SIMPLE_CODE_PATTERN.matcher(trimmed);
            if (simple.matches()) {
                int minor = simple.group(2) == null ? -1 : Integer.parseInt(simple.group(2));
                return new ScoreCodeKey(Integer.parseInt(simple.group(1)), minor);
            }
            Matcher compound = COMPOUND_CODE_PATTERN.matcher(trimmed);
            if (compound.matches()) {
                int major = Integer.parseInt(compound.group(1).split("\\.")[0]);
                return new ScoreCodeKey(major, Integer.parseInt(compound.group(2)));
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    /** 编号升序比较；null 视为不可解析，排在可解析项之后 */
    public static int compare(ScoreCodeKey a, ScoreCodeKey b) {
        if (a != null && b != null) {
            int byMajor = Integer.compare(a.major(), b.major());
            return byMajor != 0 ? byMajor : Integer.compare(a.minor(), b.minor());
        }
        return a != null ? -1 : (b != null ? 1 : 0);
    }
}
