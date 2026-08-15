package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评分项知识库类别判定策略测试（spec 041 FR-011 分型计分前置）。
 * <p>判定优先级：BRAND &gt; WAREHOUSE &gt; PERSON &gt; PROJECT &gt; CERT &gt; OTHER。
 */
class KnowledgeCategoryPolicyTest {

    private final KnowledgeCategoryPolicy policy = new KnowledgeCategoryPolicy();

    @Test
    @DisplayName("资质证书类 → CERT")
    void categorize_cert() {
        assertThat(policy.categorize("资质", "具有 ISO9001 质量管理体系认证证书且在有效期内"))
                .isEqualTo("CERT");
    }

    @Test
    @DisplayName("人员类 → PERSON")
    void categorize_person() {
        assertThat(policy.categorize("人员", "拟派项目负责人具备一级建造师注册证书"))
                .isEqualTo("PERSON");
    }

    @Test
    @DisplayName("人员优先于证书（人员项常含证书字样）")
    void categorize_person_priorityOverCert() {
        assertThat(policy.categorize("项目团队", "项目团队成员 5 人，其中持 PMP 证书人员不少于 2 人"))
                .isEqualTo("PERSON");
    }

    @Test
    @DisplayName("业绩类 → PROJECT")
    void categorize_project() {
        assertThat(policy.categorize("业绩", "近三年类似项目业绩不少于 3 个，单个合同金额不低于 500 万元"))
                .isEqualTo("PROJECT");
    }

    @Test
    @DisplayName("仓库类 → WAREHOUSE（含资质字样仍归仓库）")
    void categorize_warehouse() {
        assertThat(policy.categorize("仓储", "自有仓储面积不少于 5000 平方米并提供产权证明"))
                .isEqualTo("WAREHOUSE");
    }

    @Test
    @DisplayName("品牌授权类 → BRAND")
    void categorize_brand() {
        assertThat(policy.categorize("品牌", "提供主要设备品牌厂家授权书及售后服务承诺"))
                .isEqualTo("BRAND");
    }

    @Test
    @DisplayName("描述性要求 → OTHER（无知识库匹配语义）")
    void categorize_other() {
        assertThat(policy.categorize("技术方案", "技术方案科学合理、切实可行"))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("null 输入 → OTHER 不抛错")
    void categorize_nullInput_other() {
        assertThat(policy.categorize(null, null)).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("提取要求数量：人员单位")
    void extractCount_personUnit() {
        assertThat(policy.extractCount("项目团队成员不少于 5 人", "人")).isEqualTo(5);
    }

    @Test
    @DisplayName("提取要求数量：业绩单位")
    void extractCount_projectUnit() {
        assertThat(policy.extractCount("类似业绩不少于 3 个", "个|项|份")).isEqualTo(3);
    }

    @Test
    @DisplayName("无数量表述 → null")
    void extractCount_missing_null() {
        assertThat(policy.extractCount("具备有效的质量管理体系认证", "人")).isNull();
    }

    @Test
    @DisplayName("关键词提取：过滤停用词与短 token，保留业务词")
    void extractKeywords_filtersNoise() {
        List<String> keywords = policy.extractKeywords("具有 ISO9001 质量管理体系认证证书，且在有效期内", 6);
        assertThat(keywords).contains("ISO9001");
        assertThat(keywords).doesNotContain("具有", "且在");
    }

    @Test
    @DisplayName("关键词提取：空文本 → 空列表")
    void extractKeywords_blank_empty() {
        assertThat(policy.extractKeywords("  ", 6)).isEmpty();
    }
}
