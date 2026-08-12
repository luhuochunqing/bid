package com.xiyu.bid.integration.tenderevent.domain;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderEventPolicy - 标讯事件推送策略")
class TenderEventPolicyTest {

    @Test
    @DisplayName("人工录入 MANUAL_SINGLE → 推送")
    void manualSingle_pushes() {
        assertThat(TenderEventPolicy.shouldPublish(Tender.SourceType.MANUAL_SINGLE)).isTrue();
    }

    @Test
    @DisplayName("批量导入 BULK_IMPORT → 推送")
    void bulkImport_pushes() {
        assertThat(TenderEventPolicy.shouldPublish(Tender.SourceType.BULK_IMPORT)).isTrue();
    }

    @Test
    @DisplayName("第三方平台 EXTERNAL_PLATFORM → 推送")
    void externalPlatform_pushes() {
        assertThat(TenderEventPolicy.shouldPublish(Tender.SourceType.EXTERNAL_PLATFORM)).isTrue();
    }

    @Test
    @DisplayName("CRM 推送创建 CRM_OPPORTUNITY → 不推送（避免回发循环）")
    void crmOpportunity_skips() {
        assertThat(TenderEventPolicy.shouldPublish(Tender.SourceType.CRM_OPPORTUNITY)).isFalse();
    }

    @Test
    @DisplayName("null source → 不推送")
    void nullSource_skips() {
        assertThat(TenderEventPolicy.shouldPublish(null)).isFalse();
    }
}