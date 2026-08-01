package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoringSectionAliasesTest {

    @Test
    void all_shouldContainCoreAliases() {
        // 评分标准章节标题的常见叫法，必须全部覆盖
        assertTrue(ScoringSectionAliases.ALL.contains("评标办法"));
        assertTrue(ScoringSectionAliases.ALL.contains("评分标准"));
        assertTrue(ScoringSectionAliases.ALL.contains("评审因素"));
        assertTrue(ScoringSectionAliases.ALL.contains("评标方法"));
        assertTrue(ScoringSectionAliases.ALL.contains("评分细则"));
        assertTrue(ScoringSectionAliases.ALL.contains("评审标准"));
    }

    @Test
    void all_shouldContainAtLeast8Aliases() {
        // 至少 8 种别名，覆盖常见招标文件变体
        assertTrue(ScoringSectionAliases.ALL.size() >= 8,
                "ALL should have at least 8 aliases, got " + ScoringSectionAliases.ALL.size());
    }

    @Test
    void all_shouldNotContainNullOrBlank() {
        for (String alias : ScoringSectionAliases.ALL) {
            assertNotNull(alias, "alias must not be null");
            assertFalse(alias.isBlank(), "alias must not be blank");
        }
    }

    @Test
    void all_shouldBeUnique() {
        assertEquals(ScoringSectionAliases.ALL.size(),
                ScoringSectionAliases.ALL.stream().distinct().count(),
                "ALL must not contain duplicates");
    }

    @Test
    void display_shouldBeSlashSeparated() {
        // DISPLAY 用于注入 Prompt，格式为 "评标办法/评分标准/..."
        assertTrue(ScoringSectionAliases.DISPLAY.contains("/"),
                "DISPLAY should be slash-separated");
        assertTrue(ScoringSectionAliases.DISPLAY.contains("评标办法"),
                "DISPLAY should contain 评标办法");
    }

    @Test
    void display_shouldMatchAllJoined() {
        assertEquals(String.join("/", ScoringSectionAliases.ALL),
                ScoringSectionAliases.DISPLAY);
    }
}
