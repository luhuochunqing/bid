package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 基于 docinsight 模块 {@link DocumentTextExtractor} 的招标文件正文提取适配器。
 */
@Component
@Primary
@Profile("!e2e")
@ConditionalOnMissingBean(TenderDocumentTextExtractor.class)
class DocInsightTenderDocumentTextExtractor implements TenderDocumentTextExtractor {

    private final DocumentTextExtractor documentTextExtractor;

    DocInsightTenderDocumentTextExtractor(DocumentTextExtractor documentTextExtractor) {
        this.documentTextExtractor = documentTextExtractor;
    }

    @Override
    public ExtractedTenderDocument extract(String fileName, String contentType, byte[] content) {
        ExtractedDocument extracted = documentTextExtractor.extract(fileName, contentType, content);
        return new ExtractedTenderDocument(
                fileName,
                contentType,
                extracted.text(),
                extracted.textLength(),
                extracted.extractorKey(),
                extracted.structuredMetadata()
        );
    }
}
