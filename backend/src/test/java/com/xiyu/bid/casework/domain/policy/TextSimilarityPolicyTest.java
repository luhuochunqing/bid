package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TextSimilarityPolicyTest {

    @Test
    @DisplayName("顿号分层解析：从复杂的招投标条款中精准提取并列商业实体，过滤引导前缀与泛化尾缀")
    void testExtractPunctuationEntities_EnumerationComma() {
        String detail = "对供应商商城系统对接能力进行综合评价，供应商应根据工会规定定义的接口标准进行对接，使商城接口标准向职工商城采购接口服务，包括商品、商品组、订单、售后、对账等";

        Set<String> entities = TextSimilarityPolicy.extractPunctuationEntities(detail);

        assertThat(entities).contains("商品", "商品组", "订单", "售后", "对账");
        assertThat(entities).doesNotContain("包括商品", "对账等", "包括", "等");
        assertThat(entities).noneMatch(e -> e.contains("双的") || e.contains("的标") || e.contains("可编"));
    }

    @Test
    @DisplayName("分号与并列连词分层解析：精准提取独立技术要求与与/及/和并列实体，避免误切及时响应与涉及范围")
    void testExtractPunctuationEntities_SemicolonsAndConjunctions() {
        String text = "具备高可用微服务架构；支持同城双活与动态扩容；提供7x24小时应急保障；具备应急方案与及时响应能力；涉及范围及实施计划；";

        Set<String> entities = TextSimilarityPolicy.extractPunctuationEntities(text);

        assertThat(entities).contains("高可用微服务架构", "同城双活", "动态扩容", "7x24小时应急保障", "应急方案", "及时响应", "涉及范围", "实施计划");
        assertThat(entities).doesNotContain("具备高可用微服务架构", "支持同城双活", "提供7x24小时应急保障", "时响应", "涉");
    }

    @Test
    @DisplayName("专有名词引号提取：优先识别双引号、书名号等包裹的系统或核心概念")
    void testExtractPunctuationEntities_Quotes() {
        String text = "供应商按照“三峡e购”商城定义的接口标准进行对接，提供“实时库存对账”与“电子发票”服务。";

        Set<String> entities = TextSimilarityPolicy.extractPunctuationEntities(text);

        assertThat(entities).contains("三峡e购", "实时库存对账", "电子发票");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "双的", "的标", "可编", "向职", "为职", "以防", "由于", "了", "的", "在", "包括", "采购", "有限公司", "等", "及", "与"
    })
    @DisplayName("实体质量守卫测试：严厉拒绝滑窗碎片、助词前后缀、停用词")
    void testIsQualityEntity_RejectsInvalidFragments(String invalidFragment) {
        assertThat(TextSimilarityPolicy.isQualityEntity(invalidFragment)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "商品", "商品组", "订单", "售后", "对账", "双活容灾", "动态扩容", "三峡e购", "微服务架构", "高可用设计",
            "性能", "化工", "可用", "可靠", "可行",
            "需求分析", "应用架构", "应急响应", "在线支付", "在线商城", "华为", "以太网", "等保三级",
            "华为云", "在途物资", "等级保护三级", "等级认证"
    })
    @DisplayName("实体质量守卫测试：放行高质量商业、技术实体及合法前缀后缀专有名词（需求分析/在线支付/华为等）")
    void testIsQualityEntity_AcceptsQualityEntities(String validEntity) {
        assertThat(TextSimilarityPolicy.isQualityEntity(validEntity)).isTrue();
    }

    @Test
    @DisplayName("噪音清洗测试：去除需/应字符截断bug，需求分析/应用架构/应急响应完整保留")
    void testCleanEntityToken_PreservesCoreWordsWithoutTruncation() {
        assertThat(TextSimilarityPolicy.cleanEntityToken("需求分析")).isEqualTo("需求分析");
        assertThat(TextSimilarityPolicy.cleanEntityToken("应用架构")).isEqualTo("应用架构");
        assertThat(TextSimilarityPolicy.cleanEntityToken("应急响应")).isEqualTo("应急响应");
        assertThat(TextSimilarityPolicy.cleanEntityToken("提供需求分析")).isEqualTo("需求分析");
        assertThat(TextSimilarityPolicy.cleanEntityToken("具备应急响应能力")).isEqualTo("应急响应");
        assertThat(TextSimilarityPolicy.cleanEntityToken("在线支付对接能力")).isEqualTo("在线支付对接");
        assertThat(TextSimilarityPolicy.cleanEntityToken("华为技术")).isEqualTo("华为技术");
    }

    @ParameterizedTest
    @CsvSource({
            "'ISO9001 质量管理体系认证', 'ISO9001 质量管理体系认证'",
            "'CMMI 软件能力成熟度认证', 'CMMI 软件能力成熟度认证'",
            "'B2B 电商交易平台', 'B2B 电商交易平台'",
            "'ERP 系统对接方案', 'ERP 系统对接方案'",
            "'AI 智能算法开发', 'AI 智能算法开发'",
            "'ISO9001:2015 质量管理体系认证', 'ISO9001:2015 质量管理体系认证'",
            "'A1. 技术方案', '技术方案'",
            "'A1、 技术方案', '技术方案'",
            "'B2: 售后服务方案', '售后服务方案'"
    })
    @DisplayName("标题清洗器：英文缩写与品牌名（ISO9001, CMMI, B2B, ERP, AI）后接空格不被误剥离，带标点的章节编号精准剥离")
    void testCleanTitle_PreservesEnglishAbbreviations(String raw, String expected) {
        assertThat(TextSimilarityPolicy.cleanTitle(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("增强分词测试：优先包含标点并列实体，且绝不产生助词切词碎末")
    void testExtractTokens_PrioritizesPunctuationEntitiesWithoutNoise() {
        String text = "系统架构高可用设计，支持双活容灾与动态扩容能力，包括商品、订单、售后、对账等";

        Set<String> tokens = TextSimilarityPolicy.extractTokens(text);

        assertThat(tokens).contains("商品", "订单", "售后", "对账", "双活容灾", "动态扩容");
        assertThat(tokens).noneMatch(t -> t.startsWith("的") || t.endsWith("的") || t.equals("双的") || t.equals("可编"));
    }

    @Test
    @DisplayName("边界测试：null 与空文本安全防护")
    void testEdgeCases_NullAndBlank() {
        assertThat(TextSimilarityPolicy.extractPunctuationEntities((String[]) null)).isEmpty();
        assertThat(TextSimilarityPolicy.extractPunctuationEntities("")).isEmpty();
        assertThat(TextSimilarityPolicy.extractPunctuationEntities("   ", "\t\n")).isEmpty();
        assertThat(TextSimilarityPolicy.extractTokens(null)).isEmpty();
        assertThat(TextSimilarityPolicy.extractTokens("")).isEmpty();
        assertThat(TextSimilarityPolicy.isQualityEntity(null)).isFalse();
        assertThat(TextSimilarityPolicy.isQualityEntity("")).isFalse();
        assertThat(TextSimilarityPolicy.cleanEntityToken(null)).isEmpty();
    }

    @Test
    @DisplayName("Jaccard相似度测试：验证经典集合交并比计算")
    void testJaccardSimilarity() {
        Set<String> a = Set.of("微服务", "高可用", "双活");
        Set<String> b = Set.of("微服务", "高可用", "容灾");

        double sim = TextSimilarityPolicy.jaccardSimilarity(a, b);
        assertThat(sim).isEqualTo(0.5);

        assertThat(TextSimilarityPolicy.jaccardSimilarity(null, b)).isEqualTo(0.0);
        assertThat(TextSimilarityPolicy.jaccardSimilarity(a, Set.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("逗号分层解析测试：无顿号的复杂并列从句按逗号统一分词，严禁逗号泄漏至单一实体中")
    void testExtractPunctuationEntities_CommaSeparatedClausesWithoutEnumComma() {
        String text = "支持同城双活与动态扩容，具备应急方案与及时响应能力";

        Set<String> entities = TextSimilarityPolicy.extractPunctuationEntities(text);

        assertThat(entities).contains("同城双活", "动态扩容", "应急方案", "及时响应");
        assertThat(entities).noneMatch(e -> e.contains("，") || e.contains(",") || e.contains("；"));
    }

    @Test
    @DisplayName("实体质量守卫测试：严厉拒绝内部含标点符号的非纯净碎片")
    void testIsQualityEntity_RejectsPunctuationInsideToken() {
        assertThat(TextSimilarityPolicy.isQualityEntity("动态扩容，具备应急方案")).isFalse();
        assertThat(TextSimilarityPolicy.isQualityEntity("商品、订单")).isFalse();
        assertThat(TextSimilarityPolicy.isQualityEntity("高可用；微服务")).isFalse();
        assertThat(TextSimilarityPolicy.isQualityEntity("容灾。双活")).isFalse();
    }

    @Test
    @DisplayName("根因复现与防回归测试：项目名称与客户名称提取专属实体时，严禁暴力 2-gram 机械滑窗切词")
    void testExtractEntityTokens_NoMechanicalTwoGramSlicing() {
        Set<String> entities = TextSimilarityPolicy.extractEntityTokens(
                "朱双双的标讯不可编辑",
                "苏州胶囊有限公司"
        );

        // 核心实体提取正确
        assertThat(entities).contains("朱双双", "苏州胶囊");

        // 绝不包含 2-gram 机械切片碎片或噪音
        assertThat(entities).doesNotContain(
                "朱双", "双双", "双的", "的标", "标讯", "讯不", "不可", "编辑", "不可编辑", "州胶", "公司", "有限公司"
        );
    }
}

