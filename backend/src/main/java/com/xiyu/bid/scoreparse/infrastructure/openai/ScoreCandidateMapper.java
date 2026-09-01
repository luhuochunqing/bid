// Input: ScoringCriterion（正则召回）/ ScoreCandidateOutput.Candidate（LLM 召回）/ 已提取项清单
// Output: ScoreCandidate 域对象 / 已知项摘要文本（供回补 prompt）
// Pos: scoreparse/infrastructure/openai — 召回候选映射纯函数簇
// 维护声明: 维护者按项目SOP；2026-08-19 自 OpenAiScoreAnalyzer 拆出以满足 300 行预算（SRP：LLM 编排与 DTO 映射分离）
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 召回候选映射：外部提取器/LLM 输出 → {@link ScoreCandidate} 域对象。
 * 无业务状态；仅观测日志（对齐失败 WARN），不改变映射行为。
 */
@Slf4j
final class ScoreCandidateMapper {

    private static final int SOURCE_TEXT_MAX_CHARS = 500;
    private static final int LOCATION_MAX_CHARS = 200;
    private static final int CODE_MAX_CHARS = 50;
    private static final int DIM_MAX_CHARS = 200;

    private ScoreCandidateMapper() {
    }

    /** 正则/结构召回候选映射；section 为 null 时章节相关字段降级为空串（2026-08-17 项目 226 NPE 回归） */
    static ScoreCandidate fromCriterion(ScoringCriterion criterion,
                                        MarkdownScoreSectionLocator.ScoreSection section) {
        String detail = criterion.indicator() != null && !criterion.indicator().isBlank()
                ? criterion.indicator() : criterion.dimension();
        boolean hasSection = section != null;
        return new ScoreCandidate(
                truncate(safeText(criterion.itemNumber()), CODE_MAX_CHARS),
                truncate(safeText(criterion.dimension()), DIM_MAX_CHARS),
                detail,
                criterion.weight(),
                null,
                hasSection ? section.sectionTitle() : "",
                hasSection ? safeText(section.content()) : "",
                hasSection ? truncate(section.location(), LOCATION_MAX_CHARS) : "",
                "RECALL_REGEX_STRUCTURE"
        );
    }

    /**
     * LLM 召回候选映射（spec 041 FR-001 召回四输出）。
     *
     * <p>原文保真：detail/dim/sourceText 依次在来源原文上做空白弹性对齐，
     * 命中则回填为原文精确片段（逐字一致，消除模型复述时的空白/断词漂移）；
     * detail 对齐成功时 sourceText 取同一片段，保证 sourceText 包含 detail
     * （原文背书关系，供 {@code ScoreItemMergePolicy} 识别更完整版本）。
     * detail 对齐失败（模型概括改写）记 WARN 保留 LLM 输出（可观测降级），
     * <b>sourceText 置空</b>——LLM 复述的 sourceText 自包含 detail 会让
     * 背书判据对幻觉文本天然成立（伪背书），source_text 只允许携带原文验证过的内容。
     *
     * @param sourceText 候选来源原文（chunk 文本 / 回补节选），可为 null（降级为纯 LLM 输出）
     */
    static ScoreCandidate fromLlmCandidate(ScoreCandidateOutput.Candidate candidate, String sourceText) {
        String original = sourceText == null ? "" : sourceText;
        String llmDim = safeText(candidate.dim);
        String llmDetail = candidate.detail == null ? "" : candidate.detail.trim();
        String llmSource = truncate(safeText(candidate.sourceText));

        String alignedDetail = ScoreOriginalTextAligner.align(original, llmDetail);
        String alignedDim = ScoreOriginalTextAligner.align(original, llmDim);
        String alignedSource = alignedDetail != null ? alignedDetail
                : ScoreOriginalTextAligner.align(original, llmSource);
        if (alignedDetail == null && !original.isBlank()) {
            log.warn("评分候选 detail 原文对齐失败（保留 LLM 输出）: code={}, dim={}, detail={}",
                    safeText(candidate.code), llmDim, head(llmDetail));
        }
        return new ScoreCandidate(
                truncate(safeText(candidate.code), CODE_MAX_CHARS),
                truncate(alignedDim != null ? alignedDim : llmDim, DIM_MAX_CHARS),
                alignedDetail != null ? alignedDetail : llmDetail,
                candidate.weight == null ? null : java.math.BigDecimal.valueOf(candidate.weight),
                safeText(candidate.scoreTypeGuess),
                safeText(candidate.contextNote),
                alignedSource != null ? alignedSource : "",
                truncate(safeText(candidate.location), LOCATION_MAX_CHARS),
                safeText(candidate.semanticPattern)
        );
    }

    /** 日志用 detail 头部截断（60 字符）。 */
    private static String head(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 60 ? text : text.substring(0, 60) + "…";
    }

    /** 已提取项摘要（完整性回补 prompt 输入，含分值对账与缺额诊断，FR-005） */
    static String summarizeKnownItems(List<ScoreCandidate> knownItems) {
        if (knownItems == null || knownItems.isEmpty()) {
            return "（无已提取项，当前合计 0 分，缺 100 分）";
        }
        StringBuilder sb = new StringBuilder();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (ScoreCandidate item : knownItems) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("- ")
                    .append(safeText(item.code())).append(' ')
                    .append(safeText(item.dim())).append(": ")
                    .append(item.weight() == null ? "?" : item.weight()).append("分");
            if (item.weight() != null) {
                total = total.add(item.weight());
            }
        }
        sb.append("\n【已提取项分值合计】: ").append(total).append(" 分");
        java.math.BigDecimal diff = java.math.BigDecimal.valueOf(100).subtract(total);
        if (diff.compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append("（距离 100 分满分仍缺 ").append(diff).append(" 分，请重点复查缺少该分值的并列评分项/方案能力项）");
        }
        return sb.toString();
    }

    static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static String truncate(String text) {
        return truncate(text, SOURCE_TEXT_MAX_CHARS);
    }

    static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /**
     * 对齐成功数统计：detail 为 chunk 原文的精确子串（对齐回填后必成立；LLM 原样输出因
     * 空白漂移通常不成立）。自 Analyzer 迁入以守 line-budget（spec 057）。
     */
    public static int countAligned(java.util.List<ScoreCandidate> chunkCandidates, String chunkText) {
        return (int) chunkCandidates.stream()
                .filter(candidate -> chunkText.contains(candidate.detail()))
                .count();
    }
}
