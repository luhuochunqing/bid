package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScoringSectionLocatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\n\n\n"})
    void locate_shouldReturnEmpty_whenTextIsBlank(String text) {
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isEmpty());
    }

    @Test
    void locate_shouldReturnEmpty_whenNoScoringSectionFound() {
        String text = """
                第一章 招标公告
                本次招标项目的有关事项公告如下。

                第二章 投标人须知
                投标人应具备相应资质。
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isEmpty());
    }

    @Test
    void locate_shouldFindSection_byExactAlias_评标办法() {
        String text = """
                第一章 招标公告

                第二章 评标办法

                1. 评审方法：综合评分法
                2. 价格分：30分
                3. 技术分：50分
                4. 商务分：20分

                第三章 合同条款
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        String section = result.get();
        assertTrue(section.contains("评审方法"), "should contain scoring content");
        assertTrue(section.contains("价格分"), "should contain price score");
        assertTrue(section.contains("技术分"), "should contain technical score");
        assertFalse(section.contains("合同条款"), "should not include next chapter");
    }

    @Test
    void locate_shouldFindSection_byAlias_评分标准() {
        String text = """
                第一章 投标须知

                评分标准

                序号  评分项  分值
                1     价格     30
                2     技术     50
                3     商务     20

                第三章 中标通知
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        String section = result.get();
        assertTrue(section.contains("价格"), "should contain price");
        assertTrue(section.contains("技术"), "should contain technical");
        assertFalse(section.contains("中标通知"), "should not include next chapter");
    }

    @Test
    void locate_shouldFindSection_byAlias_评审因素() {
        String text = """
                招标文件正文

                评审因素

                A1 技术方案 30分
                A2 商务方案 20分

                第四章 中标候选人
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("技术方案"));
        assertFalse(result.get().contains("中标候选人"));
    }

    @Test
    void locate_shouldHandleWhitespaceInAlias() {
        // PDF 提取的文本可能有排版空格打断关键词："评 标 办 法"
        String text = """
                第一章 招标公告

                评 标 办 法

                价格分 30分
                技术分 50分

                第三章 合同
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent(), "should find section despite whitespace in alias");
        assertTrue(result.get().contains("价格分"));
    }

    @Test
    void locate_shouldStopAtNextChapter_第X章() {
        String text = """
                第二章 评标办法

                综合评分法，总分100分。
                价格分 30分。

                第三章 合同条款

                甲方和乙方签订合同。
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertFalse(result.get().contains("第三章"), "should stop at next chapter heading");
        assertFalse(result.get().contains("甲方和乙方"), "should not include next chapter content");
    }

    @Test
    void locate_shouldStopAtNextChapter_第X条() {
        String text = """
                评标办法

                价格分 30分。

                第十条 合同条款
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("价格分"));
        assertFalse(result.get().contains("第十条"), "should stop at 第X条 heading");
    }

    @Test
    void locate_shouldUseFirstMatch_whenMultipleAliasesPresent() {
        // 文件中多次出现评分相关标题，取第一个
        String text = """
                评分标准

                价格分 30分

                评标办法

                技术分 50分

                第四章 中标
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("价格分"), "should start from first match");
    }

    @Test
    void locate_shouldHandleScoringSectionAtEndOfFile() {
        String text = """
                第一章 招标公告

                第二章 评标办法

                价格分 30分
                技术分 50分
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("价格分"));
        assertTrue(result.get().contains("技术分"));
    }

    @Test
    void locate_shouldNotMatchAliasInDescriptiveText() {
        // "评标办法" 出现在描述性文字中，不是章节标题
        String text = """
                第一章 招标公告

                本项目采用公开招标方式，评标办法详见招标文件第三章。

                第二章 投标人须知
                """;
        Optional<String> result = ScoringSectionLocator.locate(text);
        // 描述性文字中的"评标办法"不应被匹配为章节标题
        // 因为它不是独立成行的标题
        assertTrue(result.isEmpty() || !result.get().contains("详见招标文件"),
                "should not match alias in descriptive text");
    }

    @Test
    void locate_shouldCaptureLargeScoringTable() {
        // 评分表可能很长，确保不会截断
        StringBuilder scoringContent = new StringBuilder();
        scoringContent.append("评标办法\n\n");
        scoringContent.append("评审因素及分值分配表\n\n");
        for (int i = 1; i <= 20; i++) {
            scoringContent.append(String.format("%d. 评分项%d %d分\n", i, i, 5));
        }
        scoringContent.append("\n第三章 合同\n");
        String text = "第一章 招标\n\n" + scoringContent;

        Optional<String> result = ScoringSectionLocator.locate(text);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("评分项1"), "should contain first item");
        assertTrue(result.get().contains("评分项20"), "should contain last item");
        assertFalse(result.get().contains("第三章"), "should stop at next chapter");
    }
}
