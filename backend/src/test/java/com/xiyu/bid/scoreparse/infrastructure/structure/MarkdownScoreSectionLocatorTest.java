// Input: MarkdownScoreSectionLocator（locate 静态方法）
// Output: 文档结构召回候选区域定位行为验证（spec 041 FR-001 召回二）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.infrastructure.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownScoreSectionLocatorTest {

    @Test
    void locate_headingSectionWithScoreKeyword_found() {
        String markdown = """
                # 第一章 投标邀请

                请贵公司参加投标。

                ## 评标办法

                | 序号 | 评分项 | 分值 |
                |---|---|---|
                | 1 | 技术方案 | 40 |
                | 2 | 商务部分 | 30 |

                本章节规定评分细则。
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("技术方案");
        assertThat(sections.get(0).content()).contains("40");
    }

    @Test
    void locate_tableWithScoreColumn_foundWithoutHeading() {
        // 无"评分办法"标题，但表格含分值列（分/得分 关键词）也应召回
        String markdown = """
                # 项目概述

                本项目为智能化改造。

                | 评审因素 | 评审标准 | 满分 |
                |---|---|---|
                | 资质 | ISO9001 认证 | 5 |
                | 业绩 | 近三年类似项目 | 10 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("ISO9001");
    }

    @Test
    void locate_preservesContextBeforeAndAfter() {
        // 前后文保留：候选区域应包含评分表前后若干行（注/说明常在表后）
        String markdown = """
                # 第一章 投标须知

                前置说明文字。

                ## 评分标准

                | 序号 | 评分项 | 分值 |
                |---|---|---|
                | 1 | 技术方案 | 40 |

                注：各评分项得分不得超过该项满分。

                本页以下为废标条款。
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("注：各评分项得分不得超过该项满分");
    }

    @Test
    void locate_noScoreContent_returnsEmpty() {
        String markdown = """
                # 第一章 投标邀请

                本文件不含评分内容，仅邀请说明。

                ## 项目工期

                工期 90 天。
                """;
        assertThat(MarkdownScoreSectionLocator.locate(markdown)).isEmpty();
    }

    @Test
    void locate_nullOrBlank_returnsEmpty() {
        assertThat(MarkdownScoreSectionLocator.locate(null)).isEmpty();
        assertThat(MarkdownScoreSectionLocator.locate("")).isEmpty();
        assertThat(MarkdownScoreSectionLocator.locate("   \n  ")).isEmpty();
    }

    @Test
    void locate_recordsSectionTitle() {
        String markdown = """
                ## 评分细则

                | 序号 | 评分项 | 分值 |
                |---|---|---|
                | 1 | 技术方案 | 40 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).sectionTitle()).isEqualTo("评分细则");
    }

    @Test
    void locate_multipleScoreSections_allFound() {
        String markdown = """
                ## 技术评分标准

                | 评分项 | 分值 |
                |---|---|
                | 技术方案 | 40 |

                ## 商务评分标准

                | 评分项 | 分值 |
                |---|---|
                | 商务部分 | 30 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).hasSize(2);
    }
}
