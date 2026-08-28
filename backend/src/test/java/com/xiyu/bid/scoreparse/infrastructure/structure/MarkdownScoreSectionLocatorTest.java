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
    void locate_plainTextChapterSection_foundWhenNoPipeTables() {
        String plainText = """
                第一章 招标公告
                招标公告说明文字。

                第三章 评审办法（综合评估法）
                评审办法前附表
                1. 商务评分标准：财务状况 20分，企业规模 10分，总分 30分。
                2. 技术评分标准：方案先进性 40分，服务承诺 30分，满分 70分。

                第四章 合同条款
                合同正文内容。
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(plainText);
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("商务评分标准");
        assertThat(sections.get(0).content()).contains("技术评分标准");
        assertThat(sections.get(0).sectionTitle()).contains("评审办法");
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

    private static final String ISOLATED_BLOCK_GAP = """
            过渡文字一。
            过渡文字二。
            过渡文字三。
            过渡文字四。
            过渡文字五。
            过渡文字六。
            过渡文字七。
            过渡文字八。
            过渡文字九。
            过渡文字十。
            过渡文字十一。
            """;

    @Test
    void locate_isolatedPriceParamBlock_withMultipleLongParams_collected() {
        String markdown = """
                ## 评分标准

                | 序号 | 评分项 | 分值 |
                |---|---|
                | 1 | 技术方案 | 40 |
                """ + ISOLATED_BLOCK_GAP + """

                | 公式 | 系数 |
                |---|---|
                | 价格分计算 F=100- | 基准 |
                | BP% | 80 |
                | HP% | 100 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).hasSize(1);
        // 闾值提高后，11 行过渡文字不再截断，两表合并为 1 段，价格参数内容仍完整保留
        assertThat(sections.get(0).content()).contains("价格分计算").contains("BP%");
    }

    @Test
    void locate_isolatedBlock_withOnlyShortToken_notCollected() {
        String markdown = """
                ## 评分标准

                | 序号 | 评分项 | 分值 |
                |---|---|
                | 1 | 技术方案 | 40 |
                """ + ISOLATED_BLOCK_GAP + """

                | 等级 | 要求 |
                |---|---|
                | L1级 | 资质 |
                | L1级 | 业绩 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).content()).contains("技术方案");
    }

    @Test
    void locate_procedureNoticeTable_withScoreKeyword_isSkipped() {
        // 须知前附表（表头: 条款号|条款内容|编列内容）即使含"综合评分"/"综合得分"也不应入候选池
        // 复现场景：山焦物资须知前附表 6.2.2 推荐候选供应商行含"综合评分"触发误召回
        String markdown = """
                | 条款号 | 条款内容 | 编列内容 |
                |---|---|---|
                | 1.8 | 采购预备会 | 不召开 |
                | 5.2 | 开启程序 | 本次开启程序采用电子平台进行，工作人员应提前10分钟登陆 |
                | 6.2.2 | 推荐候选成交供应商的排序及数量 | 本项目根据综合评分，选择综合得分排名前8的投标人 |
                | 7.3 | 成交结果公示 | 公示媒介：同采购公告发布媒介。公示期限：3日 |
                | 7.6 | 履约保证金 | 履约金额：人民币100000元整 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        // 须知前附表整体应被过滤，不进入评分候选池
        assertThat(sections).isEmpty();
    }

    @Test
    void locate_realScoringTable_withEvalFactorColumn_isIncluded() {
        // 真正的评分标准表（含"评分因素/评分标准"列）不受须知前附表过滤影响，正常召回
        String markdown = """
                | 条款号及名称 | 条款号及名称 | 评审因素 | 评分标准 | 评分标准 |
                |---|---|---|---|---|
                | 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力（10分） | 审计意见连续3年无保留意见，得4分 | 审计意见连续3年无保留意见，得4分 |
                | 3.3(1) | 商务评分标准（56分） | 运营能力及业绩（20分） | 平台运营年限得1-20分 | 平台运营年限得1-20分 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        // 真正评分表应正常召回
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("财务状况");
        assertThat(sections.get(0).content()).contains("得4分");
    }

    @Test
    void locate_mixedTable_withProcedureAndScoreColumns_isRetained() {
        // 混合表：表头同时含"编列内容"与"评分标准"列时保留不跳过，防止误杀真评分信息
        String markdown = """
                | 条款号 | 条款内容 | 编列内容 | 评分标准 |
                |---|---|---|---|
                | 3.1 | 商务评分 | 见附表 | 审计意见连续3年无保留意见，得4分 |
                """;
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
        // 含评分标准列 → 认定为评分信息混排的混合表，正常召回
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).content()).contains("得4分");
    }
}
