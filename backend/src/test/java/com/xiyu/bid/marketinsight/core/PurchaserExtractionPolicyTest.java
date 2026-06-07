package com.xiyu.bid.marketinsight.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaserExtractionPolicyTest {

    @Test
    void extractPurchaser_ShouldMatchOrganizationalSuffix() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "国家电网某分公司2024年办公用品采购项目");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("国家电网某分公司");
    }

    @Test
    void extractPurchaser_ShouldMatchGroupSuffix() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "中国石化集团MRO工业品框架协议采购");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("中国石化集团");
    }

    @Test
    void extractPurchaser_ShouldFallbackToBeforeYear() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "华南电力集采 - 2026年度充电桩基础建设(第一批)");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("华南电力");
    }

    @Test
    void extractPurchaser_ShouldFallbackToBeforeKeyword() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "2024年某省直机关办公设备集中采购");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("某省直机关");
    }

    @Test
    void extractPurchaser_EmptyString_ShouldReturnNotFound() {
        var result = PurchaserExtractionPolicy.extractPurchaser("");
        assertThat(result.found()).isFalse();
    }

    @Test
    void extractPurchaser_NullInput_ShouldReturnNotFound() {
        var result = PurchaserExtractionPolicy.extractPurchaser(null);
        assertThat(result.found()).isFalse();
    }

    @Test
    void extractPurchaser_NoPurchaser_ShouldReturnNotFound() {
        var result = PurchaserExtractionPolicy.extractPurchaser("采购公告");
        assertThat(result.found()).isFalse();
    }

    @Test
    void extractPurchaser_ShouldIncludeHash() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "国家电网某分公司2024年办公用品采购项目");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserHash()).isNotBlank();
        assertThat(result.purchaserHash()).hasSize(16);
    }

    @Test
    void generatePurchaserHash_ShouldBeConsistent() {
        var hash1 = PurchaserExtractionPolicy.generatePurchaserHash("中国石化集团");
        var hash2 = PurchaserExtractionPolicy.generatePurchaserHash("中国石化集团");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void generatePurchaserHash_DifferentNames_ShouldDiffer() {
        var hash1 = PurchaserExtractionPolicy.generatePurchaserHash("中国石化集团");
        var hash2 = PurchaserExtractionPolicy.generatePurchaserHash("国家电网");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void generatePurchaserHash_ShouldReturn16HexChars() {
        var hash = PurchaserExtractionPolicy.generatePurchaserHash("测试公司");
        assertThat(hash).hasSize(16);
        assertThat(hash).matches("[0-9a-f]{16}");
    }

    @Test
    void extractPurchaser_ShouldMatchInstitute() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "中科院研究所2025年实验设备采购");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("中科院研究所");
    }

    @Test
    void extractPurchaser_ShouldMatchBank() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "中国建设银行2025年IT设备采购");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("中国建设银行");
    }

    @Test
    void extractPurchaser_ShouldStripLeadingQuotes() {
        var result = PurchaserExtractionPolicy.extractPurchaser(
                "「华南电力公司」2025年物资采购");
        assertThat(result.found()).isTrue();
        assertThat(result.purchaserName()).isEqualTo("华南电力公司");
    }

    @Test
    void extractionResult_NotFound_ShouldHaveEmptyFields() {
        var result = PurchaserExtractionPolicy.ExtractionResult.notFound();
        assertThat(result.found()).isFalse();
        assertThat(result.purchaserName()).isEmpty();
        assertThat(result.purchaserHash()).isEmpty();
    }
}
