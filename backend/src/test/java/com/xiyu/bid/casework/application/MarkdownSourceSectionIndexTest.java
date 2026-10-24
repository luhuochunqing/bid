package com.xiyu.bid.casework.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarkdownSourceSectionIndex 单元测试。
 */
class MarkdownSourceSectionIndexTest {

    @Test
    @DisplayName("解析标准 Markdown 标题与层级路径（# ~ ######）")
    void testStandardMarkdownHeadings() {
        String md = """
                # 第一章 项目背景
                这是背景介绍。
                ## 1.1 建设目标
                建设高可用平台。
                ### 1.1.1 性能指标
                QPS > 10000。
                ## 1.2 实施周期
                总工期 3 个月。
                # 第二章 商务要求
                按期交付并付款。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        List<MarkdownSourceSectionIndex.Section> sections = index.getSections();

        assertThat(sections).hasSize(5);

        // Section 1
        assertThat(sections.get(0).index()).isEqualTo(1);
        assertThat(sections.get(0).level()).isEqualTo(1);
        assertThat(sections.get(0).title()).isEqualTo("第一章 项目背景");
        assertThat(sections.get(0).path()).isEqualTo("第一章 项目背景");

        // Section 2
        assertThat(sections.get(1).index()).isEqualTo(2);
        assertThat(sections.get(1).level()).isEqualTo(2);
        assertThat(sections.get(1).title()).isEqualTo("1.1 建设目标");
        assertThat(sections.get(1).path()).isEqualTo("第一章 项目背景 > 1.1 建设目标");

        // Section 3
        assertThat(sections.get(2).index()).isEqualTo(3);
        assertThat(sections.get(2).level()).isEqualTo(3);
        assertThat(sections.get(2).title()).isEqualTo("1.1.1 性能指标");
        assertThat(sections.get(2).path()).isEqualTo("第一章 项目背景 > 1.1 建设目标 > 1.1.1 性能指标");

        // Section 4
        assertThat(sections.get(3).index()).isEqualTo(4);
        assertThat(sections.get(3).level()).isEqualTo(2);
        assertThat(sections.get(3).title()).isEqualTo("1.2 实施周期");
        assertThat(sections.get(3).path()).isEqualTo("第一章 项目背景 > 1.2 实施周期");

        // Section 5
        assertThat(sections.get(4).index()).isEqualTo(5);
        assertThat(sections.get(4).level()).isEqualTo(1);
        assertThat(sections.get(4).title()).isEqualTo("第二章 商务要求");
        assertThat(sections.get(4).path()).isEqualTo("第二章 商务要求");
    }

    @Test
    @DisplayName("解析无 # 的中文章节与数字编号标题")
    void testChineseAndNumberedHeadings() {
        String md = """
                第一章 采购需求
                这是第一章的正文内容。
                第一节 服务范围
                包括云原生系统搭建。
                1.1 总体架构
                微服务与容器化方案。
                （一）数据库设计
                使用 MySQL 8.0 规范。
                1. 数据备份策略
                每日全量备份。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        List<MarkdownSourceSectionIndex.Section> sections = index.getSections();

        assertThat(sections).hasSize(5);
        assertThat(sections.get(0).title()).isEqualTo("第一章 采购需求");
        assertThat(sections.get(0).level()).isEqualTo(1);

        assertThat(sections.get(1).title()).isEqualTo("第一节 服务范围");
        assertThat(sections.get(1).path()).isEqualTo("第一章 采购需求 > 第一节 服务范围");

        assertThat(sections.get(2).title()).isEqualTo("1.1 总体架构");
        assertThat(sections.get(2).path()).isEqualTo("第一章 采购需求 > 第一节 服务范围 > 1.1 总体架构");

        assertThat(sections.get(3).title()).isEqualTo("（一）数据库设计");
        assertThat(sections.get(4).title()).isEqualTo("1. 数据备份策略");
    }

