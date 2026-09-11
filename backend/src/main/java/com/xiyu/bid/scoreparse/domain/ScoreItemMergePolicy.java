// Input: 候选池 List<ScoreCandidate>（四路召回产物）
// Output: 合并去重后的候选池（保持首次出现顺序）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-004

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;

import java.math.BigDecimal;
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

    private static final java.util.regex.Pattern BRACKET_CONTENT = ScoreParseConstants.BRACKET_CONTENT_PATTERN;
    private static final java.util.regex.Pattern SECTION_HEADING = ScoreParseConstants.CHINESE_SECTION_HEADING;

    private int findMatchingIndex(List<ScoreCandidate> list, ScoreCandidate target) {
        String targetCode = target.code() == null ? "" : target.code().trim();
        String targetDim = normalize(target.dim());
        String targetCoreDim = normalizeCoreDim(target.dim());
        String targetDetail = normalize(target.detail());
        String puncTargetDetail = normalizePunctuation(target.detail());

        for (int i = 0; i < list.size(); i++) {
            ScoreCandidate existing = list.get(i);
            String existingCode = existing.code() == null ? "" : existing.code().trim();
            String existingDim = normalize(existing.dim());
            String existingCoreDim = normalizeCoreDim(existing.dim());
            String existingDetail = normalize(existing.detail());
            String puncExistingDetail = normalizePunctuation(existing.detail());

            // 0. 报价评分特判：同一报价维度（报价评分/价格评分）且 code 相同或均为报价相关
            if (isPriceDim(existingDim) && isPriceDim(targetDim)) {
                if (!targetCode.isEmpty() && targetCode.equalsIgnoreCase(existingCode)) return i;
                if ((targetCode.isEmpty() || existingCode.isEmpty())
                        && (existingDim.equals(targetDim) || (!existingCoreDim.isEmpty() && existingCoreDim.equals(targetCoreDim)))) {
                    return i;
                }
            }

            // 1. 细则高度一致（去空白/标点后相等）：两路召回同一条款，消重核心防线
            boolean codeCompatible = targetCode.isEmpty() || existingCode.isEmpty()
                    || targetCode.equalsIgnoreCase(existingCode);
            if (codeCompatible && !puncTargetDetail.isEmpty() && puncTargetDetail.equals(puncExistingDetail)) {
                if (puncTargetDetail.length() >= 15 || existingDim.equals(targetDim)
                        || (!existingCoreDim.isEmpty() && existingCoreDim.equals(targetCoreDim))
                        || (!existingCoreDim.isEmpty() && targetCoreDim.contains(existingCoreDim))
                        || (!targetCoreDim.isEmpty() && existingCoreDim.contains(targetCoreDim))
                        || existingDim.isEmpty() || targetDim.isEmpty()) {
                    return i;
                }
            }

            // 2. 编号非空且相同
            if (!targetCode.isEmpty() && targetCode.equalsIgnoreCase(existingCode)) {
                if (existingDim.equals(targetDim)
                        || (!existingCoreDim.isEmpty() && existingCoreDim.equals(targetCoreDim))) {
                    return i;
                }
                if (isMoreCompleteRecallOfSameItem(existing, target) || isMoreCompleteRecallOfSameItem(target, existing)) {
                    return i;
                }
            }

            // 3. 碎片子项包含消重（例如局部召回细则纯属另一完整大项的前缀或子集）
            boolean dimOverlap = !targetCoreDim.isEmpty() && !existingCoreDim.isEmpty()
                    && (targetCoreDim.equals(existingCoreDim) || targetCoreDim.contains(existingCoreDim) || existingCoreDim.contains(targetCoreDim));
            if (codeCompatible && dimOverlap && !targetDetail.isEmpty() && !existingDetail.isEmpty()) {
                String cleanTarget = normalize(stripLeadingIndex(target.detail()));
                String cleanExisting = normalize(stripLeadingIndex(existing.detail()));
                if (!cleanTarget.isEmpty() && !cleanExisting.isEmpty()) {
                    if (existingDetail.contains(cleanTarget) || targetDetail.contains(cleanExisting)
                            || cleanExisting.contains(cleanTarget) || cleanTarget.contains(cleanExisting)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static final java.util.regex.Pattern LEADING_INDEX =
            java.util.regex.Pattern.compile("^(?:[\\d一二三四五六七八九十]+[\\.、\\s\\)]+|[\\(（][\\d一二三四五六七八九十]+[\\)）]\\s*)");

    private static String stripLeadingIndex(String text) {
        if (text == null) return "";
        return LEADING_INDEX.matcher(text.trim()).replaceFirst("").trim();
    }

    private boolean isPriceDim(String normalizedDim) {
        return PriceScoringKeywords.PRICE_DIM_TOKENS.stream().anyMatch(normalizedDim::contains);
    }

    private String normalize(String dim) {
        return dim == null ? "" : dim.trim().replaceAll("\\s+", "");
    }

    private static String normalizePunctuation(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "");
    }

    private String normalizeCoreDim(String dim) {
        if (dim == null) return "";
        String stripped = BRACKET_CONTENT.matcher(dim).replaceAll("");
        return normalize(stripped);
    }

    /** 把 detail 按行拆开（trim、跳过空行）追加到去重集合，标点容错 */
    private static void appendLines(Set<String> sink, String detail) {
        if (detail == null) return;
        for (String line : detail.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String norm = normalizePunctuation(trimmed);
            if (sink.stream().noneMatch(s -> normalizePunctuation(s).equals(norm))) sink.add(trimmed);
        }
    }

    private static boolean isQualitativeScoring(ScoreCandidate c) {
        return c.dim() != null && (c.dim().contains("质量") || c.dim().contains("合理性") || c.dim().contains("方案"));
    }

    /** 以首次出现为基础，补齐后续召回的非空字段（更丰富信息优先）；报价类跨 chunk 细节拼合 */
    private ScoreCandidate enrich(ScoreCandidate base, ScoreCandidate later) {
        boolean baseIsStructured = isStructuredChannel(base);
        boolean laterIsStructured = isStructuredChannel(later);
        BigDecimal mergedWeight = resolveAuthoritativeWeight(base, later, baseIsStructured, laterIsStructured);
        String mergedDim = resolveAuthoritativeDim(base, later, baseIsStructured, laterIsStructured);

        // 报价类跨 chunk 互补：定性评审项（如报价质量）禁止跨 chunk 行级反复追加；
        // 结构化表格具有确定性权威，禁止与非结构化通道拼接污染；无原文背书候选禁止参与拼接
        boolean isPriceMerge = isPriceDim(normalize(base.dim())) && isPriceDim(normalize(later.dim()))
                && !isQualitativeScoring(base) && !isQualitativeScoring(later)
                && !(baseIsStructured ^ laterIsStructured)
                && hasSourceBacking(base) && hasSourceBacking(later)
                && base.detail() != null && later.detail() != null
                && !normalizePunctuation(base.detail()).equals(normalizePunctuation(later.detail()))
                && !normalizePunctuation(later.detail()).contains(normalizePunctuation(base.detail()))
                && !normalizePunctuation(base.detail()).contains(normalizePunctuation(later.detail()));
        String detail;
        String sourceText;
        if (isPriceMerge) {
            Set<String> seenLines = new LinkedHashSet<>();
            appendLines(seenLines, base.detail());
            appendLines(seenLines, later.detail());
            StringBuilder detailBuilder = new StringBuilder();
            for (String line : seenLines) {
                if (detailBuilder.length() + line.length() + 1 > PRICE_DETAIL_MAX_CHARS) break;
                if (detailBuilder.length() > 0) detailBuilder.append('\n');
                detailBuilder.append(line);
            }
            detail = detailBuilder.toString();
            String mergedSource = (base.sourceText() == null ? "" : base.sourceText())
                    + "\n" + (later.sourceText() == null ? "" : later.sourceText());
            sourceText = mergedSource.trim().isEmpty() ? base.sourceText() : mergedSource.trim();
        } else {
            boolean laterStructuredAuthority = laterIsStructured && !baseIsStructured;
            boolean laterHasBackingWhileBaseNot = hasSourceBacking(later) && !hasSourceBacking(base);
            boolean laterLongerWithBacking = (!baseIsStructured || laterIsStructured)
                    && hasSourceBacking(later)
                    && lengthOf(later.detail()) > lengthOf(base.detail());
            boolean preferLater = laterStructuredAuthority || laterHasBackingWhileBaseNot || laterLongerWithBacking;

            detail = preferLater ? (later.detail() != null ? later.detail() : base.detail())
                    : (base.detail() != null ? base.detail() : later.detail());
            if (preferLater) {
                sourceText = (later.sourceText() != null && !later.sourceText().isBlank())
                        ? later.sourceText() : base.sourceText();
            } else {
                sourceText = (base.sourceText() != null && !base.sourceText().isBlank())
                        ? base.sourceText() : later.sourceText();
            }
        }
        String mergedParentDim = base.parentDim() != null && !base.parentDim().isBlank()
                ? base.parentDim() : later.parentDim();
        String mergedDimSource = base.dimSource() != null && !"UNKNOWN".equals(base.dimSource())
                ? base.dimSource() : later.dimSource();
        String mergedCode = (laterIsStructured && !baseIsStructured && later.code() != null && !later.code().isBlank())
                ? later.code()
                : (base.code() != null && !base.code().isBlank() ? base.code() : later.code());
        String mergedPattern = (baseIsStructured || laterIsStructured)
                ? "STRUCTURED_TABLE"
                : (later.semanticPattern() != null ? later.semanticPattern() : base.semanticPattern());
        return new ScoreCandidate(mergedCode, mergedDim,
                detail, mergedWeight, base.scoreTypeGuess() != null ? base.scoreTypeGuess() : later.scoreTypeGuess(),
                later.contextNote() != null ? later.contextNote() : base.contextNote(),
                sourceText, later.location() != null ? later.location() : base.location(),
                mergedPattern,
                mergedParentDim, mergedDimSource);
    }

    private static boolean isStructuredChannel(ScoreCandidate c) {
        return "STRUCTURED_TABLE".equals(c.semanticPattern());
    }

    private static BigDecimal resolveAuthoritativeWeight(ScoreCandidate base, ScoreCandidate later,
            boolean baseIsStructured, boolean laterIsStructured) {
        if (baseIsStructured && base.weight() != null) return base.weight();
        if (laterIsStructured && later.weight() != null) return later.weight();
        return base.weight() != null ? base.weight() : later.weight();
    }

    private static String resolveAuthoritativeDim(ScoreCandidate base, ScoreCandidate later,
            boolean baseIsStructured, boolean laterIsStructured) {
        String baseDim = base.dim();
        String laterDim = later.dim();
        if (baseDim == null) return laterDim;
        if (laterDim == null) return baseDim;
        boolean baseIsSection = SECTION_HEADING.matcher(baseDim).matches();
        boolean laterIsSection = SECTION_HEADING.matcher(laterDim).matches();
        if (baseIsSection && !laterIsSection) return laterDim;
        if (laterIsSection && !baseIsSection) return baseDim;
        if (baseDim.length() > 25 && laterDim.length() <= 25) return laterDim;
        if (laterDim.length() > 25 && baseDim.length() <= 25) return baseDim;
        if (baseIsStructured) return baseDim;
        if (laterIsStructured) return laterDim;
        return baseDim;
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
        if (!hasSourceBacking(later)) return false;
        String baseDetail = normalize(base.detail());
        String laterDetail = normalize(later.detail());
        if (baseDetail.equals(laterDetail)) {
            String baseCore = normalizeCoreDim(base.dim());
            String laterCore = normalizeCoreDim(later.dim());
            return baseCore.equals(laterCore) || baseCore.contains(laterCore) || laterCore.contains(baseCore);
        }
        return lengthOf(later.detail()) > lengthOf(base.detail())
                && laterDetail.contains(baseDetail);
    }

    private int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }
}
