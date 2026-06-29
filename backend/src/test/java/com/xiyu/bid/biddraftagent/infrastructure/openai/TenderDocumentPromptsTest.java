// Input: minimal DocumentAnalysisInput + DocumentChunk fixtures
// Output: assertions that buildFullTenderPrompt guides the LLM to cover 7 business domains
// Pos: biddraftagent/infrastructure/openai — prompt text contract test (TDD)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenderDocumentPromptsTest {

    private static DocumentAnalysisInput sampleInput() {
        return new DocumentAnalysisInput(
                "doc-insight://test",
                "sample.docx",
                "正文示例",
                "",
                List.of(new DocumentChunk("正文示例", List.of())),
                "TENDER",
                Map.of("projectId", "proj-1")
        );
    }

    @Test
    void buildFullTenderPrompt_shouldGuideSevenDomains() {
        DocumentAnalysisInput input = sampleInput();
        DocumentChunk chunk = new DocumentChunk("正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildFullTenderPrompt(
                input, chunk, 1, 1, "");

        // 财务
        assertThat(prompt).contains("财务");
        assertThat(prompt).contains("财务数据");

        // 系统
        assertThat(prompt).contains("系统");
        assertThat(prompt).contains("商城对接");
        assertThat(prompt).contains("AI 应用");
        assertThat(prompt).contains("接口对接");

        // 法务
        assertThat(prompt).contains("法务");
        assertThat(prompt).contains("诉讼案件");
        assertThat(prompt).contains("股权结构");
        assertThat(prompt).contains("律所");

        // 人力资源
        assertThat(prompt).contains("人力资源");
        assertThat(prompt).contains("员工信息");
        assertThat(prompt).contains("社保");

        // 商品
        assertThat(prompt).contains("商品");
        assertThat(prompt).contains("品牌授权");
        assertThat(prompt).contains("清单报价");
        assertThat(prompt).contains("商品方案");

        // 行政
        assertThat(prompt).contains("行政");
        assertThat(prompt).contains("证照办理");

        // 仓储运输
        assertThat(prompt).contains("仓储运输");
        assertThat(prompt).contains("仓库资料");
        assertThat(prompt).contains("仓储运输方案");
    }
}
