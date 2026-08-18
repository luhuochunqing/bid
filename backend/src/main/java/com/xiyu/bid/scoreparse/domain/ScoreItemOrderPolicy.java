// Input: 候选池（code 为评分项编号原文）
// Output: 编号数值序重排结果（不可解析编码稳定置后）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-003 / FR-004
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项编号排序策略（spec 046 FR-003）。
 * <p>解析键 ^(\d+)(?:[-—.](\d+))?$（如 "4-1" → 主段 4、子段 1）：主段升序、
 * 子段升序（无子段排前，minor 取 -1 视同最小）；不可解析编码（A1/B2/null/空白）
 * 稳定保持相对序排在可解析之后。不改 code 内容，仅重排顺序；itemIndex 由调用方按新序重编。
 */
public class ScoreItemOrderPolicy {

    private static final Pattern CODE_PATTERN = Pattern.compile("^(\\d+)(?:[-—.](\\d+))?$");

    /** 按编号数值序排序，返回新列表；入参为 null 时返回空列表 */
    public List<ScoreCandidate> sort(List<ScoreCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        List<ScoreCandidate> parsed = new ArrayList<>();
        List<ScoreCandidate> unparsed = new ArrayList<>();
        for (ScoreCandidate candidate : candidates) {
            if (parseKey(candidate.code()) == null) {
                unparsed.add(candidate);
            } else {
                parsed.add(candidate);
            }
        }
        parsed.sort((a, b) -> {
            CodeKey keyA = parseKey(a.code());
            CodeKey keyB = parseKey(b.code());
            int byMajor = Integer.compare(keyA.major(), keyB.major());
            return byMajor != 0 ? byMajor : Integer.compare(keyA.minor(), keyB.minor());
        });
        List<ScoreCandidate> ordered = new ArrayList<>(candidates.size());
        ordered.addAll(parsed);
        ordered.addAll(unparsed);
        return ordered;
    }

    private CodeKey parseKey(String code) {
        if (code == null) {
            return null;
        }
        Matcher matcher = CODE_PATTERN.matcher(code.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2));
            return new CodeKey(major, minor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param major 主段数值
     * @param minor 子段数值（无子段为 -1，排序时视同最小）
     */
    private record CodeKey(int major, int minor) {
    }
}
