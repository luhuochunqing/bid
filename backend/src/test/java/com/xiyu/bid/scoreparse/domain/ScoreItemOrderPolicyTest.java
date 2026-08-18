// Input: ScoreItemOrderPolicy（sort 方法）
// Output: 编号数值序排序行为验证（spec 046 FR-003 / FR-004）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemOrderPolicyTest {

    private final ScoreItemOrderPolicy policy = new ScoreItemOrderPolicy();

    @Test
    void sort_codesOrderedNumerically() {
        // project 212 实测编号乱序：6,7,1,2,3,4-1,4-2,5 → 1,2,3,4-1,4-2,5,6,7
        List<ScoreCandidate> result =
                policy.sort(candidates("6", "7", "1", "2", "3", "4-1", "4-2", "5"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("1", "2", "3", "4-1", "4-2", "5", "6", "7");
    }

    @Test
    void sort_unparsableCodes_stableRelativeOrderAtEnd() {
        // "A1"/"B2" 不可解析：稳定保持相对序，排在可解析之后
        List<ScoreCandidate> result = policy.sort(candidates("A1", "1", "B2", "2"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("1", "2", "A1", "B2");
    }

    @Test
    void sort_subSegmentNumericOrder_noMinorFirst() {
        // "4" < "4-1" < "4-2"：同 major 无 minor 排前；分隔符 -/—/. 等价；同键稳定保持相对序
        List<ScoreCandidate> result = policy.sort(candidates("4-2", "4", "4—1", "4.2", "2"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("2", "4", "4—1", "4-2", "4.2");
    }

    @Test
    void sort_nullOrBlankCode_unparsedGroup() {
        List<ScoreCandidate> result = policy.sort(candidates(null, "2", "  "));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("2", null, "  ");
    }

    @Test
    void sort_inputNotModified() {
        List<ScoreCandidate> input = new ArrayList<>(candidates("6", "1", "A1"));
        List<ScoreCandidate> snapshot = List.copyOf(input);

        policy.sort(input);

        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    @Test
    void sort_nullInput_returnsEmpty() {
        assertThat(policy.sort(null)).isEmpty();
    }

    private List<ScoreCandidate> candidates(String... codes) {
        List<ScoreCandidate> list = new ArrayList<>();
        for (String code : codes) {
            list.add(new ScoreCandidate(code, "维度", "要素", new BigDecimal("10"),
                    null, null, "条款原文", "P9", null));
        }
        return list;
    }
}
