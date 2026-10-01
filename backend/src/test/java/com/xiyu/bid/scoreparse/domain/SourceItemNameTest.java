package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 评审合同：项身份必须由原文唯一锚点证明；完整名由锚点回溯到结构边界的极大跨度证明，歧义不恢复。 */
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
        // "（合计W分）"是分组小计（结构边界），不构成同分值项锚点，其右括号终止名称跨度
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
        // 评审反例 1："非一级集采商品价格分"与"一级集采商品价格分"是两个同分值项，
        // 结构跨度逐字不同 → 同行双同分值锚点歧义，任何一侧引文都不得恢复
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

    @Test void punctuationInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 3a（二次/五次评审）：顿号是名称内部字符——锚点回溯到结构边界（空白/单元格起点）
        // 的极大跨度完整保留"质量、环境管理体系认证"，任何截短覆盖都不再可能
        String row = "资格评审 质量、环境管理体系认证（满分5分） 提供证书扫描件";
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"), row))
                .contains("质量、环境管理体系认证");
        assertThat(SourceItemName.recover("质量、环境管理体系认证（满分5分）", new BigDecimal("5"),
                "质量、环境管理体系认证（满分5分）")).contains("质量、环境管理体系认证");
    }

    @Test void overLengthItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 3b（二次评审）：>24 字完整名称由结构跨度自然容纳，不再只保留末段
        String longName = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十"; // 30 字
        assertThat(longName).hasSize(30);
        assertThat(SourceItemName.recover(longName + "（满分3分）", new BigDecimal("3"),
                "评分 " + longName + "（满分3分） 说明")).contains(longName);
    }

    @Test void numberedPunctuationStartIsStillACompleteName() {
        // "1、xxx" 式序号是编号结构不是名称成分，剥离后与引文跨度全等 → 恢复
        assertThat(SourceItemName.recover("一级集采商品上架折扣率（28分）", new BigDecimal("28"),
                "1、一级集采商品上架折扣率（28分）：报价表-1.1")).contains("一级集采商品上架折扣率");
    }

    @Test void slashInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4a（四次评审）：斜杠属于名称内部字符，结构跨度完整保留，两侧全等才恢复
        assertThat(SourceItemName.recover("质量/环境管理体系认证（满分5分）", new BigDecimal("5"),
                "资格要求 质量/环境管理体系认证（满分5分） 提供认证证书"))
                .contains("质量/环境管理体系认证");
        assertThat(SourceItemName.recover("A/B方案对比（满分4分）", new BigDecimal("4"),
                "评分标准 A/B方案对比（满分4分） 择优")).contains("A/B方案对比");
    }

    @Test void bracketInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4b（四次评审）：正文括号"（质量管理体系）"不是分数锚点，属于名称内部，
        // 回溯遇非边界括号继续包含 → 完整恢复
        assertThat(SourceItemName.recover("ISO9001（质量管理体系）认证（满分5分）", new BigDecimal("5"),
                "ISO9001（质量管理体系）认证（满分5分）")).contains("ISO9001（质量管理体系）认证");
        assertThat(SourceItemName.recover("ISO9001认证（满分5分）", new BigDecimal("5"),
                "证书要求 ISO9001认证（满分5分）")).contains("ISO9001认证");
    }

    @Test void hyphenInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 4c（四次评审）：连字符属于名称（ISO-9001），跨度完整保留
        assertThat(SourceItemName.recover("ISO-9001质量管理体系认证（满分5分）", new BigDecimal("5"),
                "评分标准 ISO-9001质量管理体系认证（满分5分） 提供证书"))
                .contains("ISO-9001质量管理体系认证");
    }

    @Test void colonInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 5a（五次评审）：冒号既可能是句读也可能是标准号成分（ISO9001:2015）——
        // 起点字符判定无法区分，结构回溯把冒号留在名称跨度内，两侧全等才恢复
        assertThat(SourceItemName.recover("ISO9001:2015质量管理体系认证（满分5分）", new BigDecimal("5"),
                "ISO9001:2015质量管理体系认证（满分5分）")).contains("ISO9001:2015质量管理体系认证");
        assertThat(SourceItemName.recover("ISO9001:2015质量管理体系认证（满分5分）", new BigDecimal("5"),
                "资格评审 ISO9001:2015质量管理体系认证（满分5分） 提供证书"))
                .contains("ISO9001:2015质量管理体系认证");
    }

    @Test void plusInsideItemNameIsRecoveredInFullNotTruncated() {
        // 评审反例 5b（五次评审）：加号既是公式连接符也可能是语言名（C++）——内部加号保留，
        // 仅剥离跨度首尾的连接符（Task291 "…（28分）+非一级…" 的前导 '+'）
        assertThat(SourceItemName.recover("C++软件开发能力（满分4分）", new BigDecimal("4"),
                "C++软件开发能力（满分4分）")).contains("C++软件开发能力");
        assertThat(SourceItemName.recover("C++软件开发能力（满分4分）", new BigDecimal("4"),
                "技术评审 C++软件开发能力（满分4分） 上机测试")).contains("C++软件开发能力");
    }

    @Test void quoteTruncatedMidParenGroupIsNotProvableAndRejected() {
        // 引文从名称中间的正文括号截断 → 行侧完整跨度 ≠ 引文残段 → 不恢复（同源截断防线）
        assertThat(SourceItemName.recover("质量管理体系）认证（满分5分）", new BigDecimal("5"),
                "ISO9001（质量管理体系）认证（满分5分）")).isEmpty();
    }

    @Test void anchorBrokenByCellLineWrapIsStillAStructuralBoundary() {
        // 1382 真实单元格换行把锚点拆成 "（满分28\n分）"——该锚点仍是结构边界，
        // 否则 12 分项名称跨度会粘连整段公式导致 Task291 恢复失败
        String row = "办公用品价格分（满分40分）=一级集采商品价格分（满分28\n分）+非一级集采商品价格分（满分12分） 注：按3个月账期";
        assertThat(SourceItemName.recover("非一级集采商品价格分（满分12分）", new BigDecimal("12"), row))
                .contains("非一级集采商品价格分");
    }
}
