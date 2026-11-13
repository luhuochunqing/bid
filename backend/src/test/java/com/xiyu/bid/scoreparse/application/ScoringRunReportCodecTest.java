package com.xiyu.bid.scoreparse.application;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ScoringRunReportCodecTest {
    private final ScoringRunReportCodec codec = new ScoringRunReportCodec();
    @Test void chineseOptionalSamplesAreTrimmedWithoutLosingIdentityOrCounts() {
        var report = new LinkedHashMap<String,Object>();
        report.put("request", Map.of("source", "MANUAL", "scope", "ALL", "itemIds", List.of()));
        report.put("input", Map.of("fileHash", "hash", "ruleFingerprint", "rule", "sourceDocumentId", 4));
        report.put("items", java.util.stream.LongStream.range(0,500).mapToObj(i -> Map.of("scoreItemId", i,
                "outcome", "SCORED", "originTaskId", 7, "description", "中文".repeat(400))).toList());
        var encoded = codec.encode(report);
        assertThat(encoded.tooLarge()).isFalse();
        assertThat(encoded.json().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(61440);
        assertThat(codec.scoring(encoded.json()).path("items").size()).isEqualTo(500);
        assertThat(codec.scoring(encoded.json()).path("input").path("fileHash").asText()).isEqualTo("hash");
    }
    @Test void coverageSamplesContainOnlyBoundedLocationsAndStableReasons() {
        var encoded=codec.encode(Map.of("input",Map.of("extraction",Map.of("unreadUnitCount",1,
                "unreadUnits",List.of(Map.of("partName","word/document.xml","anchor","p41","reason","BID_OCR_TIMEOUT",
                        "text","不应保存的原件正文".repeat(1000),"unknownPayload","private"))))));
        assertThat(encoded.json()).doesNotContain("不应保存的原件正文").doesNotContain("unknownPayload");
        var extraction=codec.scoring(encoded.json()).path("input").path("extraction");
        assertThat(extraction.path("unreadUnitCount").asInt()).isEqualTo(1);
        assertThat(extraction.path("unreadUnits").get(0).path("anchor").asText()).isEqualTo("p41");
        assertThat(codec.scoring(encoded.json()).path("samplesTruncated").asBoolean()).isTrue();
    }
    @Test void requiredFieldsTooLargeProduceSmallFailureAndNeverCopyLargeReport() {
        var encoded = codec.encode(Map.of("items", List.of(Map.of("scoreItemId", 1,
                "sourceFileHash", "x".repeat(70000), "outcome", "SCORED"))));
        assertThat(encoded.tooLarge()).isTrue();
        assertThat(encoded.json().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4096);
        assertThat(codec.scoring(encoded.json()).path("reasonCode").asText()).isEqualTo("REPORT_TOO_LARGE");
        assertThat(codec.scoring(encoded.json()).path("reportIncomplete").asBoolean()).isTrue();
    }
}
