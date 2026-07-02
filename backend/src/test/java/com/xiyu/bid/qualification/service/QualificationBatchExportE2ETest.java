package com.xiyu.bid.qualification.service;

import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CO-471 端到端测试：从 batchExportExcel 到 Excel 文件解析，验证数据行是否存在。
 * 使用真实的 QualificationExcelSupport（不 mock），只 mock flatQuery。
 */
class QualificationBatchExportE2ETest {

    private QualificationExportService exportService;
    private QualificationFlatQuery mockFlatQuery;
    private QualificationExcelSupport realExcelSupport;

    @BeforeEach
    void setUp() {
        mockFlatQuery = mock(QualificationFlatQuery.class);
        realExcelSupport = new QualificationExcelSupport();
        exportService = new QualificationExportService(mockFlatQuery, realExcelSupport);
    }

    @Test
    @DisplayName("完整流程：batchExportExcel 生成的 Excel 应包含数据行")
    void batchExportExcel_ShouldProduceExcelWithDataRows() throws IOException {
        // 准备测试数据
        List<QualificationDTO> allData = List.of(
                buildDTO(1L, "ISO9001质量管理体系认证", "FIRST", "中国计量认证中心", "CERT-001"),
                buildDTO(2L, "ISO14001环境管理体系认证", "FIRST", "中国计量认证中心", "CERT-002"),
                buildDTO(3L, "OHSAS18001职业健康安全管理体系认证", "FIRST", "中国计量认证中心", "CERT-003")
        );

        when(mockFlatQuery.listAll(null, null)).thenReturn(allData);

        // 选中 ID 1 和 3
        List<Long> selectedIds = List.of(1L, 3L);

        // 执行
        byte[] excelBytes = exportService.batchExportExcel(selectedIds);

        // 解析 Excel 验证
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            System.out.println("=== Excel 解析结果 ===");
            System.out.println("Sheet name: " + sheet.getSheetName());
            System.out.println("Last row num: " + lastRowNum);

            // 打印所有行内容
            for (int i = 0; i <= lastRowNum; i++) {
                var row = sheet.getRow(i);
                if (row != null) {
                    StringBuilder sb = new StringBuilder("Row " + i + ": ");
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        var cell = row.getCell(c);
                        sb.append(cell != null ? cell.getStringCellValue() : "NULL").append(" | ");
                    }
                    System.out.println(sb);
                }
            }

            // 验证：应该有 1 行表头 + 2 行数据 = 3 行
            assertEquals(2, lastRowNum, "应该有 2 行数据（不含表头），但实际 lastRowNum=" + lastRowNum);

            // 验证表头
            var headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("证书名称", headerRow.getCell(0).getStringCellValue());

            // 验证数据行
            var dataRow1 = sheet.getRow(1);
            assertNotNull(dataRow1);
            assertEquals("ISO9001质量管理体系认证", dataRow1.getCell(0).getStringCellValue());

            var dataRow2 = sheet.getRow(2);
            assertNotNull(dataRow2);
            assertEquals("OHSAS18001职业健康安全管理体系认证", dataRow2.getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("空数据时 Excel 应只有表头")
    void batchExportExcel_WithNoMatchingIds_ShouldHaveOnlyHeader() throws IOException {
        List<QualificationDTO> allData = List.of(
                buildDTO(1L, "证书A", "FIRST", "机构A", "CERT-001")
        );

        when(mockFlatQuery.listAll(null, null)).thenReturn(allData);

        // 选中不存在的 ID
        List<Long> selectedIds = List.of(999L);

        byte[] excelBytes = exportService.batchExportExcel(selectedIds);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            System.out.println("=== 空数据 Excel 解析结果 ===");
            System.out.println("Last row num: " + lastRowNum);

            // 只有表头，没有数据行
            assertEquals(0, lastRowNum, "应该只有表头（lastRowNum=0），但实际=" + lastRowNum);
        }
    }

    @Test
    @DisplayName("全部选中时 Excel 应包含所有数据行")
    void batchExportExcel_WithAllIdsSelected_ShouldHaveAllRows() throws IOException {
        List<QualificationDTO> allData = List.of(
                buildDTO(1L, "证书A", "FIRST", "机构A", "CERT-001"),
                buildDTO(2L, "证书B", "FIRST", "机构B", "CERT-002"),
                buildDTO(3L, "证书C", "FIRST", "机构C", "CERT-003")
        );

        when(mockFlatQuery.listAll(null, null)).thenReturn(allData);

        List<Long> selectedIds = List.of(1L, 2L, 3L);

        byte[] excelBytes = exportService.batchExportExcel(selectedIds);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum(), "应该有 3 行数据");
        }
    }

    @Test
    @DisplayName("flatQuery.listAll 返回空时 Excel 应只有表头")
    void batchExportExcel_WhenFlatQueryReturnsEmpty_ShouldHaveOnlyHeader() throws IOException {
        when(mockFlatQuery.listAll(null, null)).thenReturn(List.of());

        List<Long> selectedIds = List.of(1L, 2L);

        byte[] excelBytes = exportService.batchExportExcel(selectedIds);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals(0, sheet.getLastRowNum(), "应该只有表头");
        }
    }

    private QualificationDTO buildDTO(Long id, String name, String level, String issuer, String certNo) {
        return QualificationDTO.builder()
                .id(id)
                .name(name)
                .level(level)
                .issuer(issuer)
                .certificateNo(certNo)
                .issueDate(LocalDate.of(2024, 1, 15))
                .expiryDate(LocalDate.of(2027, 12, 31))
                .agency("代理机构")
                .agencyContact("13800138000")
                .certScope("认证范围")
                .status("in_stock")
                .holderName("持有人")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
