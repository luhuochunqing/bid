// Input: /casework/chapter-title-contract-cases.json（与前端共享的契约夹具）
// Output: 断言 MarkdownTitleRules.isChapterTitle 逐例与夹具期望一致
// Pos: Backend/casework 测试——章节标题契约（章节号 + 标题文字）的跨语言守卫
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
 * 章节标题契约（「章节号 + 标题文字」）的跨语言契约测试（Java 侧）。
 *
 * <p>夹具 {@code backend/src/test/resources/casework/chapter-title-contract-cases.json} 同时被前端
 * {@code src/views/Knowledge/views/components/__tests__/caseDetailFormatter.spec.js} 读取：
 * 任一侧改动判据（编号形式集合、串首锚定、句子门先后）都会在某一侧的测试上失败，
 * 避免"注释约定"式漂移——「原文所在标题」的展示与写回必须两侧同判。</p>
 */
class ChapterTitleContractTest {

    private static final String FIXTURE = "/casework/chapter-title-contract-cases.json";

    @Test
    @DisplayName("与前端共享夹具的章节标题判定逐例一致")
    void matchesSharedContractFixture() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (JsonNode node : readFixture().get("cases")) {
            JsonNode titleNode = node.get("title");
            String title = titleNode == null || titleNode.isNull() ? null : titleNode.asText();
            boolean expected = node.get("chapter").asBoolean();
            boolean actual = MarkdownTitleRules.isChapterTitle(title);
            if (actual != expected) {
                mismatches.add("标题=" + title + " 期望=" + expected + " 实得=" + actual);
            }
        }

        assertThat(mismatches)
                .as("与共享契约夹具不一致（夹具：%s）", FIXTURE)
                .isEmpty();
    }

    @Test
    @DisplayName("已验证章节必然是句子门放行的标题（两道门有先后）")
    void chapterImpliesNotSentenceLike() throws Exception {
        for (JsonNode node : readFixture().get("cases")) {
            JsonNode titleNode = node.get("title");
            if (titleNode == null || titleNode.isNull() || !node.get("chapter").asBoolean()) {
                continue;
            }
            String title = titleNode.asText();
            assertThat(MarkdownTitleRules.isSentenceLikeTitle(title))
                    .as("章节契约必须先在句子门上通过：%s", title)
                    .isFalse();
            assertThat(MarkdownTitleRules.isChapterTitle(title))
                    .as("编号在串首的真实章节必须被接受：%s", title)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("夹具自身必须同时覆盖正反例与关键形式，避免空跑")
    void fixtureCoversBothVerdicts() throws Exception {
        JsonNode cases = readFixture().get("cases");

        assertThat(cases.size()).isGreaterThanOrEqualTo(15);
        assertThat(collectTitles(cases, true)).as("夹具缺少 chapter=true 的用例").isNotEmpty();
        assertThat(collectTitles(cases, false)).as("夹具缺少 chapter=false 的用例").isNotEmpty();
        // 反例必须包含"无编号小标题"这一本次要堵的漏洞，否则任一侧放开编号要求都不会被发现
        assertThat(collectTitles(cases, false))
                .as("夹具缺少无编号小标题反例（如 项目实施）")
                .contains("项目实施");
        // 正例必须同时覆盖点分编号与中文章节号两种验收形式
        assertThat(collectTitles(cases, true))
                .as("夹具缺少点分编号正例")
                .contains("13.2.1. 对接成功案例清单");
        assertThat(collectTitles(cases, true))
                .as("夹具缺少中文章节号正例")
                .contains("第一章 项目背景");
        // 编号不在串首的反例，用来钉住"锚定串首"这一条
        assertThat(collectTitles(cases, false)).contains("技术方案 1.2 先进性");
        // 「章节号 + 标题文字」中的"标题文字"一侧：夹具丢掉任一条，两侧就会重新放行 1. / 第一章 这类串
        assertThat(collectTitles(cases, false))
                .as("夹具缺少「只有编号没有标题文字」反例")
                .contains("1.", "第一章", "（1）", "①", "A.");
        // 字母列表标记不是章节号：若任一侧重新接纳字母枚举，展示层会把 A. 附录清单这类列表标记当原文章节
        assertThat(collectTitles(cases, false))
                .as("夹具缺少字母列表标记反例")
                .contains("A. 附录清单", "B、服务方案", "a. 投标函");
        // 全角空格（U+3000）边界：Java 的 trim 与 \s 都不覆盖它、JS 的 trim 覆盖，靠这两条钉住两侧字符类逐字一致
        assertThat(collectTitles(cases, true))
                .as("夹具缺少全角空格边界正例")
                .contains("　第一章 项目背景");
        assertThat(collectTitles(cases, false))
                .as("夹具缺少全角空格边界反例")
                .contains("第一章　");
    }

    private JsonNode readFixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(stream).as("契约夹具不在测试类路径上：%s", FIXTURE).isNotNull();
            return new ObjectMapper().readTree(stream);
        }
    }

    private List<String> collectTitles(JsonNode cases, boolean expected) {
        List<String> titles = new ArrayList<>();
        for (JsonNode node : cases) {
            if (node.get("chapter").asBoolean() == expected) {
                JsonNode titleNode = node.get("title");
                titles.add(titleNode == null || titleNode.isNull() ? null : titleNode.asText());
            }
        }
        return titles;
    }
}
