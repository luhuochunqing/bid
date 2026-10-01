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
}
