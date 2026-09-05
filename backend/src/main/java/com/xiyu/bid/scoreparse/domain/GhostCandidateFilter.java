// Input: 合并去重后的评分候选池
// Output: 幽灵候选滤除结果（新列表，保留原序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-001 / FR-006
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;

/**
 * 幽灵候选过滤（spec 046 FR-001 / spec 058 程序性条款防线）。
 * <p>规则 1：dim 与 detail 归一化（trim + 去全部空白字符）相同且长度 ≤4 字
 * （表头列名回声特征，如"得分"）→ 滤除。
 * <p>规则 2：code 空白 且 sourceText 空白（无编号无条款文本 = 非条款行）→ 滤除；
 * 有任一即保留（真实项保护）。
 * <p>规则 3：匹配非评分程序性条款（如"推荐成交候选供应商"、"定标"、"履约保证金"等）→ 滤除。
 * <p>保留原序、不修改入参；被滤项由调用方记日志（FR-006）。
 */
public class GhostCandidateFilter {

    private static final Pattern NON_SCORE_PROCEDURE_PATTERN = ScoreParseConstants.NON_SCORE_PROCEDURE_PATTERN;

    /** 过滤幽灵候选，返回新列表；入参为 null 时返回空列表 */
    public List<ScoreCandidate> filter(List<ScoreCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        List<ScoreCandidate> kept = new ArrayList<>(candidates.size());
        for (ScoreCandidate candidate : candidates) {
            if (!isGhost(candidate)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private boolean isGhost(ScoreCandidate candidate) {
        String dim = normalize(candidate.dim());
        if (dim.equals(normalize(candidate.detail())) && dim.length() <= 4) {
            return true;
        }
        if (isBlank(candidate.code()) && isBlank(candidate.sourceText())) {
            return true;
        }
        // 规则 3：非评分程序性条款（如 4.4.1 推荐成交候选供应商）
        // 注：4. 编号规则优先于一切救援（含信用救援与显式规则），既有行为，刻意保留。
        if (candidate.code() != null && candidate.code().trim().startsWith("4.")) {
            return true;
        }
        String detail = candidate.detail() == null ? "" : candidate.detail();
        String source = candidate.sourceText() == null ? "" : candidate.sourceText();
        boolean matchesProcedural = NON_SCORE_PROCEDURE_PATTERN.matcher(dim).find()
                || NON_SCORE_PROCEDURE_PATTERN.matcher(detail).find()
                || NON_SCORE_PROCEDURE_PATTERN.matcher(source).find();
        if (matchesProcedural) {
            boolean creditRescue = DeductionItemPolicy.isCreditRescue(dim, detail, source);
            boolean hasExplicitRule = creditRescue
                    || DeductionItemPolicy.hasExplicitScoreRule(detail, dim)
                    || ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(dim).find()
                    || ScoreParseConstants.BRACKET_WEIGHT_PATTERN.matcher(detail).find();
            if (!hasExplicitRule) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