    @Test
    @DisplayName("唯一命中：跨多行空白归一化精确匹配，成功提取标题与路径")
    void testUniqueMatch_successWithProvenance() {
        String md = """
                # 第一章 技术方案

                ## 1.1 系统架构设计
                本项目采用 Spring Boot 3.2 与 Java 21 微服务架构，
                实现高吞吐、高可用的投标管理支撑。

                ## 1.2 数据安全方案
                采用国密算法进行敏感数据加密存储。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);

        String snippet = "本项目采用  Spring Boot 3.2\n与 Java 21 微服务架构";
        MarkdownSourceSectionIndex.Provenance prov = index.locate(snippet);

        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("1.1 系统架构设计");
        assertThat(prov.path()).isEqualTo("第一章 技术方案 > 1.1 系统架构设计");
        assertThat(prov.index()).isEqualTo(2);
    }

    @Test
    @DisplayName("零命中：片段不存在于文档中返回 null")
    void testZeroHit_returnsNull() {
        String md = """
                # 第一章 概述
                这是正文。
                """;
        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        MarkdownSourceSectionIndex.Provenance prov = index.locate("完全不存在的文本内容");
        assertThat(prov).isNull();
    }

    @Test
    @DisplayName("跨章节多命中：同文本出现在不同章节时返回 null（诚实降级，避免猜填）")
    void testCrossChapterConflict_returnsNull() {
        String md = """
                # 第一章 技术要求
                双方需严格按期交付。

                # 第二章 商务要求
                双方需严格按期交付。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        MarkdownSourceSectionIndex.Provenance prov = index.locate("双方需严格按期交付");
        assertThat(prov).isNull();
    }

    @Test
    @DisplayName("同一章节内多次出现同一文本：去重后仍属于唯一章节，成功返回")
    void testSameSectionDuplicate_returnsSection() {
        String md = """
                # 第一章 技术要求
                高可用架构至关重要。
                由于高可用架构至关重要，系统做了多机房部署。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        MarkdownSourceSectionIndex.Provenance prov = index.locate("高可用架构至关重要");
        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("第一章 技术要求");
        assertThat(prov.index()).isEqualTo(1);
    }

    @Test
    @DisplayName("仅命中评分项标题：返回 null，不误认来源标题")
    void testOnlyMatchScoringPointTitle_returnsNull() {
        String md = """
                # 第一章 招标公告
                评分项名称：技术方案先进性
                正文描述...
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        // 传入评分项标题相同的内容
        MarkdownSourceSectionIndex.Provenance prov = index.locate("技术方案先进性", "技术方案先进性");
        assertThat(prov).isNull();
    }

    @Test
    @DisplayName("支持抽取逻辑页码标记（<!-- Page X -->）")
    void testLogicalPageNumberExtraction() {
        String md = """
                <!-- Page 1 -->
                # 第一章 项目概述
                概述内容。

                <!-- Page 12 -->
                ## 1.1 技术架构
                我们采用分层解耦架构设计。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        MarkdownSourceSectionIndex.Provenance prov = index.locate("采用分层解耦架构设计");

        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("1.1 技术架构");
        assertThat(prov.pageNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("边界情况：空文本/null输入防御")
    void testNullAndEmptyInputs() {
        MarkdownSourceSectionIndex index1 = MarkdownSourceSectionIndex.parse(null);
        assertThat(index1.getSections()).isEmpty();
        assertThat(index1.locate("test")).isNull();

        MarkdownSourceSectionIndex index2 = MarkdownSourceSectionIndex.parse("    ");
        assertThat(index2.getSections()).isEmpty();
        assertThat(index2.locate("test")).isNull();

        MarkdownSourceSectionIndex index3 = MarkdownSourceSectionIndex.parse("# 标题\n内容");
        assertThat(index3.locate(null)).isNull();
        assertThat(index3.locate("   ")).isNull();
    }

    @Test
    @DisplayName("混排章节标题的字段契约：层级、序号与祖先路径稳定")
    void mixedHeadingContractMatchesPythonSelfTestTable() {
        String md = "# 第一章\n一、 中文章节\n第2节 服务\n1.1 数字\n（1）条目\n###### 末级\n正文";
        List<MarkdownSourceSectionIndex.Section> sections = MarkdownSourceSectionIndex.parse(md).getSections();
        assertThat(sections).extracting(MarkdownSourceSectionIndex.Section::level)
                .containsExactly(1, 2, 2, 3, 4, 6);
        assertThat(sections).extracting(MarkdownSourceSectionIndex.Section::index)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(sections.get(4).path())
                .isEqualTo("第一章 > 第2节 服务 > 1.1 数字 > （1）条目");
    }

    @Test
    @DisplayName("句子型伪标题：# 正文句子不作为章节，片段归入前一个真实章节")
    void testSentenceLikeHashHeadingRejected() {
        String md = """
                # 13.2. 对接平台对接成功案例及证明材料
                本章节说明平台对接情况。

                ## 13.2.1. 对接成功案例清单
                序号客户名称所属集团名称企业类型1启明信息技术股份有限公司启明集团API大型企业

                ## 因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例 188 个以及部分系统对接。
                2河北省公共资源交易中心内部网API大型企业3中盐常州化工股份有限公司内部网API大型企业
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        MarkdownSourceSectionIndex.Provenance prov = index.locate(
                "2河北省公共资源交易中心内部网API大型企业3中盐常州化工股份有限公司内部网API大型企业");

        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("13.2.1. 对接成功案例清单");
        assertThat(prov.path()).isEqualTo("13.2. 对接平台对接成功案例及证明材料 > 13.2.1. 对接成功案例清单");
    }

