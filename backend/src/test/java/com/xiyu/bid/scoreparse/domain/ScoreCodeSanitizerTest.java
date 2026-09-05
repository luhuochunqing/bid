package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCodeSanitizerTest {

    @Test
    void clean_nullAndUndefinedVariants_returnEmpty() {
        assertThat(ScoreCodeSanitizer.clean(null)).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("   ")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("null")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("NULL")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("  null  ")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("undefined")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("UNDEFINED")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("none")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("n/a")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("N/A")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("(null)")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("（null）")).isEmpty();
        assertThat(ScoreCodeSanitizer.clean("null.0")).isEmpty();
    }

    @Test
    void clean_compoundNullPrefixAndSuffix_cleansSegment() {
        assertThat(ScoreCodeSanitizer.clean("null.1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("NULL.1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("null-1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("null_1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("null/1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("1.null")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("1.null.2")).isEqualTo("1.2");
        assertThat(ScoreCodeSanitizer.clean("null.1.2")).isEqualTo("1.2");
        assertThat(ScoreCodeSanitizer.clean("undefined.3")).isEqualTo("3");
        assertThat(ScoreCodeSanitizer.clean("1.")).isEqualTo("1");
        // 边缘字符：全角 ｎｕｌｌ、零宽字符、(null)1、null.(null)
        assertThat(ScoreCodeSanitizer.clean("ｎｕｌｌ.1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("(null)1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("（null）2")).isEqualTo("2");
        assertThat(ScoreCodeSanitizer.clean("null.\u200B1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("null.(null)")).isEmpty();
        // 反斜杠分隔符（与前端 sanitizeCode 对齐）
        assertThat(ScoreCodeSanitizer.clean("null\\1")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("1\\null")).isEqualTo("1");
    }

    @Test
    void clean_chineseSectionNumbers_preserved() {
        assertThat(ScoreCodeSanitizer.clean("一")).isEqualTo("一");
        assertThat(ScoreCodeSanitizer.clean("一、")).isEqualTo("一、");
        assertThat(ScoreCodeSanitizer.clean("二、")).isEqualTo("二、");
        assertThat(ScoreCodeSanitizer.clean("十.")).isEqualTo("十");
        assertThat(ScoreCodeSanitizer.clean("一百")).isEqualTo("一百");
    }

    @Test
    void clean_validAlphanumericCodes_preserved() {
        assertThat(ScoreCodeSanitizer.clean("A1")).isEqualTo("A1");
        assertThat(ScoreCodeSanitizer.clean("1.2.3")).isEqualTo("1.2.3");
        assertThat(ScoreCodeSanitizer.clean("4-1")).isEqualTo("4-1");
        assertThat(ScoreCodeSanitizer.clean("S1")).isEqualTo("S1");
        // NFKC 归一行为锁：圈号/上标同样归一（与前端 sanitizeCode 对齐）
        assertThat(ScoreCodeSanitizer.clean("①")).isEqualTo("1");
        assertThat(ScoreCodeSanitizer.clean("²")).isEqualTo("2");
    }
}
