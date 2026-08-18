// Input: 合并去重后的评分候选池
// Output: 幽灵候选滤除结果（新列表，保留原序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-001 / FR-006
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 幽灵候选过滤（spec 046 FR-001）。
 * <p>规则 1：dim 与 detail 归一化（trim + 去全部空白字符）相同且长度 ≤4 字
 * （表头列名回声特征，如"得分"）→ 滤除。
 * <p>规则 2：code 空白 且 sourceText 空白（无编号无条款文本 = 非条款行）→ 滤除；
 * 有任一即保留（真实项保护）。
 * <p>保留原序、不修改入参；被滤项由调用方记日志（FR-006）。
 */
public class GhostCandidateFilter {

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
        return isBlank(candidate.code()) && isBlank(candidate.sourceText());
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
