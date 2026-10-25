package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CaCertificateEvidenceTest {

    private CaCertificateEvidence createEvidence(final String platforms) {
        return new CaCertificateEvidence(
                1L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.of(2028, 1, 1), platforms,
                "IN_STOCK", "ACTIVE", "张三", "李四");
    }

    @ParameterizedTest(name = "台账平台【{0}】与需求平台【{1}】应匹配成功")
    @CsvSource({
            "河北省公共资源交易中心, 河北CA",
            "河北省公共资源交易服务平台, 河北 CA",
            "河北省电子招投标交易平台, 河北CA",
            "河北CA数字证书, 河北省公共资源交易中心",
            "国家电网新一代电子商务平台, 国家电网",
            "国家电网新一代电子商务平台, 国网",
            "南方电网供应链统一服务平台, 南方电网",
            "南方电网供应链统一服务平台, 南网",
            "浙江省政府采购网, 政采云",
            "上海市公共资源交易中心, 上海市公共资源交易中心"
    })
    @DisplayName("CA证书平台模糊匹配：跨机构叫法、省市简称与核心词双向归一化匹配")
    void testMatchesPlatform_FuzzyMatchingSuccess(final String dbPlatforms, final String reqPlatform) {
        CaCertificateEvidence evidence = createEvidence(dbPlatforms);
        assertThat(evidence.matchesPlatform(reqPlatform)).isTrue();
    }

    @ParameterizedTest(name = "台账平台【{0}】与需求平台【{1}】应判定不匹配")
    @CsvSource({
            "上海市公共资源交易中心, 河北CA",
            "国家电网新一代电子商务平台, 河北 CA",
            "南方电网供应链统一服务平台, 国家电网",
            "江苏省公共资源交易平台, 浙江政采云"
    })
    @DisplayName("CA证书平台匹配：跨省市及无关平台坚决判定不匹配")
    void testMatchesPlatform_DifferentPlatformsMismatch(final String dbPlatforms, final String reqPlatform) {
        CaCertificateEvidence evidence = createEvidence(dbPlatforms);
        assertThat(evidence.matchesPlatform(reqPlatform)).isFalse();
    }

    @Test
    void matchesPlatform_chinaMerchantsFullName_matchesLedgerShortName() {
        assertThat(createEvidence("招商局集团电子招采平台")
                .matchesPlatform("招商局集团电子招标采购交易平台")).isTrue();
        assertThat(createEvidence("其他集团电子招采平台")
                .matchesPlatform("招商局集团电子招标采购交易平台")).isFalse();
        assertThat(createEvidence("招商局集团电子招采平台")
                .matchesPlatform("或称交易平台")).isFalse();
    }

    @Test
    @DisplayName("平台归一化函数：正确剔除招投标噪音后缀与省市噪音")
    void testNormalizePlatform() {
        assertThat(CaCertificateEvidence.normalizePlatform("河北省公共资源交易服务平台")).isEqualTo("河北");
        assertThat(CaCertificateEvidence.normalizePlatform("河北 CA")).isEqualTo("河北");
        assertThat(CaCertificateEvidence.normalizePlatform("国家电网新一代电子商务平台")).isEqualTo("国家电网新一代");
        assertThat(CaCertificateEvidence.normalizePlatform("上海市公共资源交易中心")).isEqualTo("上海");
    }
}
