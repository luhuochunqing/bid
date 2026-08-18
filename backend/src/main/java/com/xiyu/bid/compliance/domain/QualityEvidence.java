package com.xiyu.bid.compliance.domain;

import java.io.Serializable;

/**
 * 标书质量核查证据模型.
 * 记录问题或合规项溯源证据（招标文件条款、投标文件页码原文、知识库检索记录）.
 */
public record QualityEvidence(
        String sourceType,
        String sourceTitle,
        Integer pageNumber,
        String quote,
        String sourceRecordId,
        Double confidence
) implements Serializable {

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
    }

    public static QualityEvidence tender(
            final String title, final Integer page, final String quote) {
        return new QualityEvidence("TENDER", title, page, quote, null, 1.0);
    }

    public static QualityEvidence bid(
            final String title, final Integer page, final String quote) {
        return new QualityEvidence("BID", title, page, quote, null, 1.0);
    }

    public static QualityEvidence knowledgeBase(
            final String title, final String recordId, final String quote) {
        return new QualityEvidence(
                "KNOWLEDGE_BASE", title, null, quote, recordId, 1.0);
    }
}
