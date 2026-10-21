package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceTextTest {
    @Test void markdownEscapedMultiplicationPreservesSymbolAndRawOffsets() {
        String raw = "价格=商城价\\*折扣率";
        var source = EvidenceText.normalize(raw);
        assertThat(source.text()).isEqualTo("价格=商城价*折扣率");
        var span = source.uniqueSpan("商城价*折扣率").orElseThrow();
        assertThat(raw.substring(span.start(), span.end())).isEqualTo("商城价\\*折扣率");
        assertThat(EvidenceText.normalize("A\\\\*B").text()).isEqualTo("A\\\\*B");
        assertThat(EvidenceText.normalize("A\\qB").text()).isEqualTo("A\\qB");
        var star = source.uniqueSpan("*").orElseThrow();
        assertThat(raw.substring(star.start(), star.end())).isEqualTo("*");
        assertThat(source.uniqueSpan("商城价折扣率")).isEmpty();
        assertThat(EvidenceText.normalize(raw + "\n" + raw).uniqueSpan("商城价*折扣率")).isEmpty();
    }
    @Test void chineseLayoutWhitespaceRetainsOriginalOffsetsAndLatinWordBoundaries() {
        String raw = "**供应商**服务\n能力及 A B 型产品";
        var normalized = EvidenceText.normalize(raw);
        assertThat(normalized.text()).isEqualTo("供应商服务能力及 A B 型产品");
        var span = normalized.uniqueSpan("服务能力").orElseThrow();
        assertThat(raw.substring(span.start(), span.end())).isEqualTo("服务\n能力");
        assertThat(normalized.uniqueSpan("AB")).isEmpty();
    }
    @Test void repetitionAndTyposAreNeverUniqueSourceEvidence() {
        var source = EvidenceText.normalize("物流服务\n物流服务");
        assertThat(source.uniqueSpan("物流服务")).isEmpty();
        assertThat(source.uniqueSpan("物流服物")).isEmpty();
    }
    @Test void numberAndChineseNewlineRetainsNoWhitespace() {
        String raw = "一级集采商品价格分（满分28\n分）+非一级集采商品价格分（满分12分）";
        var normalized = EvidenceText.normalize(raw);
        assertThat(normalized.text()).isEqualTo("一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分）");
        var span = normalized.uniqueSpan("28分").orElseThrow();
        assertThat(raw.substring(span.start(), span.end())).isEqualTo("28\n分");
        var spanWithSpace = normalized.uniqueSpan("28 分").orElseThrow();
        assertThat(raw.substring(spanWithSpace.start(), spanWithSpace.end())).isEqualTo("28\n分");
    }

    @Test void comparisonOperatorGlyphVariantsAreLayoutNoise() {
        // 项目1120 task126：原文用 U+FE64「﹤」，模型引文写 ASCII「<」，逐字匹配失败导致整条规则丢失。
        assertThat(EvidenceText.normalize("速动比﹤1").text()).isEqualTo("速动比<1");
        assertThat(EvidenceText.normalize("速动比＜1").text()).isEqualTo("速动比<1");
        assertThat(EvidenceText.normalize("速动比﹥1").text()).isEqualTo("速动比>1");
        String raw = "速动比﹤1，得30分";
        var span = EvidenceText.normalize(raw).uniqueSpan("速动比<1").orElseThrow();
        assertThat(raw.substring(span.start(), span.end())).isEqualTo("速动比﹤1");
    }

    @Test void punctuationLineBreakInsideQuoteIsLayoutNoise() {
        // 项目1120 task126：模型引文在「：」后换行，原文单元格没有空白，归一化曾多插一个空格导致失配。
        assertThat(EvidenceText.normalize("数据取值来源：\n①速动资产").text()).isEqualTo("数据取值来源：①速动资产");
        assertThat(EvidenceText.normalize("满分100分。\n注：财务指标").text()).isEqualTo("满分100分。注：财务指标");
        assertThat(EvidenceText.normalize("A B").text()).isEqualTo("A B");
    }

    @Test void pdfHardLineBreakBetweenDigitAndHanIsLayoutNoise() {
        // 项目1382 Task 291：PDF 单元格把「满分28分」硬换行成「满分28\n分」，模型引文无换行。
        // 反向边界（汉字↔数字，"达到 5 年"）同样视为排版噪声；数字↔数字空格保留（千分位）。
        var cell = EvidenceText.normalize("一级集采商品价格分（满分28\n分）");
        assertThat(cell.text()).isEqualTo("一级集采商品价格分（满分28分）");
        assertThat(cell.uniqueSpan("一级集采商品价格分（满分28分）")).isPresent();
        assertThat(EvidenceText.normalize("运营时间达到 5 年以上").text()).isEqualTo("运营时间达到5年以上");
        assertThat(EvidenceText.normalize("合计 1 000").text()).isEqualTo("合计1 000");
    }
}
