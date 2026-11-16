package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.domain.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class QualityReportSnapshotTest {
    private List<QualityCheckItemResult> completeItems() {
        return Arrays.stream(BidQualityCheckItem.values()).map(i ->
                QualityCheckItemResult.pass(i, "完成", List.of())).toList();
    }
    @Test
    void completeCurrentSnapshotCanBeReusedButPreviousVersionsCannot() throws Exception {
        String json = QualityReportSnapshot.write(completeItems());
        assertThat(QualityReportSnapshot.reusable(json)).isTrue();
        assertThat(QualityReportSnapshot.reusable(json.replace(QualityReportSnapshot.CURRENT_VERSION, "previous"))).isFalse();
        assertThat(QualityReportSnapshot.read(json).items()).hasSize(25);
    }
    @Test
    void emptyCorruptOrPartialSnapshotsAreNotCacheHits() throws Exception {
        for (String json : List.of("[]", "null", "{", "{\"version\":\"x\",\"items\":null}",
                QualityReportSnapshot.write(List.of(completeItems().getFirst())))) {
            assertThat(QualityReportSnapshot.reusable(json)).as(json).isFalse();
        }
        assertThat(QualityReportSnapshot.reusable(null)).isFalse();
    }
    @Test
    void legacyReadPreservesVerdictQuoteAndStoredJsonButClearsUnverifiedLocation() throws Exception {
        String json = "[{\"itemCode\":\"RESPONSIVENESS_REVIEW\",\"status\":\"FAIL\",\"conclusion\":\"需修订\",\"evidences\":[{\"sourceType\":\"BID\",\"pageNumber\":32,\"quote\":\"原文\"}]}]";
        var item = QualityReportSnapshot.read(json).items().getFirst();
        assertThat(item.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(item.evidences().getFirst().quote()).isEqualTo("原文");
        assertThat(item.evidences().getFirst().pageNumber()).isNull();
        assertThat(json).contains("\"pageNumber\":32");
    }
}
