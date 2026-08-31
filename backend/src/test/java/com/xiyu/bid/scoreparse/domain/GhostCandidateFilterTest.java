// Input: GhostCandidateFilter（filter 方法）
// Output: 幽灵候选滤除行为验证（spec 046 FR-001 / FR-006）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GhostCandidateFilterTest {

    private final GhostCandidateFilter filter = new GhostCandidateFilter();

    @Test
    void filter_dimEqualsDetailShortNorm_filtered() {
        // 表头列名回声：dim=detail="得分"（归一化后 ≤4 字）→ 幽灵，即使有 code/sourceText
        ScoreCandidate ghost = new ScoreCandidate("1", "得 分", "得分", new BigDecimal("100"),
                null, null, "评审表整行原文", "P3", null);
        ScoreCandidate real = new ScoreCandidate("2", "技术方案", "方案架构设计说明", new BigDecimal("30"),
                null, null, "方案架构设计说明", "P5", null);

        List<ScoreCandidate> result = filter.filter(List.of(ghost, real));

        assertThat(result).containsExactly(real);
    }

    @Test
    void filter_dimEqualsDetailLongerThan4Chars_kept() {
        // dim=detail 但归一化后 >4 字（如"综合评分标准"6 字）→ 非表头回声特征，保留
        ScoreCandidate candidate = new ScoreCandidate("1", "综合评分标准", "综合评分标准",
                new BigDecimal("20"), null, null, "综合评分标准整行原文", "P2", null);

        List<ScoreCandidate> result = filter.filter(List.of(candidate));

        assertThat(result).containsExactly(candidate);
    }

    @Test
    void filter_noCodeButHasSourceText_kept() {
        // 规则 2 要求 code 空「且」sourceText 空——有任一即保留（真实项保护）
        ScoreCandidate candidate = new ScoreCandidate(null, "技术", "方案描述", new BigDecimal("10"),
                null, null, "方案描述原文条款", "P8", null);

        assertThat(filter.filter(List.of(candidate))).containsExactly(candidate);
    }

    @Test
    void filter_codeButNoSourceText_kept() {
        ScoreCandidate candidate = new ScoreCandidate("3", "商务", "业绩要求", new BigDecimal("10"),
                null, null, null, "P9", null);

        assertThat(filter.filter(List.of(candidate))).containsExactly(candidate);
    }

    @Test
    void filter_noCodeAndBlankOrNullSourceText_filtered() {
        ScoreCandidate blankSource = new ScoreCandidate(null, "资信", "信用证明", new BigDecimal("5"),
                null, null, "   ", "P9", null);
        ScoreCandidate nullSource = new ScoreCandidate("  ", "服务", "服务承诺", new BigDecimal("5"),
                null, null, null, "P10", null);

        assertThat(filter.filter(List.of(blankSource, nullSource))).isEmpty();
    }

    @Test
    void filter_inputNotModified_remainingOrderPreserved() {
        ScoreCandidate ghost1 = new ScoreCandidate(null, "得分", "得分", new BigDecimal("100"),
                null, null, null, "P1", null);
        ScoreCandidate real1 = new ScoreCandidate("1", "技术", "技术方案", new BigDecimal("30"),
                null, null, "技术方案原文", "P2", null);
        ScoreCandidate ghost2 = new ScoreCandidate(null, "商务", "商务要求", new BigDecimal("70"),
                null, null, " ", "P3", null);
        ScoreCandidate real2 = new ScoreCandidate("2", "服务", "服务方案", new BigDecimal("70"),
                null, null, "服务方案原文", "P4", null);
        List<ScoreCandidate> input = new ArrayList<>(List.of(ghost1, real1, ghost2, real2));
        List<ScoreCandidate> snapshot = List.copyOf(input);

        List<ScoreCandidate> result = filter.filter(input);

        assertThat(result).containsExactly(real1, real2);
        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    @Test
    void filter_contractAssessmentItems_filtered() {
        ScoreCandidate assessment1 = new ScoreCandidate("1", "周期保证", "产品或服务满足项目周期要求", new BigDecimal("25"),
                null, null, "考核项目：周期保证", "P10", null);
        ScoreCandidate assessment2 = new ScoreCandidate("2", "质量保证", "违规行为判定：出现质量问题扣款", new BigDecimal("25"),
                null, null, "违约责任承担：单个商品缴纳违约金2000元", "P11", null);
        ScoreCandidate real = new ScoreCandidate("1", "技术指标", "满足技术要求得10分", new BigDecimal("10"),
                null, null, "技术指标要求", "P2", null);

        List<ScoreCandidate> result = filter.filter(List.of(assessment1, assessment2, real));
        assertThat(result).containsExactly(real);
    }

    @Test
    void filter_nullInput_returnsEmpty() {
        assertThat(filter.filter(null)).isEmpty();
    }
}
