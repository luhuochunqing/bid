package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableCellIntegrityCalculatorTest {

    @Test
    @DisplayName("合规表格全量填写无违规")
    void testValidTablePass() {
        String tableText = """
                # 关键条款响应表
                | 序号 | 条款名称 | 招标要求 | 投标响应 | 偏离说明 |
                |---|---|---|---|---|
                | 1 | 工期 | 180日历天 | 150日历天，完全满足 | 无偏离 |
                | 2 | 质保期 | 3年 | 3年原厂质保 | 无 |
                | 3 | 付款方式 | 3-3-3-1 | 完全响应招标文件约定 | / |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(1, tableText);

        assertThat(result.hasViolation()).isFalse();
        assertThat(result.violations()).isEmpty();
        assertThat(result.firstErrorPage()).isNull();
    }

    @Test
    @DisplayName("检测到空单元格未填写时记录违规")
    void testEmptyCellDetected() {
        String tableText = """
                | 序号 | 项目名称 | 规格型号 | 数量 | 单价 |
                |---|---|---|---|---|
                | 1 | 服务器 | 2U机架式 | 10 | |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(3, tableText);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.violations()).hasSize(1);
        TableCellIntegrityCalculator.CellViolation v = result.violations().get(0);
        assertThat(v.pageNumber()).isEqualTo(3);
        assertThat(v.rowNumber()).isEqualTo(1);
        assertThat(v.colNumber()).isEqualTo(5);
        assertThat(v.colHeader()).isEqualTo("单价");
        assertThat(v.violationType()).isEqualTo("空单元格未填写");
        assertThat(result.summaryMessage()).contains("第 3 页表格第 1 行[单价]空单元格未填写");
    }

    @Test
    @DisplayName("检测到关键列斜杠/横杠留白时记录违规")
    void testSlashBlankDetected() {
        String tableText = """
                | 序号 | 商务条款 | 招标要求 | 投标响应 |
                |---|---|---|---|
                | 1 | 交货期 | 30天 | / |
                | 2 | 售后服务 | 7x24小时 | -- |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(5, tableText);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.violations()).hasSize(2);
        assertThat(result.violations().get(0).violationType()).isEqualTo("无效斜杠/横杠留白");
        assertThat(result.violations().get(0).cellContent()).isEqualTo("/");
        assertThat(result.violations().get(1).cellContent()).isEqualTo("--");
    }

    @Test
    @DisplayName("检测到模板占位符留白时记录违规")
    void testPlaceholderDetected() {
        String tableText = """
                | 序号 | 条款 | 要求 | 响应 |
                |---|---|---|---|
                | 1 | 工期 | 90天 | 【请填写工期】 |
                | 2 | 质保 | 2年 | _______ |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(2, tableText);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.violations()).hasSize(2);
        assertThat(result.violations().get(0).violationType()).isEqualTo("未替换模板占位符");
        assertThat(result.violations().get(1).violationType()).isEqualTo("未替换模板占位符");
    }

    @Test
    @DisplayName("偏离说明列允许/与无等正常免除")
    void testExemptColumnAllowed() {
        String tableText = """
                | 序号 | 参数项 | 响应值 | 偏离说明 | 备注 |
                |---|---|---|---|---|
                | 1 | 内存 | 64GB | / | 无 |
                | 2 | CPU | 32核 | 无 | -- |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(1, tableText);

        assertThat(result.hasViolation()).isFalse();
    }

    @Test
    @DisplayName("无分隔行表格：首行不再被误判为表头而漏检")
    void testNoSeparatorTableFirstRowChecked() {
        String tableText = """
                报价明细
                | 1 | 工期 | 60天 | / |
                | 2 | 质保 | 2年 |  |
                """;
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateText(2, tableText);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.violations()).hasSize(2);
        assertThat(result.violations().get(0).violationType()).isEqualTo("无效斜杠/横杠留白");
        assertThat(result.violations().get(1).violationType()).isEqualTo("空单元格未填写");
    }

    @Test
    @DisplayName("validateDocument 排除目录点线页干扰")
    void testValidateDocumentSkipsCatalog() {
        DocumentContent.PageContent catalogPage = new DocumentContent.PageContent(
                1,
                """
                目 录
                一、前附表关键条款响应表 .................... 1
                | 序号 | 章节 | 页码 |
                |---|---|---|
                | 1 | 前附表 | |
                """,
                List.of()
        );
        DocumentContent.PageContent substantivePage = new DocumentContent.PageContent(
                2,
                """
                | 序号 | 条款 | 响应 | 偏离说明 |
                |---|---|---|---|
                | 1 | 交付期 | 满足招标要求 | 无 |
                """,
                List.of()
        );

        DocumentContent doc = new DocumentContent("test.pdf", "hash", "", List.of(catalogPage, substantivePage));
        TableCellIntegrityCalculator.TableIntegrityResult result =
                TableCellIntegrityCalculator.validateDocument(doc);

        assertThat(result.hasViolation()).isFalse();
    }
}
