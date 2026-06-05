package com.xiyu.bid.biddraftagent.infrastructure.e2e;

import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class E2eTenderIntakeDocumentAnalyzerTest {

    @Test
    void shouldExtractManualTenderGovernanceFields() {
        E2eTenderIntakeDocumentAnalyzer analyzer = new E2eTenderIntakeDocumentAnalyzer();
        String text = """
                标题：南方电网供应链数字化项目
                招标机构：西域招标代理有限公司
                业主单位：南方电网供应链集团
                总部所在地：广州
                报名截止时间：2026-06-10 10:00
                开标时间：2026-06-12 09:30
                联系人：王工
                联系方式：13800138000
                客户类型：央企集团
                优先级：S
                """;

        DocumentAnalysisResult result = analyzer.analyze(new DocumentAnalysisInput(
                "doc-insight://TENDER_INTAKE/manual-tender/paste.txt",
                "paste.txt",
                text,
                null,
                List.of(),
                "TENDER_INTAKE",
                Map.of()
        ));

        assertThat(analyzer.supports("TENDER_INTAKE")).isTrue();
        assertThat(result.extractedData())
                .containsEntry("title", "南方电网供应链数字化项目")
                .containsEntry("tenderAgency", "西域招标代理有限公司")
                .containsEntry("purchaserName", "南方电网供应链集团")
                .containsEntry("region", "广州")
                .containsEntry("deadline", "2026-06-10 10:00")
                .containsEntry("bidOpeningTime", "2026-06-12 09:30")
                .containsEntry("contactName", "王工")
                .containsEntry("contactPhone", "13800138000")
                .containsEntry("customerType", "央企集团")
                .containsEntry("priority", "S");
    }
}
