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
    void legacyFactorNameResolutionPreservesSpecificNamesAndRejectsIdentifiers() {
        record Example(String code, String dim, String expected) {}
        for (var c : List.of(
                new Example("仓储能力", "技术", "仓储能力"),
                new Example("服务案例及业绩", "商务（56分）", "服务案例及业绩"),
                new Example("品牌授权", "技术部分", "品牌授权"),
                new Example("企业荣誉", "商务", "企业荣誉"),
                new Example("品牌授权", "财务状况", "财务状况"),
                new Example("3.3(2)", "技术", "技术"),
                new Example("A1", "商务", "商务"),
                new Example("技术", "商务", "商务"),
                new Example("", "商务", "商务"))) {
            assertThat(policy.factorName(c.code(), c.dim())).as(c.toString()).isEqualTo(c.expected());
        }
        assertThat(policy.factorName(null, "商务")).isEqualTo("商务");
        assertThat(policy.factorName("仓储能力", null)).isNull();
        assertThat(policy.factorName("仓储能力", "")).isEmpty();
    }

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
    @DisplayName("1371 财务状况：dim 含'财务'关键词 → FINANCIAL（不再是 OTHER）")
    void categorize_financeFactor_nowFinancial() {
        // 以前期望 OTHER（无 FINANCIAL 类别），现在 FINANCIAL 规则捕获 dim 中的"财务" → FINANCIAL
        assertThat(policy.categorize("财务状况（3分）",
                "1.人员配置方面：项目经理花名册。现金流量净额连续三年为正的得1.5分。"
                        + "须提供经会计师事务所审计的财务报表"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_FINANCIAL);
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

    @Test
    @DisplayName("项目22 供应商业绩：细则中含国资委授权字样，必须归类为 PROJECT 而非 BRAND")
    void categorize_project22_supplierPerformance_ignoresAuthInDetail() {
        String dim = "供应商业绩";
        String detail = "供应商具有近三年内（2023年1月1日起至今，供应商具有1项大型集团型国央企电子商城办公用品服务业绩的不得分，每增加1个得1分，最高得7分。\n"
                + "注：\n"
                + "1.大型集团型国央企指：1）国务院国资委代表国务院履行出资人职责的中央企业；2）以及国务院授权财政部履行出资人职责的企业；3）各省、自治区、直辖市人民政府授权本级国资委履行出资人职责的省属国有独资、全资、控股企业。\n"
                + "2.供应商应同时提供以下证明材料：\n"
                + "（1）服务业绩时间以合同/协议签订时间为准，提供内容须包含：合同/协议首页、签字盖章页、体现工作内容主要页。合同/协议的甲方签订主体应是国央企总部或其所属物资公司、电商专业公司等，服务范围应为国央企全集团提供电商服务。\n"
                + "（2）提供大型集团型国央企在国家企业信用信息公示系统中的股权关系截图（需与供应商所投业绩中的服务对象匹配，如签约主体为大型集团型国央企所述的物资公司、电商专业公司等，需提供从大型集团型国央企至签约主体公司的股权关系截图），无法证明甲方为央企、省属国企的业绩不予认可。";

        assertThat(policy.categorize(dim, detail)).isEqualTo(KnowledgeCategoryPolicy.CATEGORY_PROJECT);
        assertThat(policy.extractKeywords(dim, detail, 6)).contains("中央企业", "央企", "国企", "办公用品", "电商", "电子商城");
        assertThat(policy.extractSignedAfter(detail)).isEqualTo(java.time.LocalDate.of(2023, 1, 1));
        assertThat(policy.extractProjectRequiredCount(detail, new java.math.BigDecimal("15"))).isEqualTo(8);
    }

    @Test
    @DisplayName("项目22 模型仅返回 business 维度时，明确业绩阶梯规则仍归类为 PROJECT")
    void categorize_project22_genericModelDimension_usesExecutablePerformanceRule() {
        String detail = "3.2.3\n（1）\n商务评分\n标准\n供应商业绩\n（7分）\n"
                + "供应商具有1\n项大型集团型国央企电子商城办公用品服务业绩的不得分，每\n"
                + "增加1个得1分，最高得7分。";

        assertThat(policy.categorize("business", detail))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_PROJECT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 根因 1：多级管道符维度解构（项目 30/33 生产失效样本）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("根因1 项目30 ID:147 — dim=商务部分 detail首行为'业绩|...' → PROJECT")
    void categorize_genericHeader_pipeBusinessPerformance_project() {
        assertThat(policy.categorize("商务部分",
                "业绩 | 近三年央企相关业绩，每项得2分，最高6分。"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_PROJECT);
    }

    @Test
    @DisplayName("根因1 项目30 ID:149 — dim=商务部分 detail首行为'企业资质与体系认证|...' → CERT")
    void categorize_genericHeader_pipeCert_cert() {
        assertThat(policy.categorize("商务部分",
                "企业资质与体系认证 | ISO 9001 质量管理体系认证证书"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_CERT);
    }

    @Test
    @DisplayName("根因1 项目30 ID:153 — dim=技术服务部分 detail首行为'商品授权|...' → BRAND")
    void categorize_genericHeader_pipeBrand_brand() {
        assertThat(policy.categorize("技术服务部分",
                "商品授权 | 生产厂家全国授权证书，满足要求得5分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_BRAND);
    }

    @Test
    @DisplayName("根因1 项目30 ID:158 — dim=技术服务部分 detail首行为'仓储保障能力|...' → WAREHOUSE")
    void categorize_genericHeader_pipeWarehouse_warehouse() {
        assertThat(policy.categorize("技术服务部分",
                "仓储保障能力 | 设有自营库房，面积不少于3000平方米，得3分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE);
    }

    @Test
    @DisplayName("根因1 项目33 ID:119 — dim='商务 部分' detail含换行管道符 → CERT")
    void categorize_genericHeader_pipeWithNewline_cert() {
        assertThat(policy.categorize("商务 部分",
                "体系管\n理 | ISO9001 体系认证证书"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_CERT);
    }

    @Test
    @DisplayName("根因1 dim=技术部分 detail无管道符且无方案字样（含授权/人员词）→ 严格保持 OTHER 降级保护")
    void categorize_genericHeader_noPipe_noSchemeKeyword_staysOther() {
        // 无管道符时，即使细则出现“厂家授权”、“团队人员”，也不得误判为 BRAND 或 PERSON
        assertThat(policy.categorize("技术部分",
                "投标响应说明：必须按照招标文件要求落实各项组织举措，取得厂家授权书并委派专业团队"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);
    }

    @Test
    @DisplayName("根因1 泛表头有管道符分列：首列为描述项时严格判 OTHER，禁止穿透读右侧'授权'等细则词")
    void categorize_genericHeader_pipeCandidateOther_staysOther_doesNotLeakDetailWords() {
        // 首列为“售后服务承诺”，细则右侧含“授权”、“人员”打分说明，必须保持 OTHER，不能判为 BRAND 或 PERSON
        assertThat(policy.categorize("商务部分",
                "售后服务承诺 | 供应商应提供由生产厂家授权出具的全国联保承诺书及专职服务团队人员名单"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 根因 4：FINANCIAL / CREDIT 新分类
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("根因4 项目22 ID:68 — dim=商务评分 detail含现金流量净额 → FINANCIAL")
    void categorize_financialKeyword_cashflow_financial() {
        assertThat(policy.categorize("商务评分",
                "供应商近三年现金流量净额为正的得3分，须提供审计报告"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_FINANCIAL);
    }

    @Test
    @DisplayName("根因4 项目1119 ID:216 — dim=财务状况 → FINANCIAL")
    void categorize_dimFinancialStatus_financial() {
        assertThat(policy.categorize("财务状况",
                "速动比率=速动资产/流动负债，近三年均大于1的得满分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_FINANCIAL);
    }

    @Test
    @DisplayName("根因4 项目1119 ID:214 — dim=信用评价 → CREDIT")
    void categorize_dimCreditEvaluation_credit() {
        assertThat(policy.categorize("信用评价",
                "在该网站无不良记录，满足要求得100分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_CREDIT);
    }

    @Test
    @DisplayName("根因4 项目33 ID:120 — dim=纳税体系 → CREDIT")
    void categorize_dimTaxSystem_credit() {
        assertThat(policy.categorize("纳税体系",
                "年度评价为A级纳税信用的得满分，B级得50%分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_CREDIT);
    }

    @Test
    @DisplayName("根因4 财务评价维度含'授权'字样 → FINANCIAL 不进 BRAND（优先级守序）")
    void categorize_financialPriority_notBrand() {
        assertThat(policy.categorize("财务评价",
                "营业收入连续三年增长，得5分；须提供完整授权审计材料"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_FINANCIAL);
    }

    @Test
    @DisplayName("通用维度下普通合同数量阶梯不冒充项目业绩")
    void categorize_genericDimension_contractCountWithoutPerformance_staysOther() {
        assertThat(policy.categorize("business",
                "提交1份合同不得分，每增加1份得1分，最高得7分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);
    }

    @Test
    @DisplayName("通用维度下普通案例数量阶梯不冒充项目业绩")
    void categorize_genericDimension_caseCountWithoutPerformance_staysOther() {
        assertThat(policy.categorize("business",
                "提供1个案例不得分，每增加1个得1分，最高得7分"))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);
    }
}
