// Input: uploaded tender file metadata in e2e profile
// Output: deterministic extracted tender text for Playwright API-backed tests
// Pos: biddraftagent/infrastructure/e2e - test-profile adapter, no production activation
package com.xiyu.bid.biddraftagent.infrastructure.e2e;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("e2e")
public class E2eTenderDocumentTextExtractor implements TenderDocumentTextExtractor {

    private static final String FIXTURE_TEXT = """
            西域 MRO 数字化投标测试项目招标文件
            商务条款要求：投标人需提供商务条款响应、报价说明和售后服务承诺。
            技术方案要求：投标人需提交平台实施方案、接口对接方案和项目进度计划。
            资格材料要求：投标人需提交营业执照、法人授权书、类似项目经验证明。
            评分标准：技术方案完整性、商务响应完整性、资料真实性均作为评审重点。
            """;

    @Override
    public ExtractedTenderDocument extract(String fileName, String contentType, byte[] content) {
        return new ExtractedTenderDocument(
                fileName,
                contentType,
                FIXTURE_TEXT,
                FIXTURE_TEXT.length(),
                "e2e-fixed-text",
                null
        );
    }
}
