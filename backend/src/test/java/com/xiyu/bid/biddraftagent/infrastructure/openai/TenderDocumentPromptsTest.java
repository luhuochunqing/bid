// Input: minimal DocumentAnalysisInput + DocumentChunk fixtures
// Output: assertions that buildFullTenderPrompt guides the LLM to cover 7 business domains
// Pos: biddraftagent/infrastructure/openai — prompt text contract test (TDD)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
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

    @Test
    void buildTenderIntakePrompt_shouldExcludeTenderInfoFieldInstruction() {
        DocumentAnalysisInput input = new DocumentAnalysisInput(
                "doc-insight://intake",
                "tender-notice.docx",
                "招标公告正文示例",
                "",
                List.of(new DocumentChunk("招标公告正文示例", List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                Map.of()
        );
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        // tenderInfo 由代码直接从 fullText 截断填充，AI 不再输出此字段
        // prompt 不应包含 tenderInfo 字段抽取指令
        assertThat(prompt).doesNotContain("tenderInfo：");
        assertThat(prompt).doesNotContain("完整原文");
        assertThat(prompt).doesNotContain("不要摘要");
        assertThat(prompt).doesNotContain("不要改写");
        // tenderScope（≤120 字摘要）指令保留
        assertThat(prompt).contains("tenderScope");
        assertThat(prompt).contains("120 字");
    }

    @Test
    void buildTenderIntakePrompt_shouldContainFewShotAndCoT() {
        DocumentAnalysisInput input = new DocumentAnalysisInput(
                "doc-insight://intake",
                "tender-notice.docx",
                "招标公告正文示例",
                "",
                List.of(new DocumentChunk("招标公告正文示例", List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                Map.of()
        );
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        // Few-Shot 示例存在
        assertThat(prompt).contains("Few-Shot 示例");
        assertThat(prompt).contains("泸州机场（集团）有限责任公司");
        assertThat(prompt).contains("深圳市财政局");

        // CoT 思考步骤存在
        assertThat(prompt).contains("思考步骤");
        assertThat(prompt).contains("第一步");
        assertThat(prompt).contains("第四步");

        // 正则预提取提示存在
        assertThat(prompt).contains("正则预提取提示");

        // sanitize 仍生效
        assertThat(prompt).doesNotContain("tenderInfo：");
        assertThat(prompt).doesNotContain("完整原文");
    }

    @Test
    void buildTenderIntakePrompt_shouldContainSectionsBlockWhenMetadataPresent() {
        String sectionsJson = """
                {
                  "sections": [
                    {"heading": "项目概况", "level": 1},
                    {"heading": "时间", "level": 1}
                  ]
                }
                """;
        DocumentAnalysisInput input = new DocumentAnalysisInput(
                "doc-insight://intake",
                "tender-notice.docx",
                "招标公告正文示例",
                sectionsJson,  // structuredMetadata
                List.of(new DocumentChunk("招标公告正文示例", List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                Map.of()
        );
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        // sections 元数据有值时，prompt 应包含文档结构
        assertThat(prompt).contains("文档结构");
        assertThat(prompt).contains("- 项目概况");
        assertThat(prompt).contains("- 时间");
    }

    @Test
    void buildTenderIntakePrompt_shouldNotContainSectionsBlockWhenMetadataNull() {
        DocumentAnalysisInput input = new DocumentAnalysisInput(
                "doc-insight://intake",
                "tender-notice.docx",
                "招标公告正文示例",
                null,  // structuredMetadata 为 null（粘贴文本场景）
                List.of(new DocumentChunk("招标公告正文示例", List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                Map.of()
        );
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        // sections 元数据为 null 时，prompt 不应包含文档结构
        assertThat(prompt).doesNotContain("文档结构");
    }

    @Test
    void buildTenderIntakePrompt_shouldNotContainTenderAgencyField() {
        // 标讯表单不记录代理机构，Prompt 不应包含 tenderAgency 字段口径
        DocumentAnalysisInput input = sampleIntakeInput();
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        // 字段口径不应出现 tenderAgency
        assertThat(prompt).doesNotContain("tenderAgency：");
        // Few-Shot 示例输出也不应出现 tenderAgency
        assertThat(prompt).doesNotContain("tenderAgency:");
    }

    @Test
    void buildTenderIntakePrompt_shouldContainPossibleDisplayInPrompt() {
        // Prompt 必须包含"可能标签"展示文案，让 AI 知道这些标签可能是招标主体
        DocumentAnalysisInput input = sampleIntakeInput();
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        assertThat(prompt).contains(PurchaserAliases.POSSIBLE_DISPLAY);
        assertThat(prompt).contains("可能标签");
    }

    @Test
    void buildTenderIntakePrompt_shouldContainAgencyExclusionNote() {
        // Prompt 必须包含代理机构反例说明，告诉 AI 代理机构不是招标主体
        DocumentAnalysisInput input = sampleIntakeInput();
        DocumentChunk chunk = new DocumentChunk("招标公告正文示例", List.of());

        String prompt = TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);

        assertThat(prompt).contains("代理机构");
        assertThat(prompt).contains("不是招标主体");
        assertThat(prompt).contains("标讯表单不记录代理机构");
    }

    private static DocumentAnalysisInput sampleIntakeInput() {
        return new DocumentAnalysisInput(
                "doc-insight://intake",
                "tender-notice.docx",
                "招标公告正文示例",
                "",
                List.of(new DocumentChunk("招标公告正文示例", List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                Map.of()
        );
    }
}
