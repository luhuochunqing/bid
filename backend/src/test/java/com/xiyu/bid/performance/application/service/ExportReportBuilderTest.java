package com.xiyu.bid.performance.application.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出报告生成器单元测试（spec 4.3.7）
 * 验证 _导出报告.txt 文本格式。
 */
class ExportReportBuilderTest {

    @Test
    void build_noFilter_noFailures_containsBasicInfo() {
        String report = ExportReportBuilder.build(
                LocalDateTime.of(2026, 7, 3, 15, 30, 0),
                12,
                Set.of(),
                35,
                35,
                List.of());

        assertThat(report).startsWith("导出报告");
        assertThat(report).contains("导出时间: 2026-07-03 15:30:00");
        assertThat(report).contains("导出业绩数: 12");
        assertThat(report).contains("附件类型筛选: 全部");
        assertThat(report).contains("附件总数: 35");
        assertThat(report).contains("成功: 35");
        assertThat(report).contains("失败: 0");
        assertThat(report).doesNotContain("失败清单");
    }

    @Test
    void build_withFilter_showsTypeNames() {
        String report = ExportReportBuilder.build(
                LocalDateTime.of(2026, 7, 3, 15, 30, 0),
                5,
                Set.of("CONTRACT_AGREEMENT", "BID_NOTICE"),
                10,
                10,
                List.of());

        assertThat(report).contains("附件类型筛选: 合同协议, 中标通知书");
    }

    @Test
    void build_withFailures_listsFailureDetails() {
        var failures = List.of(
                new ExportReportBuilder.FailedAttachmentRecord(
                        "XXX合同", "MALL_SCREENSHOT", "screenshot_01.png", "文件不存在"),
                new ExportReportBuilder.FailedAttachmentRecord(
                        "YYY合同", "OTHER", "other.pdf", "权限不足"));

        String report = ExportReportBuilder.build(
                LocalDateTime.of(2026, 7, 3, 15, 30, 0),
                12,
                Set.of(),
                35,
                33,
                failures);

        assertThat(report).contains("失败: 2");
        assertThat(report).contains("失败清单");
        assertThat(report).contains("1. 业绩「XXX合同」/ 商城截图 / screenshot_01.png → 读取失败: 文件不存在");
        assertThat(report).contains("2. 业绩「YYY合同」/ 其他附件 / other.pdf → 读取失败: 权限不足");
    }

    @Test
    void build_nullFilter_showsAll() {
        String report = ExportReportBuilder.build(
                LocalDateTime.of(2026, 7, 3, 15, 30, 0),
                1,
                null,
                0,
                0,
                List.of());

        assertThat(report).contains("附件类型筛选: 全部");
    }

    @Test
    void build_allSevenTypeNamesMapped() {
        // 验证 7 种类型中文名都能正确映射
        String report = ExportReportBuilder.build(
                LocalDateTime.of(2026, 7, 3, 15, 30, 0),
                1,
                Set.of("CONTRACT_AGREEMENT", "MALL_SCREENSHOT", "SOE_DIRECTORY",
                        "RELATIONSHIP_PROOF", "CATEGORY_PAGE", "BID_NOTICE", "OTHER"),
                0, 0, List.of());

        assertThat(report).contains("合同协议");
        assertThat(report).contains("商城截图");
        assertThat(report).contains("央企名录");
        assertThat(report).contains("关系证明");
        assertThat(report).contains("品类页");
        assertThat(report).contains("中标通知书");
        assertThat(report).contains("其他附件");
    }
}
