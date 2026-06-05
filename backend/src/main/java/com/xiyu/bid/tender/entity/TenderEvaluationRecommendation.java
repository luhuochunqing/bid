package com.xiyu.bid.tender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标讯评估表-投标负责人建议段（V130 三段式重构新增）。
 *
 * <p>共享主键模式：evaluation_id 同时为主键和外键，与 TenderEvaluation 一对一。
 * 对应 PRD §4.2.5 评估表第三段「投标负责人建议」。
 */
@Entity
@Table(name = "tender_evaluation_recommendation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderEvaluationRecommendation {

    @Id
    @Column(name = "evaluation_id")
    private Long evaluationId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "evaluation_id")
    private TenderEvaluation evaluation;

    /** 是否建议投标。 */
    @Column(name = "should_bid", nullable = false)
    private Boolean shouldBid;

    /** 理由（不建议投标时必填）。 */
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
}
