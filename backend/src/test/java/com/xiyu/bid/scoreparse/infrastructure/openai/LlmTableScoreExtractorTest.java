// Input: LlmTableScoreExtractor 测试
// Output: 提取逻辑、章节定位、分类过滤与对齐断言
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmTableScoreExtractorTest {

    @Mock
    private OpenAiStructuredOutputService structuredOutputService;

    @Mock
    private OpenAiBidAgentConfigurationResolver configurationResolver;

    private LlmTableScoreExtractor extractor;

    @BeforeEach
    void setUp() {
        lenient().when(configurationResolver.resolve(anyString()))
                .thenReturn(mock(OpenAiBidAgentRequestConfig.class));
        extractor = new LlmTableScoreExtractor(structuredOutputService, configurationResolver);
    }

    @Test
    void extractFromTable_standardScoreTable_returnsCandidates() {
        String table = """
                | 序号 | 评审因素 | 评分标准 | 分值 |
                | 1.1 | 财务状况 | 资产负债率合理得5分 | 5 |
                | 1.2 | 供应商业绩 | 提供近3年类似业绩得10分 | 10 |
                """;

        TableScoreExtractionOutput output = new TableScoreExtractionOutput();
        output.tableType = "SCORE_TABLE";
        output.isScoreTable = true;

        TableScoreExtractionOutput.TableScoreItem item1 = new TableScoreExtractionOutput.TableScoreItem();
        item1.code = "1.1";
        item1.parentDim = "商务部分";
        item1.dim = "财务状况";
        item1.detail = "资产负债率合理得5分";
        item1.weight = 5.0;

        TableScoreExtractionOutput.TableScoreItem item2 = new TableScoreExtractionOutput.TableScoreItem();
        item2.code = "1.2";
        item2.parentDim = "商务部分";
        item2.dim = "供应商业绩";
        item2.detail = "提供近3年类似业绩得10分";
        item2.weight = 10.0;

        output.items = List.of(item1, item2);

        when(structuredOutputService.request(anyString(), eq(TableScoreExtractionOutput.class), any(), anyString()))
                .thenReturn(output);

        List<ScoreCandidate> candidates = extractor.extractFromTable(table);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).code()).isEqualTo("1.1");
        assertThat(candidates.get(0).dim()).isEqualTo("商务部分/财务状况");
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
        assertThat(candidates.get(0).detail()).isEqualTo("资产负债率合理得5分");

        assertThat(candidates.get(1).code()).isEqualTo("1.2");
        assertThat(candidates.get(1).dim()).isEqualTo("商务部分/供应商业绩");
        assertThat(candidates.get(1).weight()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    }

    @Test
    void extractFromTable_contractAssessmentTable_returnsEmptyList() {
        String table = """
                | 考核项目 | 考核内容 | 违约金扣款 |
                | 供货时效 | 延迟交付扣2分 | 200元 |
                """;

        TableScoreExtractionOutput output = new TableScoreExtractionOutput();
        output.tableType = "CONTRACT_ASSESSMENT";
        output.isScoreTable = false;
        output.nonScoreReason = "属于合同执行期违约考核表，非评标打分项";
        output.items = List.of();

        when(structuredOutputService.request(anyString(), eq(TableScoreExtractionOutput.class), any(), anyString()))
                .thenReturn(output);

        List<ScoreCandidate> candidates = extractor.extractFromTable(table);

        assertThat(candidates).isEmpty();
    }

    @Test
    void extractFromTable_filtersSummaryRowAndZeroWeight() {
        String table = "| 3.1 | 商务评分标准 | 总分56分 | 56 |";

        TableScoreExtractionOutput output = new TableScoreExtractionOutput();
        output.tableType = "SCORE_TABLE";
        output.isScoreTable = true;

        TableScoreExtractionOutput.TableScoreItem summary = new TableScoreExtractionOutput.TableScoreItem();
        summary.code = "3.1";
        summary.dim = "商务评分标准";
        summary.detail = "总分56分";
        summary.weight = 56.0;
        summary.isSummaryRow = true;

        TableScoreExtractionOutput.TableScoreItem zeroWeight = new TableScoreExtractionOutput.TableScoreItem();
        zeroWeight.code = "3.2";
        zeroWeight.dim = "无分值项";
        zeroWeight.detail = "合格制审查";
        zeroWeight.weight = 0.0;

        output.items = List.of(summary, zeroWeight);

        when(structuredOutputService.request(anyString(), eq(TableScoreExtractionOutput.class), any(), anyString()))
                .thenReturn(output);

        List<ScoreCandidate> candidates = extractor.extractFromTable(table);

        assertThat(candidates).isEmpty();
    }

    @Test
    void extractFromTable_nullOrBlankInput_returnsEmpty() {
        assertThat(extractor.extractFromTable(null)).isEmpty();
        assertThat(extractor.extractFromTable("")).isEmpty();
        assertThat(extractor.extractFromTable("   ")).isEmpty();
    }

    @Test
    void extractRawTableBlocks_parsesMarkdownTables() {
        String doc = """
                # 招标文件
                这是说明段落。

                | 序号 | 评审因素 | 分值 |
                | 1 | 资质 | 5 |

                中间其他段落文本。

                | 考核项 | 扣分 |
                | 延误 | 2 |
                """;

        List<String> tables = LlmTableScoreExtractor.extractRawTableBlocks(doc);
        assertThat(tables).hasSize(2);
        assertThat(tables.get(0)).contains("评审因素");
        assertThat(tables.get(1)).contains("考核项");
    }

    @Test
    void extractFromDocument_skipsNonScoreTablesAndProcedureTables() {
        String doc = """
                # 第二章 投标人须知前附表
                | 条款号 | 条款名称 | 编列内容 |
                | 3.1 | 评标方法 | 综合评估法，价格30分，技术70分 |

                # 第三章 评标办法
                | 序号 | 评分因素 | 评分标准 | 分值 |
                | 1 | 业绩 | 类似业绩得10分 | 10 |
                """;

        TableScoreExtractionOutput output = new TableScoreExtractionOutput();
        output.tableType = "SCORE_TABLE";
        output.isScoreTable = true;
        TableScoreExtractionOutput.TableScoreItem item = new TableScoreExtractionOutput.TableScoreItem();
        item.code = "1";
        item.dim = "业绩";
        item.detail = "类似业绩得10分";
        item.weight = 10.0;
        output.items = List.of(item);

        when(structuredOutputService.request(anyString(), eq(TableScoreExtractionOutput.class), any(), anyString()))
                .thenReturn(output);

        List<ScoreCandidate> candidates = extractor.extractFromDocument(doc);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).dim()).isEqualTo("业绩");
        // 验证只请求了评分章节内的评分表（1 次调用），前附表被前置守卫拦截
        verify(structuredOutputService, times(1)).request(anyString(), eq(TableScoreExtractionOutput.class), any(), anyString());
    }
}
