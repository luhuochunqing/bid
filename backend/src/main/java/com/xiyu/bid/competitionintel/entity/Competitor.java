package com.xiyu.bid.competitionintel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞争对手实体
 * 管理竞争对手信息，包括基本情况、优势劣势、市场份额、投标范围等
 */
@Entity
@Table(name = "competitors", indexes = {
    @Index(name = "idx_competitor_name", columnList = "name"),
    @Index(name = "idx_competitor_industry", columnList = "industry")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Competitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 竞争对手名称
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 所属行业
     */
    @Column(length = 100)
    private String industry;

    /**
     * 竞争优势
     */
    @Column(columnDefinition = "TEXT")
    private String strengths;

    /**
     * 竞争劣势
     */
    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    /**
     * 市场份额（百分比）
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal marketShare;

    /**
     * 典型投标范围最小值
     */
    @Column(name = "typical_bid_range_min", precision = 19, scale = 2)
    private BigDecimal typicalBidRangeMin;

    /**
     * 典型投标范围最大值
     */
    @Column(name = "typical_bid_range_max", precision = 19, scale = 2)
    private BigDecimal typicalBidRangeMax;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
