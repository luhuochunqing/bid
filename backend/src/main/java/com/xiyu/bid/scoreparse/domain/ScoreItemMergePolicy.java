// Input: 候选池 List<ScoreCandidate>（四路召回产物）
// Output: 合并去重后的候选池（保持首次出现顺序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-004

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 评分项候选池合并去重策略（spec 041 FR-004）。
 * <p>规则：
 * <ul>
 *   <li>同维度同编号重复：合并为一条，以首次出现为基础，补齐后续召回的丰富信息
 *       （contextNote/semanticPattern/location 等非空字段）</li>
 *   <li>编号相同但维度表述稍有出入（如"商务部分" vs "商务"）：如果是同一评分项的更完整原文召回，合并为一条</li>
 *   <li>编号相同但维度不同、detail 互不包含的真不同项（如商务项 1 vs 技术项 1）：不合并，各自保留</li>
 *   <li>无编号但细则高度一致（去空白后相等）：合并为一条</li>
 *   <li>报价维度跨 chunk 拆分（前半价格分方法 + 后半参数）：同属报价项时拼合 detail，避免截断</li>
 *   <li>合并结果保持首次出现的相对顺序</li>
 * </ul>
 */
public class ScoreItemMergePolicy {

    /** 报价项跨 chunk 拼合后 detail 长度上界（字符）：超出部分丢弃，防无界增长 */
    static final int PRICE_DETAIL_MAX_CHARS = 6000;

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

            // 0. 报价评分特判：同一报价维度（报价评分/价格评分）且 code 相同或均为报价相关，
            // 跨 chunk 拆分的细节互补（非子串）应视为同一项，避免价格分只保留前 3 行后截断
            if (isPriceDim(existingDim) && isPriceDim(targetDim)) {
                if (!targetCode.isEmpty() && targetCode.equalsIgnoreCase(existingCode)) {
                    return i;
                }
                if (targetCode.isEmpty() || existingCode.isEmpty()) {
                    // 无编号报价项，按维度相同即视为同一项（跨 chunk 合并）
                    if (existingDim.equals(targetDim)) {
                        return i;
                    }
                }
            }

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

    private boolean isPriceDim(String normalizedDim) {
        // 词元收敛于 PriceScoringKeywords.PRICE_DIM_TOKENS（单一真相源，防各处硬编码漂移）
        return PriceScoringKeywords.PRICE_DIM_TOKENS.stream().anyMatch(normalizedDim::contains);
    }

    private String normalize(String dim) {
        return dim == null ? "" : dim.trim().replaceAll("\\s+", "");
    }

    /** 把 detail 按行拆开（trim、跳过空行）追加到去重集合 */
    private static void appendLines(Set<String> sink, String detail) {
        if (detail == null) {
            return;
        }
        for (String line : detail.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                sink.add(trimmed);
            }
        }
    }

    /** 以首次出现为基础，补齐后续召回的非空字段（更丰富信息优先）；报价类跨 chunk 细节拼合 */
    private ScoreCandidate enrich(ScoreCandidate base, ScoreCandidate later) {
        // 报价类跨 chunk 互补：两段 detail 非子串但同属报价项，需拼合而非择一
        boolean isPriceMerge = isPriceDim(normalize(base.dim())) && isPriceDim(normalize(later.dim()))
                && base.detail() != null && later.detail() != null
                && !normalize(base.detail()).equals(normalize(later.detail()))
                && !normalize(later.detail()).contains(normalize(base.detail()))
                && !normalize(base.detail()).contains(normalize(later.detail()));
        if (isPriceMerge) {
            // 按行去重拼接：chunk 滑窗重叠召回的重复行不重复进入 detail（行级 trim 精确匹配）
            Set<String> seenLines = new LinkedHashSet<>();
            appendLines(seenLines, base.detail());
            appendLines(seenLines, later.detail());
            // 长度上界：多 chunk 反复拼合不得无界增长（典型报价参数表远低于此限）
            StringBuilder detailBuilder = new StringBuilder();
            for (String line : seenLines) {
                if (detailBuilder.length() + line.length() + 1 > PRICE_DETAIL_MAX_CHARS) {
                    break;
                }
                if (detailBuilder.length() > 0) {
                    detailBuilder.append('\n');
                }
                detailBuilder.append(line);
            }
            String mergedDetail = detailBuilder.toString();
            String mergedSource = (base.sourceText() == null ? "" : base.sourceText())
                    + "\n" + (later.sourceText() == null ? "" : later.sourceText());
            return new ScoreCandidate(
                    base.code() != null ? base.code() : later.code(),
                    base.dim() != null ? base.dim() : later.dim(),
                    mergedDetail,
                    base.weight() != null ? base.weight() : later.weight(),
                    base.scoreTypeGuess() != null ? base.scoreTypeGuess() : later.scoreTypeGuess(),
                    later.contextNote() != null ? later.contextNote() : base.contextNote(),
                    mergedSource.trim().isEmpty() ? base.sourceText() : mergedSource.trim(),
                    later.location() != null ? later.location() : base.location(),
                    later.semanticPattern() != null ? later.semanticPattern() : base.semanticPattern()
            );
        }
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
