package com.xiyu.bid.project.service;

import com.xiyu.bid.project.dto.ProjectDTO;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CO-553: 项目列表导出表格与系统显示一致性单测。
 *
 * 验证点：
 * 1. 列定义与前端 List.vue 表格 19 列完全对齐（表头完整）。
 * 2. 枚举字段（项目状态/项目类型/客户类型/优先级/项目阶段/来源平台）输出中文，不输出英文枚举名。
 * 3. 已移除前端无对应列的"客户等级"和"中标状态"。
 * 4. revenue 格式化为 2 位小数（与前端 toFixed(2) 一致）。
 * 5. 数据源复用 ProjectQueryService（与列表接口同源）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectExportServiceTest {

    @Mock
    private ProjectQueryService projectQueryService;

    private ProjectExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ProjectExportService(projectQueryService);
    }

    @Test
    void exportProjectsAsExcel_shouldHave19ColumnsMatchingFrontendTable() throws Exception {
        when(projectQueryService.getAllProjects()).thenReturn(List.of(buildSampleDTO()));

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow header = sheet.getRow(0);

            // CO-553: 19 列，与前端 List.vue 表格列完全对齐（不含序号/选择列）
            assertThat(header.getLastCellNum()).isEqualTo((short) 19);

            List<String> headers = readRow(header, 19);
            assertThat(headers).containsExactly(
                    "项目名称", "项目状态", "来源平台", "招标主体", "计划入围供应商数量",
                    "创建时间", "开标时间", "投标月份", "项目类型", "客户营收（亿）",
                    "客户类型", "优先级", "总部所在地", "项目负责人", "项目负责人部门",
                    "投标负责人", "投标辅助人员", "项目阶段", "投标平台");

            // 已移除前端无对应列的"客户等级"和"中标状态"
            assertThat(headers).doesNotContain("客户等级", "中标状态", "业主单位", "入围家数", "投标状态");
        }
    }

    @Test
    void exportProjectsAsExcel_shouldMapEnumValuesToChinese() throws Exception {
        when(projectQueryService.getAllProjects()).thenReturn(List.of(buildSampleDTO()));

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow dataRow = sheet.getRow(1);
            List<String> cells = readRow(dataRow, 19);

            // 枚举字段必须输出中文，不能是英文枚举名
            assertThat(cells.get(1)).isEqualTo("已立项");          // bidStatus=INITIATED
            assertThat(cells.get(2)).isEqualTo("CRM创建");         // sourceModule=CRM_OPPORTUNITY
            assertThat(cells.get(8)).isEqualTo("办公");             // projectType=OFFICE
            assertThat(cells.get(10)).isEqualTo("央企");            // customerType=CENTRAL_SOE
            assertThat(cells.get(11)).isEqualTo("S级");             // priority=S
            assertThat(cells.get(17)).isEqualTo("项目立项");         // stage=INITIATED

            // 非枚举字段保持原值
            assertThat(cells.get(0)).isEqualTo("测试项目");
            assertThat(cells.get(3)).isEqualTo("某招标主体");
            assertThat(cells.get(13)).isEqualTo("张三");   // 项目负责人
            assertThat(cells.get(15)).isEqualTo("李四");   // 投标负责人

            // revenue 格式化为 2 位小数
            assertThat(cells.get(9)).isEqualTo("12.50");
        }
    }

    @Test
    void exportProjectsAsExcel_shouldIncludeSecondaryBiddingLeaderName() throws Exception {
        when(projectQueryService.getAllProjects()).thenReturn(List.of(buildSampleDTO()));

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow dataRow = sheet.getRow(1);
            List<String> cells = readRow(dataRow, 19);

            // CO-551 新增的"投标辅助人员"列必须存在且有值
            assertThat(cells.get(16)).isEqualTo("王五");
        }
    }

    @Test
    void exportProjectsAsExcel_shouldFallbackToOriginalForUnknownEnumValue() throws Exception {
        ProjectDTO dto = buildSampleDTO();
        dto.setProjectType("UNKNOWN_TYPE");
        dto.setBidStatus("CUSTOM_STATUS");
        when(projectQueryService.getAllProjects()).thenReturn(List.of(dto));

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow dataRow = sheet.getRow(1);
            List<String> cells = readRow(dataRow, 19);

            // 未知枚举值 fallback 显示原值（与前端 map[v] || v 一致），避免丢失数据
            assertThat(cells.get(8)).isEqualTo("UNKNOWN_TYPE");
            assertThat(cells.get(1)).isEqualTo("CUSTOM_STATUS");
        }
    }

    @Test
    void exportProjectsAsExcel_shouldApplyBidStatusFilter() throws Exception {
        ProjectDTO matching = buildSampleDTO();
        ProjectDTO nonMatching = buildSampleDTO();
        nonMatching.setName("其他项目");
        nonMatching.setBidStatus("BIDDING");
        when(projectQueryService.getAllProjects()).thenReturn(List.of(matching, nonMatching));

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, "INITIATED",
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            // header + 1 data row（只有 INITIATED 状态的项目）
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("测试项目");
        }
    }

    @Test
    void exportProjectsAsExcel_shouldHandleEmptyProjectList() throws Exception {
        when(projectQueryService.getAllProjects()).thenReturn(List.of());

        var result = exportService.exportProjectsAsExcel(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        try (var wb = readWorkbook(result)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            // 只有表头行，无数据行
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 19);
        }
    }

    // ── helpers ──

    private static ProjectDTO buildSampleDTO() {
        return ProjectDTO.builder()
                .id(1L)
                .name("测试项目")
                .sourceModule("CRM_OPPORTUNITY")
                .ownerUnit("某招标主体")
                .shortlistedCount(5)
                .createdAt(LocalDateTime.of(2025, 7, 8, 10, 0))
                .bidOpenTime(LocalDateTime.of(2025, 8, 1, 14, 0))
                .bidMonth("2025-08")
                .projectType("OFFICE")
                .revenue(new BigDecimal("12.5"))
                .customerType("CENTRAL_SOE")
                .priority("S")
                .region("北京")
                .projectLeaderName("张三")
                .leaderDepartment("技术部")
                .biddingLeaderName("李四")
                .secondaryBiddingLeaderName("王五")
                .stage("INITIATED")
                .bidStatus("INITIATED")
                .biddingPlatform("某平台")
                .build();
    }

    private static XSSFWorkbook readWorkbook(ProjectExportService.ExportResult result) throws IOException {
        try (var in = (ByteArrayInputStream) result.data()) {
            return new XSSFWorkbook(in);
        }
    }

    private static List<String> readRow(XSSFRow row, int count) {
        // shortlistedCount 列以数值写入，需兼容 NUMERIC 与 STRING 两种 cell 类型
        var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        java.util.List<String> values = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            XSSFCell cell = row.getCell(i);
            values.add(cell != null ? formatter.formatCellValue(cell) : "");
        }
        return values;
    }
}
