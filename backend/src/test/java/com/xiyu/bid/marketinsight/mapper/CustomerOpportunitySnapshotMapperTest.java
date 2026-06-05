package com.xiyu.bid.marketinsight.mapper;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerOpportunitySnapshotMapperTest {

    private final CustomerOpportunitySnapshotMapper mapper = new CustomerOpportunitySnapshotMapper();

    @Test
    void toTenderSnapshot_shouldExtractPurchaserAndIndustrySignals() {
        Tender tender = Tender.builder()
                .id(11L)
                .title("国网江苏省电力办公设备采购项目")
                .budget(new BigDecimal("1280000"))
                .createdAt(LocalDateTime.of(2026, 4, 10, 9, 0))
                .build();

        var snapshot = mapper.toTenderSnapshot(tender);

        assertThat(snapshot.tenderId()).isEqualTo(11L);
        assertThat(snapshot.tenderTitle()).contains("办公设备采购");
        assertThat(snapshot.purchaserName()).contains("国网江苏省电力");
        assertThat(snapshot.purchaserHash()).isNotBlank();
        assertThat(snapshot.industry()).isNotBlank();
        assertThat(snapshot.budget()).isEqualByComparingTo("1280000");
        assertThat(snapshot.createdAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 9, 0));
    }
}
