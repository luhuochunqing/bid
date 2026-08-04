package com.xiyu.bid.performance.controller;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BundleExportRequest} 单元测试。
 *
 * @since CO-602 PR 设计评估修复（D1-3）
 */
class BundleExportRequestTest {

    @Test
    void isIdMode_ids非空_返回true() {
        BundleExportRequest req = new BundleExportRequest(
                List.of(1L, 2L), null, Set.of());
        assertThat(req.isIdMode()).isTrue();
    }

    @Test
    void isIdMode_ids为空列表_返回false() {
        BundleExportRequest req = new BundleExportRequest(
                List.of(), null, Set.of());
        assertThat(req.isIdMode()).isFalse();
    }

    @Test
    void isIdMode_ids为null_返回false() {
        BundleExportRequest req = new BundleExportRequest(
                null, null, Set.of());
        assertThat(req.isIdMode()).isFalse();
    }

    @Test
    void safeCriteria_criteria非null_返回原值() {
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();
        BundleExportRequest req = new BundleExportRequest(
                null, criteria, Set.of());
        assertThat(req.safeCriteria()).isSameAs(criteria);
    }

    @Test
    void safeCriteria_criteria为null_返回空条件() {
        BundleExportRequest req = new BundleExportRequest(
                null, null, Set.of());
        assertThat(req.safeCriteria()).isNotNull();
    }

    @Test
    void safeAttachmentTypes_非null_返回原值() {
        Set<String> types = Set.of("CONTRACT_AGREEMENT", "BID_NOTICE");
        BundleExportRequest req = new BundleExportRequest(
                null, null, types);
        assertThat(req.safeAttachmentTypes()).isSameAs(types);
    }

    @Test
    void safeAttachmentTypes_null_返回空集合() {
        BundleExportRequest req = new BundleExportRequest(
                null, null, null);
        assertThat(req.safeAttachmentTypes()).isEmpty();
    }
}
