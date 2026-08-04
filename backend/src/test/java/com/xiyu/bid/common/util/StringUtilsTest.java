package com.xiyu.bid.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StringUtils} 单元测试。
 *
 * @since CO-602 PR 设计评估修复
 */
class StringUtilsTest {

    @Test
    void truncate_短字符串_原样返回() {
        assertThat(StringUtils.truncate("hello", 10)).isEqualTo("hello");
    }

    @Test
    void truncate_长字符串_截断到指定长度() {
        assertThat(StringUtils.truncate("hello world", 5)).isEqualTo("hello");
    }

    @Test
    void truncate_刚好等于maxLen_原样返回() {
        assertThat(StringUtils.truncate("12345", 5)).isEqualTo("12345");
    }

    @Test
    void truncate_null_返回空串() {
        assertThat(StringUtils.truncate(null, 10)).isEqualTo("");
    }

    @Test
    void truncate_maxLen为零_返回空串() {
        assertThat(StringUtils.truncate("hello", 0)).isEqualTo("");
    }

    @Test
    void truncate_maxLen为负数_返回空串() {
        assertThat(StringUtils.truncate("hello", -1)).isEqualTo("");
    }

    @Test
    void truncate_空字符串_返回空串() {
        assertThat(StringUtils.truncate("", 10)).isEqualTo("");
    }
}