    @Test
    @DisplayName("句子型伪标题：无 # 的正文句（含句读）不作为章节边界")
    void testSentenceLikePlainHeadingRejected() {
        String md = """
                第一章 技术要求
                1.2 概述，本节说明实施要点
                高可用架构至关重要。

                3.4 建设目标，涵盖内容如下所述
                3.5 建设计划，分三期推进；同时包含后续说明
                1启明信息技术股份有限公司API大型企业
                """;

        List<MarkdownSourceSectionIndex.Section> sections = MarkdownSourceSectionIndex.parse(md).getSections();

        assertThat(sections).extracting(MarkdownSourceSectionIndex.Section::title)
                .containsExactly("第一章 技术要求", "1.2 概述，本节说明实施要点", "3.4 建设目标，涵盖内容如下所述");
    }

    @Test
    @DisplayName("句子型伪标题：超长标题（>80 字）不作为章节边界")
    void testOverlongHeadingRejected() {
        String md = """
                # 第一章 总体方案
                ## 本项目实施团队将严格按照招标文件要求的工期节点组织施工并在各个阶段安排专职人员对接客户需求确保交付质量与进度可控且全过程接受甲方监督检查并定期提交阶段性成果报告以便于双方及时核对实施效果
                实施保障措施说明文字。
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        List<MarkdownSourceSectionIndex.Section> sections = index.getSections();

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).title()).isEqualTo("第一章 总体方案");

        MarkdownSourceSectionIndex.Provenance prov = index.locate("实施保障措施说明文字");
        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("第一章 总体方案");
    }

    @Test
    @DisplayName("章节契约：命中无编号合法小标题（## 项目实施）正常给出来源")
    void testUnnumberedHeadingGivesProvenance() {
        String md = """
                # 13.2. 对接平台对接成功案例及证明材料
                本章节说明平台对接情况。

                ## 项目实施
                1启明信息技术股份有限公司API大型企业
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);

        // 层级树仍接受无编号小标题（它可能是真实排版层级），故它确实是一个 section
        assertThat(index.getSections()).extracting(MarkdownSourceSectionIndex.Section::title)
                .containsExactly("13.2. 对接平台对接成功案例及证明材料", "项目实施");
        assertThat(index.getSections().get(1).path())
                .isEqualTo("13.2. 对接平台对接成功案例及证明材料 > 项目实施");

