package com.xiyu.bid.projectworkflow.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDraftTextParserTest {

    private final ScoreDraftTextParser parser = new ScoreDraftTextParser();

    @Test
    void parse_ShouldSplitRegularScoreSectionsIntoMinimalUnits() {
        String text = """
                第三章 评审程序及办法
                3.6 商务评分标准（20分）
                序号
                评价项目
                评分标准
                评标分值
                1
                供应商综合实力
                注册资金10000万元及以上，得2分，其他得0分。
                2
                2022、2023连续2年，供应商年度营业收入累计不低于40亿元人民币，得2分，其他得0分。
                2
                2
                供应商资质
                供应商具备ISO14001环境管理体系证书、质量管理体系认证证书ISO9001、ISO45001职业健康安全管理体系证书；每个资质证书2分，满分6分。
                6
                3.7 技术评分标准（50分）
                评分项目
                分数
                评分因素及标准
                整体方案
                15
                最大程度满足采购文件要求，从科学性、先进性、可行性等综合评价，按照优、良、中、差，依次得14-15分、9-13分、3-8分、0-2分。
                平台系统对接实施方案
                3
                根据本项目采购文件要求做平台系统对接实施方案，从方案质量、合理性、适用性等进行综合评审。
                3.8 价格评分标准（30分）
                序号
                评价项目
                分值
                评分细则
                1
                品类折扣率
                25
                接受平台的价格管控，确保用户单位享受优惠价格。平均折扣率等于基准价的得25分。
                2
                结算周期
                5
                结算周期：采购单位收到发票后90个自然日现汇得2分，采购单位收到发票付6个月以内银承或中兵保兑得3分。
                3.9 报价要求
                """;

        var sections = parser.parse("评分标准.doc", text);

        assertThat(sections).hasSize(3);
        assertThat(sections).extracting(ParsedSection::category)
                .containsExactly("business", "technical", "price");
        assertThat(sections.get(0).seeds()).hasSize(3);
        assertThat(sections.get(1).seeds()).hasSize(2);
        assertThat(sections.get(2).seeds()).hasSize(2);
        assertThat(sections.get(0).seeds()).extracting(DraftSeed::scoreItemTitle)
                .contains("供应商综合实力", "供应商综合实力（子项2）", "供应商资质");
        assertThat(sections.get(0).seeds().get(0).generatedTaskTitle()).contains("供应商综合实力");
    }

    @Test
    void parse_ShouldSplitCompactPdfTableRowsIntoScoreSections() {
        String text = """
                3.6 商务评分标准（20分）
                序号 评价项目 评分标准 评标分值
                1 同类项目业绩 每提供1个同类项目业绩得2分，最高6分。 6
                3.7 技术评分标准（50分）
                评分项目 分数 评分因素及标准
                整体方案 15 最大程度满足采购文件要求。
                3.8 价格评分标准（30分）
                序号 评价项目 分值 评分细则
                1 品类折扣率 25 接受平台的价格管控，确保用户单位享受优惠价格。
                """;

        var sections = parser.parse("评分标准.pdf", text);

        assertThat(sections).hasSize(3);
        assertThat(sections).extracting(ParsedSection::category)
                .containsExactly("business", "technical", "price");
        assertThat(sections.get(0).seeds()).extracting(DraftSeed::scoreItemTitle)
                .containsExactly("同类项目业绩");
        assertThat(sections.get(1).seeds()).extracting(DraftSeed::scoreItemTitle)
                .containsExactly("整体方案");
        assertThat(sections.get(2).seeds()).extracting(DraftSeed::scoreItemTitle)
                .containsExactly("品类折扣率");
    }

    @Test
    void parse_ShouldFilterOutTextWithoutValidSections() {
        assertThat(parser.parse("评分标准.docx", "普通说明\n无评分标准")).isEmpty();
    }
}
