package com.xiyu.bid.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceFileNameTest extends AbstractExcelExportServiceTest {

    @Test
    void getExportFileName_WithTendersType_ShouldReturnCorrectFileName() {
        String result = excelExportService.getExportFileName("tenders");

        assertThat(result).contains("标讯列表");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_WithProjectsType_ShouldReturnCorrectFileName() {
        String result = excelExportService.getExportFileName("projects");

        assertThat(result).contains("项目列表");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_WithQualificationsType_ShouldReturnCorrectFileName() {
        String result = excelExportService.getExportFileName("qualifications");

        assertThat(result).contains("资质列表");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_WithCasesType_ShouldReturnCorrectFileName() {
        String result = excelExportService.getExportFileName("cases");

        assertThat(result).contains("案例列表");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_WithTemplatesType_ShouldReturnCorrectFileName() {
        String result = excelExportService.getExportFileName("templates");

        assertThat(result).contains("模板列表");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_WithInvalidType_ShouldReturnGenericFileName() {
        String result = excelExportService.getExportFileName("invalid");

        assertThat(result).contains("导出数据");
        assertThat(result).endsWith(".xlsx");
    }

    @Test
    void getExportFileName_ShouldIncludeTimestamp() {
        String result = excelExportService.getExportFileName("tenders");

        assertThat(result).containsPattern("\\d{8}_\\d{6}");
    }
}
