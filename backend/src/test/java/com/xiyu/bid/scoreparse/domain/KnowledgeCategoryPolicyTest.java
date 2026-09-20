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

    @Test
    @DisplayName("面积门槛：不少于 5000 平方米")
    void extractMinArea_squareMeters() {
        assertThat(policy.extractMinArea("自有仓储面积不少于 5000 平方米"))
                .isEqualByComparingTo("5000");
        assertThat(policy.extractMinArea("无面积要求")).isNull();
    }

    @Test
    @DisplayName("面积门槛：千分位与小数不得咬成 0 或假门槛")
    void extractMinArea_thousandSeparatorAndDecimals() {
        assertThat(policy.extractMinArea("自有仓储面积不少于 5,000 平方米"))
                .isEqualByComparingTo("5000");
        assertThat(policy.extractMinArea("仓储面积 5000.00平方米"))
                .isEqualByComparingTo("5000.00");
        assertThat(policy.extractMinArea("面积 5000.5平方米"))
                .isEqualByComparingTo("5000.5");
        assertThat(policy.extractMinArea("面积 0 平方米")).isNull();
        assertThat(policy.extractMinArea(
                "供应商须在全国所有省份（含直辖市）中均设有仓储地点的得1.5分。"
                        + "须提供全国各省仓储地点的统计表，仓库面积、类型等信息"))
                .isNull();
    }

    @Test
    @DisplayName("1371 财务状况：即使细则提到人员/报表，因素名不是人员 → OTHER")
    void categorize_financeFactor_ignoresPersonnelWordsInDetail() {
        assertThat(policy.categorize("财务状况（3分）",
                "1.人员配置方面：项目经理花名册。现金流量净额连续三年为正的得1.5分。"
                        + "须提供经会计师事务所审计的财务报表"))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("1371 综合服务方案：细则含品牌覆盖，因素名是方案 → OTHER 不进品牌库")
    void categorize_servicePlan_ignoresBrandWordsInDetail() {
        assertThat(policy.categorize("针对MRO工业品综合服务方案（10分）",
                "商品上架管理品类及主流品牌覆盖管理、厂家授权"))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("1371 产品质量及价格：细则含授权，因素名是方案 → OTHER")
    void categorize_qualityPricePlan_ignoresAuthorizationInDetail() {
        assertThat(policy.categorize("针对MRO工业品产品质量及价格管控方案（10分）",
                "原厂商品合法销售授权管控；商品授权情况"))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("1371 服务网点及仓储：因素名含仓储 → WAREHOUSE")
    void categorize_warehouseFactor_byDim() {
        assertThat(policy.categorize("服务网点及仓储（3分）",
                "须提供全国各省仓储地点的统计表，包含仓库面积"))
                .isEqualTo("WAREHOUSE");
    }

    @Test
    @DisplayName("1371 物流仓储方案 / 服务团队方案是技术评分方案 → OTHER")
    void categorize_schemeFactors_areOther_notWarehouseOrPerson() {
        assertThat(policy.categorize("针对MRO工业品物流仓储方案（8分）", "仓储方案须涵盖省市区"))
                .isEqualTo("OTHER");
        assertThat(policy.categorize("针对MRO工业品服务团队方案（10分）", "人员配置方面：项目经理"))
                .isEqualTo("OTHER");
    }

    @Test
    @DisplayName("1374 央企案例：从 dim 和 detail 中提取央企/中央企业/MRO/工业品等领域专有词")
    void extractKeywords_dimAndDetail_priorityDomainTerms() {
        String dim = "央企案例";
        String detail = "在满足资格条件的基础上，每增加1个有效业绩得1分，最高得5分。注：业绩合同内容为：中央企业（集团）全系统范围或中央金融企业的MRO工业品类交易服务。时间为2022年1月至今。";
        List<String> keywords = policy.extractKeywords(dim, detail, 6);
        assertThat(keywords).contains("中央企业", "央企", "MRO", "工业品");
    }

    @Test
    @DisplayName("1374 提取签约时间：时间为2022年1月至今 / 近三年")
    void extractSignedAfter_variousFormats() {
        assertThat(policy.extractSignedAfter("时间为2022年1月至今"))
                .isEqualTo(java.time.LocalDate.of(2022, 1, 1));
        assertThat(policy.extractSignedAfter("2022年1月1日至今"))
                .isEqualTo(java.time.LocalDate.of(2022, 1, 1));
        assertThat(policy.extractSignedAfter("近三年类似业绩"))
                .isEqualTo(java.time.LocalDate.now().minusYears(3));
        assertThat(policy.extractSignedAfter("无时间要求")).isNull();
    }

    @Test
    @DisplayName("1374 提取业绩所需份数：每增加1个得1分最高得5分 → 5份")
    void extractProjectRequiredCount_perItemAndMaxScore() {
        String detail = "在满足资格条件的基础上，每增加1个有效业绩得1分，最高得5分（如多个业绩合同方为同一家公司，则算1个有效业绩）。";
        assertThat(policy.extractProjectRequiredCount(detail, new java.math.BigDecimal("5")))
                .isEqualTo(5);
        assertThat(policy.extractProjectRequiredCount("类似项目业绩不少于 3 个", new java.math.BigDecimal("6")))
                .isEqualTo(3);
        assertThat(policy.extractProjectRequiredCount("每提供一个有效业绩得5分，最高30分", new java.math.BigDecimal("30")))
                .isEqualTo(6);
    }

    @Test
    @DisplayName("提取业绩合同金额下限：1000万元以上与千分位")
    void extractMinContractAmount_variousFormats() {
        assertThat(policy.extractMinContractAmount("合同金额在1000万元以上（含1000万）"))
                .isEqualByComparingTo("10000000");
        assertThat(policy.extractMinContractAmount("单项合同金额不低于 1,000 万元"))
                .isEqualByComparingTo("10000000");
        assertThat(policy.extractMinContractAmount("合同金额不低于人民币 1000 万元"))
                .isEqualByComparingTo("10000000");
        assertThat(policy.extractMinContractAmount("合同金额在 1000 万元（含）以上"))
                .isEqualByComparingTo("10000000");
        assertThat(policy.extractMinContractAmount("未要求合同金额，只按业绩数量计分"))
                .isNull();
    }
}
