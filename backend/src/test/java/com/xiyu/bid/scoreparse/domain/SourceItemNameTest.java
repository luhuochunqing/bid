package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 评审合同：项身份必须由原文唯一锚点证明；同分值歧义、无锚点引文一律不恢复。 */
class SourceItemNameTest {

    @Test void recoversCanonicalNameFromUniqueFullScoreAnchor() {
        String row = "办公用品价格分（满分40分）=一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分） 注：按3个月账期";
        assertThat(SourceItemName.recover("非一级集采商品价格分（满分12分）", new BigDecimal("12"), row))
                .contains("非一级集采商品价格分");
    }

    @Test void rejectsWhenAnotherItemNameClaimsSameWeightInRow() {
        // 评审反例：供应商履约业绩 冒充 供应商履约能力（同一行两项都是 5 分）→ 歧义，不恢复
        String row = "供应商履约能力（满分5分）按季度评价 供应商履约业绩（满分5分）按合同评价";
        assertThat(SourceItemName.recover("供应商履约能力（满分5分）按季度评价", new BigDecimal("5"), row))
                .isEmpty();
    }

    @Test void rejectsQuoteWithoutSelfDeclaredAnchorOrMismatchedWeight() {
        assertThat(SourceItemName.recover("每份证书得2分，最高10分", new BigDecimal("10"),
                "证书 每份证书得2分，最高10分 10分")).isEmpty();
        assertThat(SourceItemName.recover("供应商业绩（满分7分）", new BigDecimal("3"),
                "供应商业绩（满分7分）")).isEmpty();
    }

    @Test void groupSubtotalMarkersAreNotItemAnchors() {
        // "（合计W分）"是分组小计，不构成同分值歧义
        assertThat(SourceItemName.recover("一级集采商品价格分（满分28分）", new BigDecimal("28"),
                "第一部分：一级集采商品（合计28分） 一级集采商品价格分（满分28分）=折扣率评分")).isPresent();
    }

    @Test void multiItemQuoteIsAmbiguousAndNeverRecovered() {
        Optional<String> recovered = SourceItemName.recover(
                "一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分）", new BigDecimal("28"),
                "一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分）");
        assertThat(recovered).isEmpty();
    }

    @Test void suffixRelatedSameWeightNamesAreAmbiguousNotSameItem() {
        // 评审反例 1："非一级集采商品价格分".endsWith("一级集采商品价格分") 纯属后缀巧合，
        // 同行两个同分值项 → 身份歧义，任何一侧引文都不得恢复
        String row = "价格分计算 一级集采商品价格分（满分12分） 非一级集采商品价格分（满分12分）";
        assertThat(SourceItemName.recover("一级集采商品价格分（满分12分）", new BigDecimal("12"), row)).isEmpty();
        assertThat(SourceItemName.recover("非一级集采商品价格分（满分12分）", new BigDecimal("12"), row)).isEmpty();
    }

    @Test void truncatedQuoteNameNeverOverwritesOriginalFullName() {
        // 评审反例 2：原文"供应商业绩（满分5分）"，引文只截"业绩（5分）"→ 短名不是唯一锚点，
        // 不恢复，正式项名不得被改写为"业绩"
        String row = "商务评审 供应商业绩（满分5分） 提供合同扫描件";
        assertThat(SourceItemName.recover("业绩（5分）", new BigDecimal("5"), row)).isEmpty();
    }

    @Test void punctuationInsideItemNameIsNotProvableAndNeverTruncates() {
        // 评审反例 3a（二次评审）：名称含顿号"质量、环境管理体系认证"，正则字符类不含顿号，
        // 引文与行会截出同一段残文"环境管理体系认证"，逐字全等不设防 → 必须证明完整名，不恢复
        String row = "资格评审 质量、环境管理体系认证（满分5分） 提供证书扫描件";
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"), row)).isEmpty();
        // 名称内顿号即使单格出现也不恢复（引文侧同样无法证明完整名）
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"),
                "质量、环境管理体系认证（满分5分）")).isEmpty();
    }

    @Test void overLengthItemNameIsNotProvableAndNeverTruncates() {
        // 评审反例 3b（二次评审）：超过 24 字的完整名称，正则从名字中间起匹配只留末 24 字 → 不恢复
        String longName = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十"; // 30 字
        assertThat(longName).hasSize(30);
        assertThat(SourceItemName.recover(longName + "（满分3分）", new BigDecimal("3"),
                "评分 " + longName + "（满分3分） 说明")).isEmpty();
    }

    @Test void numberedPunctuationStartIsStillACompleteName() {
        // "1、xxx" 式序号后是合法名称起点，不得被顿号规则误杀（Task291 行内裸锚点保持可辨识）
        assertThat(SourceItemName.recover("一级集采商品上架折扣率（28分）", new BigDecimal("28"),
                "1、一级集采商品上架折扣率（28分）：报价表-1.1")).isPresent();
    }
}
