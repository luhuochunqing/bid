package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceTextTest {
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
}
