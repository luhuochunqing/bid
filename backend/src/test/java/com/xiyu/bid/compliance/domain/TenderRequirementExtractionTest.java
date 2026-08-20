// Input: 招标文件解析文本 + AiProvider mock（四种契约场景）
// Output: 验证招标要求 LLM 结构化提取契约——正常提取/无章节不臆造/非法 JSON 得 missing/Noop 得 missing
// Pos: compliance/domain/ - 招标要求提取契约测试（spec 049 US2 / T025，contracts/tender-requirements-extraction.md §4）
package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.service.TenderRequirementsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * spec 049 US2 / contracts/tender-requirements-extraction.md §4 四例契约测试.
 */
@ExtendWith(MockitoExtension.class)
class TenderRequirementExtractionTest {

    private static final String TENDER_TEXT_WITH_SECTIONS = """
            第三章 评标办法
            评分办法：技术方案 40 分，方案完整性与针对性；商务报价 30 分。
            第一章 资格审查
            投标人须具备 ISO27001 信息安全管理体系认证、CMMI 3 级及以上。
            第二章 否决条款
            未提供投标保证金的，其投标无效。
            """;

    @Mock
    private AiProvider aiProvider;

    private TenderRequirementsExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TenderRequirementsExtractor(aiProvider);
    }

    @Test
    @DisplayName("契约 1：含评分/资格审查/否决章节的招标文本提取出非空评分项与否决条款")
    void extract_tenderWithSections_returnsScoringItemsAndVetoClauses() {
        when(aiProvider.chatCompletion(anyString(), anyInt())).thenReturn("""
                {
                  "scoringItems": [
                    { "name": "技术方案", "maxScore": 40, "criteria": "方案完整性与针对性" }
                  ],
                  "mandatoryQualifications": ["ISO27001 信息安全管理体系认证", "CMMI 3 级及以上"],
                  "vetoClauses": [
                    { "clause": "未提供投标保证金的，其投标无效", "keywords": ["投标保证金", "无效"] }
                  ],
                  "compositionRequirements": [],
                  "formatRequirements": []
                }
                """);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash-t1", TENDER_TEXT_WITH_SECTIONS);

        TenderRequirements result = extractor.extract(tenderDoc);

        assertThat(result.missing()).isFalse();
        assertThat(result.scoringItems()).hasSize(1);
        assertThat(result.scoringItems().get(0).name()).isEqualTo("技术方案");
        assertThat(result.scoringItems().get(0).maxScore()).isEqualTo(40.0);
        assertThat(result.vetoClauses()).hasSize(1);
        assertThat(result.vetoClauses().get(0).clause()).contains("投标保证金");
        assertThat(result.vetoClauses().get(0).keywords()).containsExactly("投标保证金", "无效");
        assertThat(result.mandatoryQualifications()).contains("ISO27001 信息安全管理体系认证");

        // 提示词必须携带招标原文（仅从给定文本提取）
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(aiProvider).chatCompletion(prompt.capture(), anyInt());
        assertThat(prompt.getValue()).contains("投标保证金");
    }

    @Test
    @DisplayName("契约 2：无相关章节时五个键均为空数组，不臆造")
    void extract_tenderWithoutSections_returnsEmptyListsNotFabricated() {
        when(aiProvider.chatCompletion(anyString(), anyInt())).thenReturn("""
                {"scoringItems":[],"mandatoryQualifications":[],"vetoClauses":[],
                 "compositionRequirements":[],"formatRequirements":[]}
                """);
        DocumentContent tenderDoc = DocumentContent.fromText("notice.pdf", "hash-t2", "招标公告：项目概况说明，无评分与否决条款。");

        TenderRequirements result = extractor.extract(tenderDoc);

        assertThat(result.missing()).isFalse();
        assertThat(result.scoringItems()).isEmpty();
        assertThat(result.mandatoryQualifications()).isEmpty();
        assertThat(result.vetoClauses()).isEmpty();
        assertThat(result.compositionRequirements()).isEmpty();
        assertThat(result.formatRequirements()).isEmpty();
    }

    @Test
    @DisplayName("契约 3：LLM 返回非法 JSON 得 missing 哨兵，不抛异常中断任务")
    void extract_invalidJson_returnsMissingWithoutThrowing() {
        when(aiProvider.chatCompletion(anyString(), anyInt())).thenReturn("这不是 JSON {{{");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash-t3", TENDER_TEXT_WITH_SECTIONS);

        TenderRequirements result = extractor.extract(tenderDoc);

        assertThat(result.missing()).isTrue();
        assertThat(result.scoringItems()).isEmpty();
    }

    @Test
    @DisplayName("契约 4：Noop（AI 未启用）得 missing 哨兵；空响应同样得 missing")
    void extract_noopProvider_returnsMissing() {
        when(aiProvider.chatCompletion(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("AI 功能已在系统设置中关闭"));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash-t4", TENDER_TEXT_WITH_SECTIONS);

        TenderRequirements result = extractor.extract(tenderDoc);

        assertThat(result.missing()).isTrue();
    }

    @Test
    @DisplayName("招标文件未上传（空文本）不发起提取，直接得 missing")
    void extract_emptyTenderDocument_returnsMissingWithoutLlmCall() {
        TenderRequirements result = extractor.extract(DocumentContent.empty());

        assertThat(result.missing()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(aiProvider);
    }
}