        // 项目实施是非句子型、非纯编号的合法小标题，正常给出来源
        MarkdownSourceSectionIndex.Provenance prov = index.locate("1启明信息技术股份有限公司API大型企业");
        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("项目实施");
        assertThat(prov.path()).isEqualTo("13.2. 对接平台对接成功案例及证明材料 > 项目实施");
    }

    @Test
    @DisplayName("章节契约：只有编号没有标题文字的章节（## 第一章）不给来源，返回 null")
    void testNumberOnlyHeadingGivesNoProvenance() {
        String md = """
                # 13.2. 对接平台对接成功案例及证明材料
                本章节说明平台对接情况。

                ## 第一章
                1启明信息技术股份有限公司API大型企业
                """;

        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);

        // 层级树同样接受它（有编号、无句读），故它确实是一个 section
        assertThat(index.getSections()).extracting(MarkdownSourceSectionIndex.Section::title)
                .containsExactly("13.2. 对接平台对接成功案例及证明材料", "第一章");

        // 但「章节号 + 标题文字」缺了标题文字，指不到具体内容 —— 宁可无来源
        assertThat(index.locate("1启明信息技术股份有限公司API大型企业")).isNull();
    }

    @Test
    @DisplayName("章节契约：已有可核对的编号章节，回归为不因严格化而误伤")
    void testNumberedHeadingStillGivesProvenance() {
        String md = """
                # 13.2. 对接平台对接成功案例及证明材料
                ## 13.2.1. 对接成功案例清单
                1启明信息技术股份有限公司API大型企业
                """;

        MarkdownSourceSectionIndex.Provenance prov =
                MarkdownSourceSectionIndex.parse(md).locate("1启明信息技术股份有限公司API大型企业");

        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("13.2.1. 对接成功案例清单");
        assertThat(prov.index()).isEqualTo(2);
    }

    @Test
    @DisplayName("章节标题契约规则：编号在串首且过句子门才放行，无编号一律拒绝")
    void testIsChapterTitleRule() {
        assertThat(MarkdownTitleRules.isChapterTitle("13.2.1. 对接成功案例清单")).isTrue();
        assertThat(MarkdownTitleRules.isChapterTitle("第一章 项目背景")).isTrue();
        assertThat(MarkdownTitleRules.isChapterTitle("一、总体要求")).isTrue();
        assertThat(MarkdownTitleRules.isChapterTitle("（1）项目概述")).isTrue();

        assertThat(MarkdownTitleRules.isChapterTitle("项目实施")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("技术方案 1.2 先进性")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("13.2 建设计划，分三期推进；同时包含后续说明")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle(null)).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("   ")).isFalse();
    }

    @Test
    @DisplayName("「章节号 + 标题文字」：只有编号没有标题文字的串不是已验证章节")
    void testChapterTitleRequiresTitleText() {
        assertThat(MarkdownTitleRules.isChapterTitle("1.")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("3、")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("13.2.1.")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("第一章")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("第一节")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("一、")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("（1）")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("①")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("A.")).isFalse();
        // 全角空格（U+3000）：Java 的 trim/\s 与 JS 不同，两侧字符类必须显式覆盖，否则会在此处与前端分叉
        assertThat(MarkdownTitleRules.isChapterTitle("第一章　")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("　第一章 项目背景")).isTrue();
    }

    @Test
    @DisplayName("字母列表标记不是章节号：A. 附录清单 / B、服务方案 / a. 投标函 一律不给来源")
    void testLetterMarkerIsNotChapterTitle() {
        assertThat(MarkdownTitleRules.isChapterTitle("A. 附录清单")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("B、服务方案")).isFalse();
        assertThat(MarkdownTitleRules.isChapterTitle("a. 投标函")).isFalse();
    }

    @Test
    @DisplayName("句子型伪标题识别规则：句读/超长拒绝，序号+短标题放行")
    void testIsSentenceLikeTitleRule() {
        assertThat(MarkdownTitleRules.isSentenceLikeTitle(
                "因投标文件篇幅有限，如下仅展示自 2023 年 1 月 1 日至本次投标截止日部分西域成功对接案例188个以及部分系统对接。"))
                .isTrue();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle("双方需严格按期交付！")).isTrue();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle(
                "本项目实施团队将严格按照招标文件要求的工期节点组织施工并在各个阶段安排专职人员对接客户需求确保交付质量与进度可控且全过程接受甲方监督检查并定期提交阶段性成果报告以便于双方及时核对实施效果与偏差情况"))
                .isTrue();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle(null)).isTrue();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle("   ")).isTrue();

        assertThat(MarkdownTitleRules.isSentenceLikeTitle("13.2.1. 对接成功案例清单")).isFalse();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle("第一章 项目背景")).isFalse();
        assertThat(MarkdownTitleRules.isSentenceLikeTitle("1.2 建设目标，思路与路径")).isFalse();
    }

    @Test
    @DisplayName("解析带制表符与页码的Markdown标题时自动清洗页码与噪声")
    void testMarkdownHeadingWithTrailingPageNumber() {
        String md = """
                # 一、 投标函\t1
                正文内容1
                ## 九、 本项目相关其他内容\t1806
                正文内容2
                ### 7. 响应性评审标准承诺函\t2003
                响应性评审承诺正文
                """;
        MarkdownSourceSectionIndex index = MarkdownSourceSectionIndex.parse(md);
        assertThat(index.getSections()).hasSize(3);

        MarkdownSourceSectionIndex.Section s1 = index.getSections().get(0);
        assertThat(s1.title()).isEqualTo("一、 投标函");
        assertThat(s1.path()).isEqualTo("一、 投标函");

        MarkdownSourceSectionIndex.Section s2 = index.getSections().get(1);
        assertThat(s2.title()).isEqualTo("九、 本项目相关其他内容");
        assertThat(s2.path()).isEqualTo("一、 投标函 > 九、 本项目相关其他内容");

        MarkdownSourceSectionIndex.Section s3 = index.getSections().get(2);
        assertThat(s3.title()).isEqualTo("7. 响应性评审标准承诺函");
        assertThat(s3.path()).isEqualTo("一、 投标函 > 九、 本项目相关其他内容 > 7. 响应性评审标准承诺函");

        MarkdownSourceSectionIndex.Provenance prov = index.locate("响应性评审承诺正文");
        assertThat(prov).isNotNull();
        assertThat(prov.title()).isEqualTo("7. 响应性评审标准承诺函");
        assertThat(prov.path()).isEqualTo("一、 投标函 > 九、 本项目相关其他内容 > 7. 响应性评审标准承诺函");
    }
}
