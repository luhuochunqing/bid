package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringItemExtractorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\n\n\n"})
    void extract_shouldReturnEmpty_whenTextIsBlank(String text) {
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertTrue(result.isEmpty());
    }

    @Test
    void extract_shouldReturnEmpty_whenNoScoringItemsFound() {
        String text = """
                评标办法

                本项目采用综合评分法进行评审。
                评审委员会应当按照招标文件规定的方法和标准进行评审。
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertTrue(result.isEmpty());
    }

    // === 格式1：编号 评分项 分值（空格/制表符分隔） ===

    @Test
    void extract_shouldParseFormat1_numberSpaceItemWeight() {
        String text = """
                评标办法

                序号  评分项  分值
                1     价格     30
                2     技术     50
                3     商务     20

                第三章 合同
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals("1", result.get(0).itemNumber());
        assertEquals("价格", result.get(0).dimension());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
        assertEquals("2", result.get(1).itemNumber());
        assertEquals("技术", result.get(1).dimension());
        assertEquals(new java.math.BigDecimal("50"), result.get(1).weight());
    }

    // === 格式2：编号. 评审因素 评审标准 分值 ===

    @Test
    void extract_shouldParseFormat2_dottedNumberWithDetails() {
        String text = """
                评审因素

                1.1  技术方案  方案完整性  30分
                1.2  商务方案  报价合理性  20分
                2.1  价格      投标报价    50分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals("1.1", result.get(0).itemNumber());
        assertEquals("技术方案", result.get(0).dimension());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
        assertEquals("1.2", result.get(1).itemNumber());
        assertEquals("2.1", result.get(2).itemNumber());
    }

    // === 格式3：A1 技术方案（30分） ===

    @Test
    void extract_shouldParseFormat3_alphaNumberWithWeightInParens() {
        String text = """
                评标办法

                A1 技术方案（30分）
                A2 商务方案（20分）
                A3 价格评分（50分）
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals("A1", result.get(0).itemNumber());
        assertEquals("技术方案", result.get(0).dimension());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
        assertEquals("A3", result.get(2).itemNumber());
        assertEquals(new java.math.BigDecimal("50"), result.get(2).weight());
    }

    // === 格式4：维度 权重分 ===

    @Test
    void extract_shouldParseFormat4_dimensionWeightNoNumber() {
        String text = """
                评分标准

                价格评分 30分
                技术评分 50分
                商务评分 20分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals("价格评分", result.get(0).dimension());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
        assertEquals("技术评分", result.get(1).dimension());
        assertEquals(new java.math.BigDecimal("50"), result.get(1).weight());
    }

    // === 权重百分比 ===

    @Test
    void extract_shouldParseWeightAsPercentage() {
        String text = """
                评标办法

                1  价格  30%
                2  技术  50%
                3  商务  20%
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
        assertEquals(new java.math.BigDecimal("50"), result.get(1).weight());
        assertEquals(new java.math.BigDecimal("20"), result.get(2).weight());
    }

    // === subType 应自动分类 ===

    @Test
    void extract_shouldClassifySubType() {
        String text = """
                评标办法

                1  价格  30分
                2  技术方案  50分
                3  售后服务  20分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(3, result.size());
        assertEquals(com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType.PRICE_WEIGHT,
                result.get(0).subType());
        assertEquals(com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType.TECHNICAL_EVALUATION,
                result.get(1).subType());
        assertEquals(com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType.SERVICE_EVALUATION,
                result.get(2).subType());
    }

    // === 表头行不应被提取 ===

    @Test
    void extract_shouldSkipHeaderRows() {
        String text = """
                评标办法

                序号  评分项  分值
                1     价格     30

                评审因素  评审标准  分值
                1.1   技术方案  30
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        // "序号 评分项 分值" 和 "评审因素 评审标准 分值" 不应被提取
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).itemNumber());
        assertEquals("1.1", result.get(1).itemNumber());
    }

    // === 制表符分隔 ===

    @Test
    void extract_shouldHandleTabSeparated() {
        String text = """
                评标办法

                1\t价格\t30
                2\t技术\t50
                """;
        List<ScoringItemExtractor.ScoringItemRow> rows = ScoringItemExtractor.extractRaw(text);
        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).itemNumber());
        assertEquals("价格", rows.get(0).dimension());
    }

    // === 全角空格 ===

    @Test
    void extract_shouldHandleFullWidthSpace() {
        String text = """
                评标办法

                1　价格　30分
                2　技术　50分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(2, result.size());
        assertEquals("价格", result.get(0).dimension());
        assertEquals(new java.math.BigDecimal("30"), result.get(0).weight());
    }

    // === 纯文本行不应被提取 ===

    @Test
    void extract_shouldSkipPlainTextRows() {
        String text = """
                评标办法

                本项目采用综合评分法。
                评审委员会按照以下标准进行评审。
                总分100分。

                1  价格  30分
                2  技术  50分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(2, result.size());
    }

    // === weight 可为 null（未标注分值） ===

    @Test
    void extract_shouldHandleNullWeight() {
        String text = """
                评标办法

                1  价格评分
                2  技术方案
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(2, result.size());
        assertNull(result.get(0).weight());
        assertEquals("价格评分", result.get(0).dimension());
    }

    // === 排除非评分行（如"第三章 合同"） ===

    @Test
    void extract_shouldStopAtNextChapter() {
        String text = """
                评标办法

                1  价格  30分
                2  技术  50分

                第三章 合同条款
                1  甲方  乙方
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(2, result.size());
    }

    // === indicator 提取 ===

    @Test
    void extract_shouldCaptureIndicator() {
        String text = """
                评审因素

                1.1  技术方案  方案完整性和可行性  30分
                """;
        List<ScoringCriterion> result = ScoringItemExtractor.extract(text);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).indicator());
        assertTrue(result.get(0).indicator().contains("方案完整性"));
    }
}
