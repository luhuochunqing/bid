package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 基于 docinsight 模块 {@link DocumentTextExtractor} 的招标文件正文提取适配器。
 *
 * <p>H13 修复 (2026-06-14): 移除 {@code @ConditionalOnMissingBean(TenderDocumentTextExtractor.class)}
 * —— 该条件检查的接口类型正是本类实现的接口 (self-referential), 在 test profile 因 bean
 * 评估顺序导致本 fallback 不注册, {@code AuthControllerTest} @SpringBootTest context 加载失败
 * (TenderDocumentTextExtractor 缺失, 非本任务 H13 引入而是 !574 未修全)。
 * {@code @Profile("!e2e")} + {@code @Primary} 已足够: 非 e2e profile 无条件注册,
 * e2e profile 由 {@code E2eTenderDocumentTextExtractor} 接管。</p>
 */
@Component
@Primary
@Profile("!e2e")
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
