package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRegionalScorePolicyTest {

    private static final String CLAUSE_1117 = """
            1、评审标准: 1) 投标人需提供指定六大地区内各一个配送仓库，得15分，缺一个区域扣3分。（①华北地区（北京市、天津市、河北省、山西省、内蒙古）②东北地区（辽宁省、吉林省、黑龙江省）③华东地区（上海市、江苏省、浙江省、安徽省、江西省、福建省、山东省）④中南地区（河南省、湖北省、湖南省、广东省、海南省、广西壮族自治区）⑤西南地区（重庆市、四川省、贵州省、云南省）⑥西北地区（青海省、甘肃省、宁夏省））。2）六大区域每多一个仓库得1分，满分5分；3）按投标人自有及租赁仓储面积（中华人民共和国境内）进行排序，第一名得分为5分，第二名3分，第三名1分，其余不得分。
            """;

    @Test
    void isMultiRegionWarehouseClause_identifiesSixRegions() {
        assertThat(WarehouseRegionalScorePolicy.isMultiRegionWarehouseClause(CLAUSE_1117)).isTrue();
        assertThat(WarehouseRegionalScorePolicy.isMultiRegionWarehouseClause("普通仓库面积5000平米")).isFalse();
    }

    @Test
    void evaluate_allSixRegionsCoveredWithExtraWarehouses_calculatesTwentyPoints() {
        List<WarehouseMatchedItem> warehouses = List.of(
                new WarehouseMatchedItem(1L, "北京仓", "华北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(2L, "沈阳仓", "东北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(3L, "上海仓", "华东", new BigDecimal("5000"), "IN_USE"),
                new WarehouseMatchedItem(4L, "广州仓", "中南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(5L, "成都仓", "西南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(6L, "西安仓", "西北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(7L, "苏州仓", "华东", new BigDecimal("4000"), "IN_USE"),
                new WarehouseMatchedItem(8L, "武汉仓", "中南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(9L, "天津仓", "华北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(10L, "重庆仓", "西南", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(11L, "杭州仓", "华东", new BigDecimal("2000"), "IN_USE")
        );

        var result = WarehouseRegionalScorePolicy.evaluate(CLAUSE_1117, new BigDecimal("25"), warehouses);

        assertThat(result.coveredRegions()).isEqualTo(6);
        assertThat(result.score()).isEqualByComparingTo("20");
        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.kbHit()).isTrue();
        assertThat(result.basis()).contains("覆盖六大区域中 6 个地区", "基础得分 15 分", "增分 5 分", "另 5 分仓储面积横向排序");
    }

    @Test
    void evaluate_partialRegions_deductsThreePointsPerMissingRegion() {
        // 缺西北和东北，共 4 个大区
        List<WarehouseMatchedItem> warehouses = List.of(
                new WarehouseMatchedItem(1L, "北京仓", "华北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(3L, "上海仓", "华东", new BigDecimal("5000"), "IN_USE"),
                new WarehouseMatchedItem(4L, "广州仓", "中南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(5L, "成都仓", "西南", new BigDecimal("3000"), "IN_USE")
        );

        var result = WarehouseRegionalScorePolicy.evaluate(CLAUSE_1117, new BigDecimal("25"), warehouses);

        assertThat(result.coveredRegions()).isEqualTo(4);
        // 15 - (6 - 4) * 3 = 9 分
        assertThat(result.score()).isEqualByComparingTo("9");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.basis()).contains("覆盖六大区域中 4 个地区", "基础得分 9 分");
    }
}
