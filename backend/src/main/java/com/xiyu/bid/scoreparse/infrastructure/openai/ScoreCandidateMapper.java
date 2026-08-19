// Input: ScoringCriterion（正则召回）/ ScoreCandidateOutput.Candidate（LLM 召回）/ 已提取项清单
// Output: ScoreCandidate 域对象 / 已知项摘要文本（供回补 prompt）
// Pos: scoreparse/infrastructure/openai — 召回候选映射纯函数簇
// 维护声明: 维护者按项目SOP；2026-08-19 自 OpenAiScoreAnalyzer 拆出以满足 300 行预算（SRP：LLM 编排与 DTO 映射分离）
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;

import java.util.List;

/** 召回候选映射：外部提取器/LLM 输出 → {@link ScoreCandidate} 域对象（纯函数，无状态）。 */
final class ScoreCandidateMapper {

    private static final int SOURCE_TEXT_MAX_CHARS = 500;

    private ScoreCandidateMapper() {
    }

    /** 正则/结构召回候选映射；section 为 null 时章节相关字段降级为空串（2026-08-17 项目 226 NPE 回归） */
    static ScoreCandidate fromCriterion(ScoringCriterion criterion,
                                        MarkdownScoreSectionLocator.ScoreSection section) {
        String detail = criterion.indicator() != null && !criterion.indicator().isBlank()
                ? criterion.indicator() : criterion.dimension();
        boolean hasSection = section != null;
        return new ScoreCandidate(
                safeText(criterion.itemNumber()),
                safeText(criterion.dimension()),
                detail,
                criterion.weight(),
                null,
                hasSection ? section.sectionTitle() : "",
                hasSection ? truncate(section.content()) : "",
                hasSection ? section.location() : "",
                "RECALL_REGEX_STRUCTURE"
        );
    }

    /** LLM 召回候选映射（spec 041 FR-001 召回四输出） */
    static ScoreCandidate fromLlmCandidate(ScoreCandidateOutput.Candidate candidate) {
        return new ScoreCandidate(
                safeText(candidate.code),
                safeText(candidate.dim),
                candidate.detail,
                candidate.weight == null ? null : java.math.BigDecimal.valueOf(candidate.weight),
                safeText(candidate.scoreTypeGuess),
                safeText(candidate.contextNote),
                truncate(safeText(candidate.sourceText)),
                safeText(candidate.location),
                safeText(candidate.semanticPattern)
        );
    }

    /** 已提取项摘要（完整性回补 prompt 输入，FR-005） */
    static String summarizeKnownItems(List<ScoreCandidate> knownItems) {
        if (knownItems == null || knownItems.isEmpty()) {
            return "（无已提取项）";
        }
        StringBuilder sb = new StringBuilder();
        for (ScoreCandidate item : knownItems) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(safeText(item.code())).append(' ')
                    .append(safeText(item.dim())).append(' ')
                    .append(item.weight() == null ? "?" : item.weight()).append("分");
        }
        return sb.toString();
    }

    static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SOURCE_TEXT_MAX_CHARS ? text : text.substring(0, SOURCE_TEXT_MAX_CHARS);
    }
}
