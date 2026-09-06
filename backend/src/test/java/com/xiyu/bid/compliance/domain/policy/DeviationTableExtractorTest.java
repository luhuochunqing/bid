package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviationTableExtractorTest {

    @Test
    @DisplayName("空文档或无偏离表时提取结果为空")
    void testExtract_WhenNullOrBlank_ReturnsEmpty() {
        var res1 = DeviationTableExtractor.extract(null);
        assertThat(res1.tables()).isEmpty();
        assertThat(res1.hasCommercialTable()).isFalse();
        assertThat(res1.hasTechnicalTable()).isFalse();

        DocumentContent emptyDoc = DocumentContent.fromText("bid.pdf", "hash", "");
        var res2 = DeviationTableExtractor.extract(emptyDoc);
        assertThat(res2.tables()).isEmpty();
    }

    @Test
    @DisplayName("标准商务偏离表结构化解析：正常行识别为 NONE 偏离")
    void testExtract_WhenStandardCommercialTable_ExtractsRowsCorrectly() {
        String text = """
                ### 第一节 商务偏离表
                | 序号 | 招标文件条款 | 招标文件要求 | 投标文件响应 | 偏离情况 | 偏离说明 |
                |---|---|---|---|---|---|
                | 1 | 商务条款 3.1 | 付款方式：3-3-3-1 | 完全响应 | 无偏离 | 满足招标文件要求 |
                | 2 | 商务条款 4.2 | 工期：180日历天 | 响应180天 | 无偏离 | / |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h1", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.tables()).hasSize(1);
        var table = result.tables().get(0);
        assertThat(table.category()).isEqualTo(DeviationTableExtractor.TableCategory.COMMERCIAL);
        assertThat(table.rows()).hasSize(2);
        assertThat(table.hasNegativeDeviation()).isFalse();
        assertThat(table.allNoneOrPositive()).isTrue();

        var row1 = table.rows().get(0);
        assertThat(row1.type()).isEqualTo(DeviationTableExtractor.DeviationType.NONE);
        assertThat(row1.clause()).contains("商务条款 3.1");

        var row2 = table.rows().get(1);
        assertThat(row2.type()).isEqualTo(DeviationTableExtractor.DeviationType.NONE);
        assertThat(row2.description()).isEqualTo("/");
    }

    @Test
    @DisplayName("技术偏离表中识别负偏离行：精准定位不满足条款与说明")
    void testExtract_WhenNegativeDeviationRow_IdentifiesAccurately() {
        String text = """
                ### 技术偏离表
                | 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |
                | 1 | 2.1 | 质保期 3 年 | 承诺 3 年 | 无偏离 | 满足 |
                | 2 | 2.2 | 交付周期 30 天 | 实际 45 天 | 负偏离 | 产能排期原因需 45 天 |
                | 3 | 2.3 | 响应时间 2 小时 | 4 小时 | 部分满足 | 驻点较远 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h2", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.hasTechnicalTable()).isTrue();
        assertThat(result.negativeRows()).hasSize(2);

        var neg1 = result.negativeRows().get(0);
        assertThat(neg1.type()).isEqualTo(DeviationTableExtractor.DeviationType.NEGATIVE);
        assertThat(neg1.clause()).isEqualTo("2.2");
        assertThat(neg1.description()).contains("产能排期");

        var neg2 = result.negativeRows().get(1);
        assertThat(neg2.type()).isEqualTo(DeviationTableExtractor.DeviationType.NEGATIVE);
        assertThat(neg2.clause()).isEqualTo("2.3");
        assertThat(neg2.bidResponse()).contains("4 小时");
    }

    @Test
    @DisplayName("偏离表中识别正偏离行：优于招标要求放行为 POSITIVE")
    void testExtract_WhenPositiveDeviationRow_IdentifiedAsPositive() {
        String text = """
                ### 技术规格偏离表
                | 序号 | 条款 | 招标要求 | 投标响应 | 偏离情况 | 偏离说明 |
                | 1 | 3.1 | 系统可用性 99.9% | 系统可用性 99.99% | 正偏离 | 采用双机热备架构，指标优于招标要求 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h3", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.tables()).hasSize(1);
        assertThat(result.positiveRows()).hasSize(1);
        assertThat(result.negativeRows()).isEmpty();
        var pos = result.positiveRows().get(0);
        assertThat(pos.type()).isEqualTo(DeviationTableExtractor.DeviationType.POSITIVE);
        assertThat(pos.description()).contains("双机热备");
    }

    @Test
    @DisplayName("否定短语防护：'无负偏离'或'不存在负偏离'不应误判为负偏离")
    void testExtract_WhenNegatedPhrases_ClassifiedAsNone() {
        String text = """
                ### 商务偏离表
                | 序号 | 条款 | 招标要求 | 投标响应 | 偏离说明 |
                | 1 | 付款 | 约定条款 | 遵照执行 | 全面响应，无负偏离 |
                | 2 | 售后 | 7x24响应 | 遵照执行 | 严格遵从，不存在负偏离 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h4", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.negativeRows()).isEmpty();
        assertThat(result.tables().get(0).allNoneOrPositive()).isTrue();
    }

    @Test
    @DisplayName("未填占位符识别：表内包含【请填写】或下划线留白时提取占位符")
    void testExtract_WhenPlaceholdersPresent_CapturesPlaceholders() {
        String text = """
                ### 商务偏离表
                | 序号 | 条款 | 招标要求 | 投标响应 | 偏离情况 | 偏离说明 |
                | 1 | 供货期 | 30 天 | 30 天 | 无偏离 | 【请填写详细说明】 |
                | 2 | 培训 | 提供培训 | 响应 | 无偏离 | _________ |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h5", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.allPlaceholders()).isNotEmpty();
        assertThat(result.allPlaceholders()).anyMatch(p -> p.contains("【请填写详细说明】"));
        assertThat(result.allPlaceholders()).anyMatch(p -> p.contains("___"));
    }

    @Test
    @DisplayName("双偏离表齐备性提取：同时识别商务偏离表与技术偏离表")
    void testExtract_WhenDualTables_ExtractsBoth() {
        String text = """
                第二章 商务偏离表
                | 序号 | 商务条款 | 响应 | 偏离情况 | 说明 |
                | 1 | 条款A | 响应 | 无偏离 | 无 |

                第三章 技术偏离表
                | 序号 | 技术条款 | 响应 | 偏离情况 | 说明 |
                | 1 | 条款B | 响应 | 无偏离 | / |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h6", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.hasCommercialTable()).isTrue();
        assertThat(result.hasTechnicalTable()).isTrue();
        assertThat(result.tables()).hasSize(2);
    }

    @Test
    @DisplayName("目录点线过滤：跳过纯目录中的偏离表点线索引")
    void testExtract_WhenCatalogPage_SkipsCatalog() {
        String text = """
                目录
                一、投标函....................................1
                二、商务偏离表................................5
                三、技术偏离表................................8
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "h7", text);

        var result = DeviationTableExtractor.extract(doc);

        assertThat(result.tables()).isEmpty();
    }
}
