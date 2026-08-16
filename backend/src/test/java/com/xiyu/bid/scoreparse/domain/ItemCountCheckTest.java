// Input: ItemCountCheck（check 方法）
// Output: 解析项数量校验行为验证（spec 041 FR-006 / FR-007）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCountCheckTest {

    private final ItemCountCheck check = new ItemCountCheck();

    @Test
    void check_zeroItems_failed() {
        // 0 项 → 解析失败终态（FR-007：任务 FAILED + "未在文件中识别到评分标准章节"）
        ItemCountCheck.Result result = check.check(0);
        assertThat(result.failed()).isTrue();
        assertThat(result.failureMessage()).isEqualTo("未在文件中识别到评分标准章节，请确认文件内容或手动联系管理员");
    }

    @Test
    void check_negativeItems_failed() {
        assertThat(check.check(-1).failed()).isTrue();
    }

    @Test
    void check_positiveItems_passed() {
        ItemCountCheck.Result result = check.check(12);
        assertThat(result.failed()).isFalse();
        assertThat(result.failureMessage()).isNull();
    }

    @Test
    void check_singleItem_passed() {
        assertThat(check.check(1).failed()).isFalse();
    }

    @Test
    void checkCandidates_prefixGaps_detectsMissingNumbers() {
        ScoreCandidate c1 = new ScoreCandidate("A1", "技术", "架构", null, null, null, null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("A10", "技术", "实施", null, null, null, null, null, null);

        ItemCountCheck.Result result = check.checkCandidates(List.of(c1, c2));
        assertThat(result.failed()).isFalse();
        assertThat(result.needRecheck()).isTrue();
        assertThat(result.missingNumbers()).contains(2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    void checkCandidates_continuousPrefix_noRecheck() {
        ScoreCandidate c1 = new ScoreCandidate("A1", "技术", "架构", null, null, null, null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("A2", "技术", "实施", null, null, null, null, null, null);
        ScoreCandidate c3 = new ScoreCandidate("B1", "商务", "报价", null, null, null, null, null, null);
        ScoreCandidate c4 = new ScoreCandidate("B2", "商务", "付款", null, null, null, null, null, null);

        ItemCountCheck.Result result = check.checkCandidates(List.of(c1, c2, c3, c4));
        assertThat(result.needRecheck()).isFalse();
        assertThat(result.missingNumbers()).isEmpty();
    }
}
