package com.xiyu.bid.competitionintel.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 竞争对手实体测试
 * 测试竞争对手实体的创建、字段验证和生命周期方法
 */
class CompetitorTest {

    @Test
    void createCompetitor_WithAllFields_ShouldSucceed() {
        // When
        Competitor competitor = Competitor.builder()
                .id(1L)
                .name("竞企A")
                .industry("建筑业")
                .strengths("资质齐全，技术实力强")
                .weaknesses("报价偏高，响应速度慢")
                .marketShare(new BigDecimal("25.5"))
                .typicalBidRangeMin(new BigDecimal("1000000"))
                .typicalBidRangeMax(new BigDecimal("1500000"))
                .createdAt(LocalDateTime.now())
                .build();

        // Then
        assertThat(competitor).isNotNull();
        assertThat(competitor.getId()).isEqualTo(1L);
        assertThat(competitor.getName()).isEqualTo("竞企A");
        assertThat(competitor.getIndustry()).isEqualTo("建筑业");
        assertThat(competitor.getStrengths()).isEqualTo("资质齐全，技术实力强");
        assertThat(competitor.getWeaknesses()).isEqualTo("报价偏高，响应速度慢");
        assertThat(competitor.getMarketShare()).isEqualByComparingTo("25.5");
        assertThat(competitor.getTypicalBidRangeMin()).isEqualByComparingTo("1000000");
        assertThat(competitor.getTypicalBidRangeMax()).isEqualByComparingTo("1500000");
        assertThat(competitor.getCreatedAt()).isNotNull();
    }

    @Test
    void createCompetitor_WithRequiredFieldsOnly_ShouldSucceed() {
        // When
        Competitor competitor = Competitor.builder()
                .name("竞企B")
                .build();

        // Then
        assertThat(competitor).isNotNull();
        assertThat(competitor.getName()).isEqualTo("竞企B");
        assertThat(competitor.getIndustry()).isNull();
        assertThat(competitor.getMarketShare()).isNull();
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Given
        Competitor competitor = new Competitor();

        // When
        competitor.setId(1L);
        competitor.setName("竞企C");
        competitor.setIndustry("制造业");
        competitor.setStrengths("成本控制好");
        competitor.setWeaknesses("创新能力弱");
        competitor.setMarketShare(new BigDecimal("15.3"));
        competitor.setTypicalBidRangeMin(new BigDecimal("800000"));
        competitor.setTypicalBidRangeMax(new BigDecimal("1200000"));
        competitor.setCreatedAt(LocalDateTime.of(2024, 3, 1, 10, 0));

        // Then
        assertThat(competitor.getId()).isEqualTo(1L);
        assertThat(competitor.getName()).isEqualTo("竞企C");
        assertThat(competitor.getIndustry()).isEqualTo("制造业");
        assertThat(competitor.getStrengths()).isEqualTo("成本控制好");
        assertThat(competitor.getWeaknesses()).isEqualTo("创新能力弱");
        assertThat(competitor.getMarketShare()).isEqualByComparingTo("15.3");
        assertThat(competitor.getTypicalBidRangeMin()).isEqualByComparingTo("800000");
        assertThat(competitor.getTypicalBidRangeMax()).isEqualByComparingTo("1200000");
        assertThat(competitor.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 3, 1, 10, 0));
    }

    @Test
    void noArgsConstructor_ShouldCreateEmptyCompetitor() {
        // When
        Competitor competitor = new Competitor();

        // Then
        assertThat(competitor).isNotNull();
        assertThat(competitor.getId()).isNull();
        assertThat(competitor.getName()).isNull();
        assertThat(competitor.getIndustry()).isNull();
        assertThat(competitor.getStrengths()).isNull();
        assertThat(competitor.getWeaknesses()).isNull();
        assertThat(competitor.getMarketShare()).isNull();
        assertThat(competitor.getTypicalBidRangeMin()).isNull();
        assertThat(competitor.getTypicalBidRangeMax()).isNull();
        assertThat(competitor.getCreatedAt()).isNull();
    }
}
