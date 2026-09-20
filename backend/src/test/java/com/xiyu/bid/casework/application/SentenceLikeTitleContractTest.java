// Input: /casework/sentence-like-title-cases.json（与前端共享的契约夹具）
// Output: 断言 MarkdownTitleRules.isSentenceLikeTitle 逐例与夹具期望一致
// Pos: Backend/casework 测试——句子型伪标题规则的跨语言契约守卫
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 句子型伪标题判定规则的跨语言契约测试（Java 侧）。
 *
 * <p>夹具 {@code backend/src/test/resources/casework/sentence-like-title-cases.json} 同时被前端
 * {@code src/views/Knowledge/views/components/__tests__/caseDetailFormatter.spec.js} 读取：
 * 任一侧改动规则（句读集合、长度阈值、逗号阈值）都会在某一侧的测试上失败，避免"注释约定"式漂移。</p>
 */
class SentenceLikeTitleContractTest {

    private static final String FIXTURE = "/casework/sentence-like-title-cases.json";

    @Test
    @DisplayName("与前端共享夹具的句子型标题判定逐例一致")
    void matchesSharedContractFixture() throws Exception {
        JsonNode cases = readFixture().get("cases");
        assertThat(cases).isNotNull();

        List<String> mismatches = new ArrayList<>();
        for (JsonNode node : cases) {
            JsonNode titleNode = node.get("title");
            String title = titleNode == null || titleNode.isNull() ? null : titleNode.asText();
            boolean expected = node.get("sentenceLike").asBoolean();
            boolean actual = MarkdownTitleRules.isSentenceLikeTitle(title);
            if (actual != expected) {
                mismatches.add("标题=" + title + " 期望=" + expected + " 实得=" + actual);
            }
        }

        assertThat(mismatches)
                .as("与共享契约夹具不一致（夹具：%s）", FIXTURE)
                .isEmpty();
    }

    @Test
    @DisplayName("夹具自身必须同时覆盖真/假两类与阈值边界，避免空跑")
    void fixtureCoversBothVerdicts() throws Exception {
        JsonNode cases = readFixture().get("cases");

        assertThat(cases.size()).isGreaterThanOrEqualTo(12);
        assertThat(collectExpected(cases, true)).as("夹具缺少 sentenceLike=true 的用例").isNotEmpty();
        assertThat(collectExpected(cases, false)).as("夹具缺少 sentenceLike=false 的用例").isNotEmpty();
        // 阈值边界必须成对存在，否则任一侧改阈值都不会被夹具发现
        assertThat(collectTitlesOfLength(cases, 80, false)).isNotEmpty();
        assertThat(collectTitlesOfLength(cases, 81, true)).isNotEmpty();
        assertThat(collectTitlesOfLength(cases, 30, false)).isNotEmpty();
        assertThat(collectTitlesOfLength(cases, 31, true)).isNotEmpty();
    }

    private JsonNode readFixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(stream).as("契约夹具不在测试类路径上：%s", FIXTURE).isNotNull();
            return new ObjectMapper().readTree(stream);
        }
    }

    private List<String> collectExpected(JsonNode cases, boolean expected) {
        List<String> titles = new ArrayList<>();
        for (JsonNode node : cases) {
            if (node.get("sentenceLike").asBoolean() == expected) {
                titles.add(node.get("title").asText());
            }
        }
        return titles;
    }

    private List<String> collectTitlesOfLength(JsonNode cases, int length, boolean expected) {
        List<String> titles = new ArrayList<>();
        for (JsonNode node : cases) {
            JsonNode titleNode = node.get("title");
            if (titleNode == null || titleNode.isNull()) {
                continue;
            }
            if (titleNode.asText().length() == length && node.get("sentenceLike").asBoolean() == expected) {
                titles.add(titleNode.asText());
            }
        }
        return titles;
    }
}
