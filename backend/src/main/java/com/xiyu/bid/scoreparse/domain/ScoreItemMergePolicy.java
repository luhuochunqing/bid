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
 *   <li>detail 例外：后续版本有原文背书（sourceText 包含 detail）且更完整时优先，
 *       保证跨行评分条款展示完整原文而非正则召回的残缺行内片段</li>
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
        boolean laterDetailPreferred = hasSourceBacking(later)
                && lengthOf(later.detail()) > lengthOf(base.detail());
        String detail;
        String sourceText;
        if (laterDetailPreferred) {
            detail = later.detail();
            sourceText = later.sourceText() != null ? later.sourceText() : base.sourceText();
        } else {
            detail = base.detail() != null ? base.detail() : later.detail();
            sourceText = base.sourceText() != null ? base.sourceText() : later.sourceText();
        }
        return new ScoreCandidate(
                base.code() != null ? base.code() : later.code(),
                base.dim() != null ? base.dim() : later.dim(),
                detail,
                base.weight() != null ? base.weight() : later.weight(),
                base.scoreTypeGuess() != null ? base.scoreTypeGuess() : later.scoreTypeGuess(),
                later.contextNote() != null ? later.contextNote() : base.contextNote(),
                sourceText,
                later.location() != null ? later.location() : base.location(),
                later.semanticPattern() != null ? later.semanticPattern() : base.semanticPattern()
        );
    }

    /**
     * 原文背书判定：detail 是 sourceText 的子串（原文对齐回填后成立的包含关系）。
     * <p>正则召回的行内片段天然残缺（跨行单元格被拆碎），LLM 召回经
     * {@code ScoreOriginalTextAligner} 对齐后是完整原文片段；仅当 later 版本有原文背书
     * 且更完整时才允许覆盖首次出现版本，防止无背书的 LLM 幻觉长文本污染。
     */
    private boolean hasSourceBacking(ScoreCandidate candidate) {
        String detail = candidate.detail();
        String sourceText = candidate.sourceText();
        return detail != null && !detail.isBlank()
                && sourceText != null && !sourceText.isBlank()
                && sourceText.contains(detail);
    }

    private int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }
}
