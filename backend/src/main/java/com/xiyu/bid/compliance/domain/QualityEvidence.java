// Input: 证据来源类型/页码/原文摘录/知识库匹配字段
// Output: 标书质量核查证据模型（原件页码与来源文件身份）（knowledgeSource 四值白名单校验、KNOWLEDGE_BASE 类 recordId/matched 必填）
// Pos: compliance/domain/ - 核查证据模型（spec 049 US4 / T041，data-model.md §2.1 向后兼容扩展）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;

import java.io.Serializable;
import java.util.Set;

/**
 * 标书质量核查证据模型.
 * 记录问题或合规项溯源证据（招标文件条款、投标文件页码原文、知识库检索记录）.
 *
 * <p>spec 049 语义收口：BID/TENDER 类 pageNumber 为真实页码或 null（禁止填 1 充数），
 * quote 为真实原文摘录（禁止固定文案）；KNOWLEDGE_BASE 类 sourceRecordId 与 matched 必填，
 * knowledgeSource 限定四库枚举.
 */
public record QualityEvidence(
        String sourceType,
        String sourceTitle,
        Integer pageNumber,
        String quote,
        String sourceRecordId,
        Double confidence,
        String knowledgeSource,
        Boolean matched,
        String sourceFileName,
        String sourceFileHash
) implements Serializable {

    public QualityEvidence(String sourceType, String sourceTitle, Integer pageNumber, String quote,
            String sourceRecordId, Double confidence, String knowledgeSource, Boolean matched) {
        this(sourceType, sourceTitle, pageNumber, quote, sourceRecordId, confidence, knowledgeSource, matched, "", "");
    }

    /** knowledgeSource 允许的四库枚举值（data-model.md §2.1）. */
    private static final Set<String> KNOWLEDGE_SOURCES = Set.of(
            KnowledgeMatch.SOURCE_QUALIFICATION,
            KnowledgeMatch.SOURCE_BRAND_AUTHORIZATION,
            KnowledgeMatch.SOURCE_PERSONNEL,
            KnowledgeMatch.SOURCE_PERFORMANCE,
            KnowledgeMatch.SOURCE_CA_CERTIFICATE);

    public QualityEvidence {
        if (sourceType == null) {
            sourceType = "BID";
        }
        if (sourceTitle == null) {
            sourceTitle = "";
        }
        if (quote == null) {
            quote = "";
        }
        if (knowledgeSource != null && !KNOWLEDGE_SOURCES.contains(knowledgeSource)) {
            throw new IllegalArgumentException(
                    "QualityEvidence.knowledgeSource 仅允许四库枚举值或 null，实际: " + knowledgeSource);
        }
        if ("KNOWLEDGE_BASE".equals(sourceType)) {
            if (sourceRecordId == null || sourceRecordId.isBlank()) {
                throw new IllegalArgumentException("KNOWLEDGE_BASE 类证据 sourceRecordId 必填（真实记录 ID）");
            }
            if (matched == null) {
                throw new IllegalArgumentException("KNOWLEDGE_BASE 类证据 matched 必填（匹配结论）");
            }
        }
    }

    public static QualityEvidence tender(
            final String title, final Integer page, final String quote) {
        return new QualityEvidence("TENDER", title, page, quote, null, 1.0, null, null);
    }

    public static QualityEvidence bid(
            final String title, final Integer page, final String quote) {
        return new QualityEvidence("BID", title, page, quote, null, 1.0, null, null);
    }

    /**
     * 四知识库真实匹配记录 → 证据（spec 049 第三步，data-model.md §3.2 转换）.
     * sourceTitle 承载知识库记录标题语义，knowledgeSource 承载四库枚举.
     */
    public static QualityEvidence fromKnowledgeMatch(final KnowledgeMatch match) {
        return new QualityEvidence(
                "KNOWLEDGE_BASE", match.knowledgeSource(), null,
                match.content(), match.recordId(), match.matched() ? 1.0 : 0.0,
                match.knowledgeSource(), match.matched());
    }
}
