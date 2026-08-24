// Input: 候选池 List<ScoreCandidate>（四路召回产物）
// Output: 合并去重后的候选池（保持首次出现顺序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-004

package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分项候选池合并去重策略（spec 041 FR-004）。
 * <p>规则：
 * <ul>
 *   <li>同维度同编号重复：合并为一条，以首次出现为基础，补齐后续召回的丰富信息
 *       （contextNote/semanticPattern/location 等非空字段）</li>
 *   <li>跨维度同编号（如商务表 1 vs 技术表 1）：互不包含真不同项，各自独立保留（防误吞）</li>
 *   <li>detail 例外：后续版本有原文背书（sourceText 包含 detail）且更完整时优先，
 *       保证跨行评分条款展示完整原文而非正则召回的残缺行内片段</li>
 *   <li>同编号不同维度补强：两路召回 dim 表述稍有出入（如正则"技术部分" vs LLM"技术方案"）
 *       但 base.detail 是 later.detail 的归一化子串且有原文背书时，按同项补强 detail</li>
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
        List<ScoreCandidate> merged = new ArrayList<>();
        for (ScoreCandidate candidate : candidates) {
            int matchIndex = findMatchingIndex(merged, candidate);
            if (matchIndex >= 0) {
                ScoreCandidate existing = merged.get(matchIndex);
                merged.set(matchIndex, enrich(existing, candidate));
            } else {
                merged.add(candidate);
            }
        }
        return merged;
    }

    private int findMatchingIndex(List<ScoreCandidate> list, ScoreCandidate target) {
        String targetCode = target.code() == null ? "" : target.code().trim();
        String targetDim = normalize(target.dim());
        String targetDetail = normalize(target.detail());

        for (int i = 0; i < list.size(); i++) {
            ScoreCandidate existing = list.get(i);
            String existingCode = existing.code() == null ? "" : existing.code().trim();
            String existingDim = normalize(existing.dim());
            String existingDetail = normalize(existing.detail());

            // 1. 编号非空且相同
            if (!targetCode.isEmpty() && targetCode.equalsIgnoreCase(existingCode)) {
                if (existingDim.equals(targetDim)) {
                    return i;
                }
                // 编号相同但维度表述稍有出入，如果是同一评分项的更完整原文召回，仍判定匹配
                if (isMoreCompleteRecallOfSameItem(existing, target)
                        || isMoreCompleteRecallOfSameItem(target, existing)) {
                    return i;
                }
                // 编号相同但维度不同、detail 互不包含的真不同项（如商务项 1 vs 技术项 1）：不匹配，各自保留
            }

            // 2. 编号至少一方缺失，但维度和细则高度一致
            if ((targetCode.isEmpty() || existingCode.isEmpty())
                    && !targetDetail.isEmpty() && targetDetail.equals(existingDetail)) {
                if (existingDim.equals(targetDim) || targetDim.isEmpty() || existingDim.isEmpty()) {
                    return i;
                }
            }
        }
        return -1;
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
            sourceText = later.sourceText();
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

    /**
     * 同编号不同名称的补强判定（防误并子串判据）：later 有原文背书、更完整，
     * 且 base 的 detail 归一化后是 later detail 的子串（残缺行内片段出自完整条款），
     * 判定为同一评分项的两路召回，允许补强。内容互不为子串时视为真不同项撞编号，
     * 维持"保留首次出现"的丢弃语义。
     */
    private boolean isMoreCompleteRecallOfSameItem(ScoreCandidate base, ScoreCandidate later) {
        return hasSourceBacking(later)
                && lengthOf(later.detail()) > lengthOf(base.detail())
                && normalize(later.detail()).contains(normalize(base.detail()));
    }

    private int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }
}
