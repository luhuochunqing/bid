// Input: ItemCountCheck（check 方法）
// Output: 解析项数量校验行为验证（spec 041 FR-006 / FR-007）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCountCheckTest {

    private final ItemCountCheck check = new ItemCountCheck();

    @Test
    void check_zeroItems_failed() {
        // 0 项 → 解析失败终态（FR-007：任务 FAILED + "未在文件中识别到评分标准章节"）
        ItemCountCheck.Result result = check.check(0);
        assertThat(result.failed()).isTrue();
        assertThat(result.failureMessage()).isEqualTo("未在文件中识别到评分标准章节");
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
}
