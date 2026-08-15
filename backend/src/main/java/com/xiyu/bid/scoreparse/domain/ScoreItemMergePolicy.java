// Input: 候选池 List<ScoreCandidate>（四路召回产物）
// Output: 合并去重后的候选池（保持首次出现顺序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-004

package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分项候选池合并去重策略（spec 041 FR-004）。
 * <p>规则：
 * <ul>
 *   <li>编号重复：保留首次出现（编号是原文锚点，重复编号视为同项）</li>
 *   <li>编号 + 名称均相同：合并为一条，以首次出现为基础，补齐后续召回的丰富信息
 *       （contextNote/semanticPattern/location 等非空字段）</li>
 *   <li>名称相同但编号不同：语义相似不误删，两条均保留（交由后续结构化提取甄别）</li>
 *   <li>weight 缺失的候选保留，由 Validation 层丢弃并记日志</li>
 * </ul>
 */
public class ScoreItemMergePolicy {

    /** 合并候选池 */
    public List<ScoreCandidate> merge(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, ScoreCandidate> merged = new LinkedHashMap<>();
        for (ScoreCandidate candidate : candidates) {
            String key = mergeKey(candidate);
            ScoreCandidate existing = merged.get(key);
            if (existing == null) {
                merged.put(key, candidate);
            } else if (sameDim(existing, candidate)) {
                merged.put(key, enrich(existing, candidate));
            }
            // 编号重复但名称不同：保留首次出现（不覆盖）
        }
        return new ArrayList<>(merged.values());
    }

    /** 合并键：编号（trim + 大写）；编号缺失时用全文指纹保证不误并 */
    private String mergeKey(ScoreCandidate candidate) {
        String code = candidate.code() == null ? "" : candidate.code().trim();
        if (!code.isEmpty()) {
            return code.toUpperCase();
        }
        // 无编号候选：按 dim+detail 指纹独立保留
        return "\u0000" + candidate.dim() + "|" + candidate.detail();
    }

    private boolean sameDim(ScoreCandidate a, ScoreCandidate b) {
        return normalize(a.dim()).equals(normalize(b.dim()));
    }

    private String normalize(String dim) {
        return dim == null ? "" : dim.trim().replaceAll("\\s+", "");
    }

    /** 以首次出现为基础，补齐后续召回的非空字段（更丰富信息优先） */
    private ScoreCandidate enrich(ScoreCandidate base, ScoreCandidate later) {
        return new ScoreCandidate(
                base.code() != null ? base.code() : later.code(),
                base.dim() != null ? base.dim() : later.dim(),
                base.detail() != null ? base.detail() : later.detail(),
                base.weight() != null ? base.weight() : later.weight(),
                base.scoreTypeGuess() != null ? base.scoreTypeGuess() : later.scoreTypeGuess(),
                later.contextNote() != null ? later.contextNote() : base.contextNote(),
                base.sourceText() != null ? base.sourceText() : later.sourceText(),
                later.location() != null ? later.location() : base.location(),
                later.semanticPattern() != null ? later.semanticPattern() : base.semanticPattern()
        );
    }
}
