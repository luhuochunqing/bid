// Input: TenderDocumentAnalysisInput and DocumentAnalysisInput with mocked DeepSeek-compatible AI responses
// Output: assertions on full field round-trip (CRIT-1) and prompt rule content (CRIT-2)
// Pos: biddraftagent/infrastructure/openai (integration test with mocks)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.application.TenderDocumentAnalysisInput;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiTenderDocumentAnalyzerTest {

    @Mock
    private OpenAiBidAgentConfigurationResolver configurationResolver;

    @Mock
    private OpenAiStructuredOutputService structuredOutputService;

    private OpenAiTenderDocumentAnalyzer analyzer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        StructuralDocumentChunker chunker = new StructuralDocumentChunker(objectMapper);
        analyzer = new OpenAiTenderDocumentAnalyzer(configurationResolver, structuredOutputService, chunker);

        lenient().when(configurationResolver.resolve(anyString())).thenReturn(new OpenAiBidAgentRequestConfig(
                "deepseek-key", "https://api.deepseek.com", "deepseek-chat",
                java.time.Duration.ofSeconds(90), OpenAiBidAgentApiStyle.CHAT_COMPLETIONS
        ));
        lenient().when(configurationResolver.resolveTenderIntake()).thenReturn(new OpenAiBidAgentRequestConfig(
                "deepseek-key", "https://api.deepseek.com", "deepseek-chat",
                java.time.Duration.ofSeconds(45), OpenAiBidAgentApiStyle.CHAT_COMPLETIONS
        ));
    }

    @Test
    void supports_shouldAcceptTenderAndTenderIntakeProfiles() {
        assertThat(analyzer.supports("TENDER")).isTrue();
        assertThat(analyzer.supports("tender_intake")).isTrue();
        assertThat(analyzer.supports("REPORT")).isFalse();
    }

    @Test
    void analyze_shouldUseStructuralChunksAndPreserveSectionPath() {
        String text = "# Chapter 1\\nQualification Content\\n# Chapter 2\\nTechnical Content";
        String metadata = """
                {
                  "sections": [
                    {"heading": "Chapter 1", "charStart": 0, "charEnd": 34, "path": ["Chapter 1"]},
                    {"heading": "Chapter 2", "charStart": 35, "charEnd": 68, "path": ["Chapter 2"]}
                  ]
                }
                """;
        TenderDocumentAnalysisInput input = new TenderDocumentAnalysisInput(1L, 100L, "test.docx", text, metadata);

        // Mock AI responses – use standalone TenderRequirementOutput (no longer inner class)
        TenderRequirementOutput output1 = new TenderRequirementOutput();
        TenderRequirementItemOutput item1 = new TenderRequirementItemOutput();
        item1.category = "qualification";
        item1.title = "Cert 1";
        item1.sectionPath = ""; // AI didn't return path, should use default
        output1.requirementItems = List.of(item1);

        TenderRequirementOutput output2 = new TenderRequirementOutput();
        TenderRequirementItemOutput item2 = new TenderRequirementItemOutput();
        item2.category = "technical";
        item2.title = "Spec 1";
        item2.sectionPath = "Chapter 2 > 2.1 Fine Grained"; // AI returned more specific path
        output2.requirementItems = List.of(item2);

        when(structuredOutputService.request(anyString(), eq(TenderRequirementOutput.class), any(), anyString()))
                .thenReturn(output1)
                .thenReturn(output2);

        TenderRequirementProfile result = analyzer.analyze(input);

        assertThat(result.items()).hasSize(2);

        // Item 1: path inferred from chunk
        assertThat(result.items().get(0).title()).isEqualTo("Cert 1");
        assertThat(result.items().get(0).sectionPath()).isEqualTo("Chapter 1");

        // Item 2: path taken from AI response
        assertThat(result.items().get(1).title()).isEqualTo("Spec 1");
        assertThat(result.items().get(1).sectionPath()).isEqualTo("Chapter 2 > 2.1 Fine Grained");

        // Verify prompts contained section info
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredOutputService, times(2)).request(promptCaptor.capture(), any(), any(), anyString());

        assertThat(promptCaptor.getAllValues().get(0)).contains("当前正文所属章节路径: Chapter 1");
        assertThat(promptCaptor.getAllValues().get(1)).contains("当前正文所属章节路径: Chapter 2");
    }

    // ── CRIT-1: all 18 output fields survive the round-trip ───────────────────

    @Test
    void analyze_allFieldsRoundTrip_noneDropped() {
        String text = "招标文件正文内容";
        TenderDocumentAnalysisInput input = new TenderDocumentAnalysisInput(10L, 200L, "bid.docx", text);

        TenderRequirementOutput output = new TenderRequirementOutput();
        output.projectName = "西域信息系统采购";
        output.tenderTitle = "招标公告标题";
        output.tenderScope = "软件系统集成";
        output.purchaserName = "西域集团有限公司";
        output.budget = "6800000";
        output.region = "新疆";
        output.industry = "信息技术";
        output.publishDate = "2024-01-15";
        output.deadline = "2024-03-20T17:00:00";
        output.qualificationRequirements = List.of("具备软件开发资质");
        output.technicalRequirements = List.of("支持国产化操作系统");
        output.commercialRequirements = List.of("价格不超过预算");
        output.scoringCriteria = List.of("技术分60，商务分40");
        output.deadlineText = "2024年3月20日17时整";
        output.requiredMaterials = List.of("营业执照", "资质证书");
        output.riskPoints = List.of("交付周期风险");
        output.tags = List.of("信息化", "集成");

        TenderRequirementItemOutput item = new TenderRequirementItemOutput();
        item.category = "qualification";
        item.title = "资质要求";
        item.content = "需具备CMMI3及以上";
        item.mandatory = true;
        item.sourceExcerpt = "供应商须具备";
        item.confidence = 90;
        item.sectionPath = "第一章 > 资质要求";
        output.requirementItems = List.of(item);

        when(structuredOutputService.request(anyString(), eq(TenderRequirementOutput.class), any(), anyString()))
                .thenReturn(output);

        TenderRequirementProfile result = analyzer.analyze(input);

        // Scalar fields – all 9 previously-dropped scalar fields are present
        assertThat(result.projectName()).isEqualTo("西域信息系统采购");
        assertThat(result.tenderTitle()).isEqualTo("招标公告标题");
        assertThat(result.tenderScope()).isEqualTo("软件系统集成");
        assertThat(result.purchaserName()).isEqualTo("西域集团有限公司");
        assertThat(result.budget()).isEqualByComparingTo(new BigDecimal("6800000"));
        assertThat(result.region()).isEqualTo("新疆");
        assertThat(result.industry()).isEqualTo("信息技术");
        assertThat(result.publishDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.deadline()).isEqualTo(LocalDateTime.of(2024, 3, 20, 17, 0, 0));
        assertThat(result.deadlineText()).isEqualTo("2024年3月20日17时整");

        // List fields – all previously-dropped list fields are present
        assertThat(result.qualificationRequirements()).containsExactly("具备软件开发资质");
        assertThat(result.technicalRequirements()).containsExactly("支持国产化操作系统");
        assertThat(result.commercialRequirements()).containsExactly("价格不超过预算");
        assertThat(result.scoringCriteria()).containsExactly("技术分60，商务分40");
        assertThat(result.requiredMaterials()).containsExactly("营业执照", "资质证书");
        assertThat(result.riskPoints()).containsExactly("交付周期风险");
        assertThat(result.tags()).containsExactly("信息化", "集成");

        // requirementItems round-trip
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).category()).isEqualTo("qualification");
        assertThat(result.items().get(0).title()).isEqualTo("资质要求");
        assertThat(result.items().get(0).mandatory()).isTrue();
        assertThat(result.items().get(0).confidence()).isEqualTo(90);
    }

    // ── CRIT-2: prompt contains all 10 rule items ──────────────────────────────

    @Test
    void buildPrompt_containsAllRuleItems() {
        String text = "招标文件内容";
        TenderDocumentAnalysisInput input = new TenderDocumentAnalysisInput(1L, 1L, "test.docx", text);

        TenderRequirementOutput emptyOutput = new TenderRequirementOutput();
        when(structuredOutputService.request(anyString(), eq(TenderRequirementOutput.class), any(), anyString()))
                .thenReturn(emptyOutput);

        analyzer.analyze(input);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredOutputService, atLeastOnce()).request(promptCaptor.capture(), any(), any(), anyString());
        String prompt = promptCaptor.getValue();

        // Rule 1: safety preamble – prompt-injection guard
        assertThat(prompt).contains("不可信用户内容");

        // Rule 2: chunk-context – only extract from this slice
        assertThat(prompt).contains("请只从本片正文中提取");

        // Rule 3: requirementItems coverage instruction
        assertThat(prompt).contains("requirementItems 必须逐条列出");

        // Rule 4: category enum constraint
        assertThat(prompt).contains("category 只能使用");

        // Rule 5: mandatory semantics
        assertThat(prompt).contains("mandatory 表示是否为必须");

        // Rule 6: sourceExcerpt + confidence format
        assertThat(prompt).contains("sourceExcerpt 保留能定位来源的短句");

        // Rule 7: budget format + no-inference rule
        assertThat(prompt).contains("budget 表示项目预算");

        // Rule 8: region/industry no-inference rule
        assertThat(prompt).contains("region 表示项目所属地区");

        // Rule 9: date format rules
        assertThat(prompt).contains("publishDate 使用 yyyy-MM-dd");

        // Rule 10: all-fields constraint
        assertThat(prompt).contains("所有字段只能来自本片正文");

        // Fields list (18 fields enumerated)
        assertThat(prompt).contains("projectName").contains("tenderTitle").contains("requirementItems");

        // Document fence still present
        assertThat(prompt).contains("<document>");
        assertThat(prompt).contains("</document>");
    }

    @Test
    void analyzeTenderIntake_shouldUseFocusedPromptForManualFormFieldsOnly() {
        String text = """
                目录
                第一章 招标公告
                项目名称：西域MRO测试项目
                最高限价：1200000元
                采购单位：西域采购中心
                资格要求：供应商须具备资质
                第二章 评分办法
                技术分60分，商务分40分
                """;
        DocumentAnalysisInput input = new DocumentAnalysisInput(
                "doc-insight://manual",
                "manual.docx",
                text,
                "",
                List.of(new DocumentChunk(text, List.of())),
                DocInsightProfiles.TENDER_INTAKE,
                java.util.Map.of()
        );
        TenderRequirementOutput output = new TenderRequirementOutput();
        output.tenderTitle = "西域MRO测试项目";
        output.budget = "1200000";
        output.tenderAgency = "上海招标代理有限公司";
        output.bidOpeningTime = "2026-06-03T10:00:00";
        output.contactName = "李经理";
        output.contactPhone = "13800138000";
        output.customerType = "KA 客户";
        output.priority = "A";
        when(structuredOutputService.request(anyString(), eq(TenderRequirementOutput.class), any(), anyString()))
                .thenReturn(output);

        var result = analyzer.analyze(input);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredOutputService).request(promptCaptor.capture(), any(), any(), anyString());
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("人工录入标讯表单");
        assertThat(prompt).contains("只抽取这些字段");
        assertThat(prompt).contains("标讯标题").contains("预算金额").contains("招标机构")
                .contains("业主单位").contains("客户类型").contains("优先级");
        assertThat(result.extractedData()).containsEntry("tenderAgency", "上海招标代理有限公司")
                .containsEntry("bidOpeningTime", "2026-06-03T10:00:00")
                .containsEntry("contactPhone", "13800138000")
                .containsEntry("customerType", "KA 客户")
                .containsEntry("priority", "A");
        assertThat(prompt).doesNotContain("requirementItems 必须逐条列出");
        assertThat(prompt.substring(prompt.indexOf("<candidate_text>")))
                .doesNotContain("评分办法");
        verify(configurationResolver).resolveTenderIntake();
    }
}
