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

    @Test
    @DisplayName("isNumberOnlyTitle 识别纯编号伪标题")
    void testNumberOnlyTitleRecognition() {
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("1.")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("3、")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("13.2.1.")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("第一章")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("第一节")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("一、")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("（1）")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("①")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("A.")).isTrue();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("第一章　")).isTrue();

        assertThat(MarkdownTitleRules.isNumberOnlyTitle("技术团队人员安排")).isFalse();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("第一章 项目背景")).isFalse();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle("A. 附录清单")).isFalse();
        assertThat(MarkdownTitleRules.isNumberOnlyTitle(null)).isFalse();
    }

    @Test
    @DisplayName("isValidSectionTitle 放行合法小标题并拦截伪标题")
    void testValidSectionTitle() {
        assertThat(MarkdownTitleRules.isValidSectionTitle("技术团队人员安排")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("项目实施")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("第一章 项目背景")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("13.2.1. 对接成功案例清单")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("A. 附录清单")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("六、 技术方案")).isTrue();
        assertThat(MarkdownTitleRules.isValidSectionTitle("3. 技术团队配置")).isTrue();

        assertThat(MarkdownTitleRules.isValidSectionTitle("因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例188个以及部分系统对接。")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("双方需严格按期交付！")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("1.")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("第一章")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("（1）")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("A.")).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle(null)).isFalse();
        assertThat(MarkdownTitleRules.isValidSectionTitle("")).isFalse();
    }

    @Test
    @DisplayName("stripTrailingPageNumber 清洗制表符/多空格末尾页码并保护合法实体编号")
    void testStripTrailingPageNumber() {
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("一、 投标函\t1")).isEqualTo("一、 投标函");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("一、 投标函 1")).isEqualTo("一、 投标函");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("九、 本项目相关其他内容\t1806")).isEqualTo("九、 本项目相关其他内容");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("九、 本项目相关其他内容 1806")).isEqualTo("九、 本项目相关其他内容");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("7. 响应性评审标准承诺函\t2003")).isEqualTo("7. 响应性评审标准承诺函");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("7. 响应性评审标准承诺函 2003")).isEqualTo("7. 响应性评审标准承诺函");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("技术团队人员安排......12")).isEqualTo("技术团队人员安排");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("七、 服务方案······134")).isEqualTo("七、 服务方案");

        // 保护合法实体编号标题不被误裁
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("附录 1")).isEqualTo("附录 1");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("附件 2")).isEqualTo("附件 2");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("表 3")).isEqualTo("表 3");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("图 4")).isEqualTo("图 4");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("标段 1")).isEqualTo("标段 1");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("分包 2")).isEqualTo("分包 2");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("阶段 1")).isEqualTo("阶段 1");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("包 1")).isEqualTo("包 1");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("技术团队人员安排")).isEqualTo("技术团队人员安排");
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("13.2.1. 对接成功案例清单")).isEqualTo("13.2.1. 对接成功案例清单");

        assertThat(MarkdownTitleRules.stripTrailingPageNumber(null)).isNull();
        assertThat(MarkdownTitleRules.stripTrailingPageNumber("")).isEqualTo("");
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
