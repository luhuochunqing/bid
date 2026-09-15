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
}
