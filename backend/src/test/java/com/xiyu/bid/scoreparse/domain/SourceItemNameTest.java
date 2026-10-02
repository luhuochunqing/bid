package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 评审合同：引文声明名称单位（内部空白/符号原样保留），原文验证锚点唯一、逐字紧邻与显式左分隔符；不满足不恢复。 */
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
        // "（合计W分）"是分组小计（不计入同分值锚点唯一性），其右括号是合法左分隔符场景之外的独立结构
        assertThat(SourceItemName.recover("一级集采商品价格分（满分28分）", new BigDecimal("28"),
                "第一部分：一级集采商品（合计28分）+一级集采商品价格分（满分28分）=折扣率评分"))
                .contains("一级集采商品价格分");
    }

    @Test void multiItemQuoteIsAmbiguousAndNeverRecovered() {
        Optional<String> recovered = SourceItemName.recover(
                "一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分）", new BigDecimal("28"),
                "一级集采商品价格分（满分28分）+非一级集采商品价格分（满分12分）");
        assertThat(recovered).isEmpty();
    }

    @Test void suffixRelatedSameWeightNamesAreAmbiguousNotSameItem() {
        // 评审反例 1："非一级集采商品价格分"与"一级集采商品价格分"是两个同分值项 → 歧义不恢复
        String row = "价格分计算 一级集采商品价格分（满分12分） 非一级集采商品价格分（满分12分）";
        assertThat(SourceItemName.recover("一级集采商品价格分（满分12分）", new BigDecimal("12"), row)).isEmpty();
        assertThat(SourceItemName.recover("非一级集采商品价格分（满分12分）", new BigDecimal("12"), row)).isEmpty();
    }

    @Test void truncatedQuoteNameNeverOverwritesOriginalFullName() {
        // 评审反例 2：原文"供应商业绩（满分5分）"，引文只截"业绩（5分）"→ 名称左邻是名称字符"商"，
        // 不是显式左分隔符 → 不恢复，正式项名不得被改写为"业绩"
        String row = "商务评审 供应商业绩（满分5分） 提供合同扫描件";
        assertThat(SourceItemName.recover("业绩（5分）", new BigDecimal("5"), row)).isEmpty();
    }

    @Test void punctuationInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 3a（二次评审）：顿号是名称成分——引文声明单位后原样保留，行侧只验证邻接
        String row = "资格评审 质量、环境管理体系认证（满分5分） 提供证书扫描件";
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"), row))
                .contains("质量、环境管理体系认证");
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"),
                "质量、环境管理体系认证（满分5分）")).contains("质量、环境管理体系认证");
    }

    @Test void overLengthItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 3b（二次评审）：>24 字完整名称不再有字符类上限截断
        String longName = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十"; // 30 字
        assertThat(longName).hasSize(30);
        assertThat(SourceItemName.recover(longName + "（满分3分）", new BigDecimal("3"),
                "评分 " + longName + "（满分3分） 说明")).contains(longName);
    }

    @Test void numberedPunctuationStartIsStillACompleteName() {
        // "1、xxx" 式序号是编号结构：左邻 '、' 前有数字时跳过序号再证分隔符
        assertThat(SourceItemName.recover("一级集采商品上架折扣率（28分）", new BigDecimal("28"),
                "1、一级集采商品上架折扣率（28分）：报价表-1.1")).contains("一级集采商品上架折扣率");
    }

    @Test void slashInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4a（四次评审）：斜杠名称内部成立（引文声明单位），'/' 只被禁止作为左分隔符
        assertThat(SourceItemName.recover("质量/环境管理体系认证（满分5分）", new BigDecimal("5"),
                "资格要求 质量/环境管理体系认证（满分5分） 提供认证证书"))
                .contains("质量/环境管理体系认证");
        assertThat(SourceItemName.recover("A/B方案对比（满分4分）", new BigDecimal("4"),
                "评分标准 A/B方案对比（满分4分） 择优")).contains("A/B方案对比");
    }

    @Test void bracketInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4b（四次评审）：正文括号属于名称，引文单位原样恢复
        assertThat(SourceItemName.recover("ISO9001（质量管理体系）认证（满分5分）", new BigDecimal("5"),
                "ISO9001（质量管理体系）认证（满分5分）")).contains("ISO9001（质量管理体系）认证");
        assertThat(SourceItemName.recover("ISO9001认证（满分5分）", new BigDecimal("5"),
                "证书要求 ISO9001认证（满分5分）")).contains("ISO9001认证");
    }

    @Test void hyphenInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4c（四次评审）：连字符名称内部成立；'-' 不作为左分隔符，
        // 所以从 "ISO-9001…" 中部截短的引文 "9001…（5分）" 因左邻 '-' 被拒绝
        assertThat(SourceItemName.recover("ISO-9001质量管理体系认证（满分5分）", new BigDecimal("5"),
                "评分标准 ISO-9001质量管理体系认证（满分5分） 提供证书"))
                .contains("ISO-9001质量管理体系认证");
        assertThat(SourceItemName.recover("9001质量管理体系认证（满分5分）", new BigDecimal("5"),
                "评分标准 ISO-9001质量管理体系认证（满分5分） 提供证书")).isEmpty();
    }

    @Test void colonInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 5a（五次评审）：冒号属于名称（ISO9001:2015），引文单位原样恢复
        assertThat(SourceItemName.recover("ISO9001:2015质量管理体系认证（满分5分）", new BigDecimal("5"),
                "ISO9001:2015质量管理体系认证（满分5分）")).contains("ISO9001:2015质量管理体系认证");
        assertThat(SourceItemName.recover("ISO9001:2015质量管理体系认证（满分5分）", new BigDecimal("5"),
                "资格评审 ISO9001:2015质量管理体系认证（满分5分） 提供证书"))
                .contains("ISO9001:2015质量管理体系认证");
    }

    @Test void plusInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 5b（五次评审）：名称内部加号保留（C++）；'+' 作为左分隔符只作用于引文单位之外
        assertThat(SourceItemName.recover("C++软件开发能力（满分4分）", new BigDecimal("4"),
                "C++软件开发能力（满分4分）")).contains("C++软件开发能力");
        assertThat(SourceItemName.recover("C++软件开发能力（满分4分）", new BigDecimal("4"),
                "技术评审 C++软件开发能力（满分4分） 上机测试")).contains("C++软件开发能力");
    }

    @Test void quoteTruncatedMidParenGroupIsNotProvableAndRejected() {
        // 引文从名称中间的正文括号截断 → 左邻是 '（'（名称内部字符）→ 不恢复（同源截断防线）
        assertThat(SourceItemName.recover("质量管理体系）认证（满分5分）", new BigDecimal("5"),
                "ISO9001（质量管理体系）认证（满分5分）")).isEmpty();
    }

    @Test void anchorBrokenByCellLineWrapIsStillAStructuralBoundary() {
        // 1382 真实单元格换行把锚点拆成 "（满分28\n分）"——锚点识别与逐字对齐都容忍散落空白，
        // 否则 12 分项引文无法与行内锚点邻接对齐，Task291 恢复失败
        String row = "办公用品价格分（满分40分）=一级集采商品价格分（满分28\n分）+非一级集采商品价格分（满分12分） 注：按3个月账期";
        assertThat(SourceItemName.recover("非一级集采商品价格分（满分12分）", new BigDecimal("12"), row))
                .contains("非一级集采商品价格分");
    }

    @Test void internalSpaceInItemNameIsPreservedNotSplit() {
        // 评审反例 6a（六次评审）："ISO 9001质量管理体系认证" 名称内部空格是成分，
        // 不得当结构边界截掉前半——恢复值就是引文自身声明的单位
        assertThat(SourceItemName.recover("ISO 9001质量管理体系认证（满分5分）", new BigDecimal("5"),
                "ISO 9001质量管理体系认证（满分5分）")).contains("ISO 9001质量管理体系认证");
        assertThat(SourceItemName.recover("ISO 9001质量管理体系认证（满分5分）", new BigDecimal("5"),
                "资格评审 ISO 9001质量管理体系认证（满分5分） 提供证书"))
                .contains("ISO 9001质量管理体系认证");
    }

    @Test void latinWordSpaceInsideItemNameIsPreserved() {
        // 评审反例 6b（六次评审）："Technical Support服务能力" 英文词间空格保留
        assertThat(SourceItemName.recover("Technical Support服务能力（满分6分）", new BigDecimal("6"),
                "技术评审 Technical Support服务能力（满分6分） 现场演示"))
                .contains("Technical Support服务能力");
    }

    @Test void trailingSymbolInItemNameIsMeaningfulAndNeverStripped() {
        // 评审反例 6c（六次评审）："信用评级AA+" 末尾 '+' 承载评级含义，不得无条件剥离
        assertThat(SourceItemName.recover("信用评级AA+（满分3分）", new BigDecimal("3"),
                "信用评级AA+（满分3分）")).contains("信用评级AA+");
        assertThat(SourceItemName.recover("信用评级AA+（满分3分）", new BigDecimal("3"),
                "商务评审 信用评级AA+（满分3分） 以评级报告为准")).contains("信用评级AA+");
    }
}
