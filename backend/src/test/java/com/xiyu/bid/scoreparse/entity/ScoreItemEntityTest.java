package com.xiyu.bid.scoreparse.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemEntityTest {

    @Test
    @DisplayName("ScoreItem 实体支持 deliverableType 与 scoringMode 字段的构建与读取")
    void testScoreItemDeliverableTypeAndScoringMode() {
        ScoreItem item = ScoreItem.builder()
                .id(1L)
                .projectId(100L)
                .parseTaskId(200L)
                .itemIndex(1)
                .code("A1")
                .dim("商品丰富性")
                .detail("自营商品SKU数量由多到少排序")
                .weight(new BigDecimal("4.00"))
                .scoreType("OBJECTIVE")
                .statusStage1("OK")
                .deliverableType("SYSTEM_SCREENSHOT")
                .scoringMode("QUANTITATIVE_RANKING")
                .build();

        assertThat(item.getDeliverableType()).isEqualTo("SYSTEM_SCREENSHOT");
        assertThat(item.getScoringMode()).isEqualTo("QUANTITATIVE_RANKING");

        item.setDeliverableType("TEXT_PROPOSAL");
        item.setScoringMode("SUBJECTIVE_REVIEW");

        assertThat(item.getDeliverableType()).isEqualTo("TEXT_PROPOSAL");
        assertThat(item.getScoringMode()).isEqualTo("SUBJECTIVE_REVIEW");
    }

    @Test
    @DisplayName("存量历史数据兼容性：deliverableType 与 scoringMode 默认为 null")
    void testLegacyScoreItemDefaultsToNull() {
        ScoreItem legacyItem = ScoreItem.builder()
                .id(2L)
                .projectId(38L)
                .parseTaskId(201L)
                .itemIndex(1)
                .code("B1")
                .dim("商品管理")
                .detail("具备商品管理方案")
                .weight(new BigDecimal("5.00"))
                .scoreType("SUBJECTIVE")
                .statusStage1("PENDING")
                .build();

        assertThat(legacyItem.getDeliverableType()).isNull();
        assertThat(legacyItem.getScoringMode()).isNull();
    }
}
