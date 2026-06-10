// Input: MarginService mock
// Output: MarginExportService unit tests — Excel export with header/data verification
// Pos: Test/纯核心验证
package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarginExportServiceTest {

    @Mock
    private MarginService marginService;

    private MarginExportService exportService;

    private static final String[] EXPECTED_HEADERS = {
            "项目名称", "业主单位", "项目负责人", "投标负责人",
            "缴纳金额", "缴纳日期", "缴纳方式",
            "收款方名称", "收款方账号",
            "应退日期", "退回金额", "转服务费金额", "实际退回日期", "状态"
    };

    @BeforeEach
    void setUp() {
        exportService = new MarginExportService(marginService);
    }

    @Test
    @DisplayName("导出空台账 — 仅表头行、标题正确")
    void exportToExcel_emptyData_returnsHeadersOnly() throws Exception {
        when(marginService.getList(eq(1L), eq("admin"), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        byte[] result = exportService.exportToExcel(1L, "admin", Map.of());

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("保证金台账");
            // 仅有表头行
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
            // 验证表头内容
            var headerRow = sheet.getRow(0);
            assertThat(headerRow.getPhysicalNumberOfCells()).isEqualTo(EXPECTED_HEADERS.length);
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                assertThat(headerRow.getCell(i).getStringCellValue()).isEqualTo(EXPECTED_HEADERS[i]);
            }
        }
    }

    @Test
    @DisplayName("导出含数据的台账 — 数据行正确写入")
    void exportToExcel_withData_writesRows() throws Exception {
        MarginDTO dto = MarginDTO.builder()
                .projectName("测试项目")
                .ownerUnit("业主单位A")
                .projectLeaderName("张三")
                .biddingLeaderName("李四")
                .depositAmount(new BigDecimal("50000.00"))
                .paymentDate(LocalDateTime.of(2026, 1, 15, 0, 0))
                .depositPaymentMethod("电汇")
                .payeeName("收款方A")
                .payeeAccount("6222021234567890")
                .expectedReturnDate(LocalDateTime.of(2026, 6, 15, 0, 0))
                .returnedAmount(new BigDecimal("50000.00"))
                .serviceFeeAmount(BigDecimal.ZERO)
                .actualReturnDate(LocalDateTime.of(2026, 6, 10, 0, 0))
                .statusLabel("已退回")
                .build();

        when(marginService.getList(eq(1L), eq("admin"), any(), anyInt(), anyInt()))
                .thenReturn(List.of(dto));

        byte[] result = exportService.exportToExcel(1L, "admin", Map.of());

        assertThat(result).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1); // header + 1 data row
            var dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("测试项目");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("业主单位A");
            assertThat(dataRow.getCell(4).getNumericCellValue()).isEqualTo(50000.00);
        }
    }

    @Test
    @DisplayName("导出多台账数据 — 每行对应一条记录")
    void exportToExcel_multipleRows_returnsCorrectCount() throws Exception {
        List<MarginDTO> data = List.of(
                MarginDTO.builder().projectName("项目A").build(),
                MarginDTO.builder().projectName("项目B").build(),
                MarginDTO.builder().projectName("项目C").build()
        );

        when(marginService.getList(eq(1L), eq("admin"), any(), anyInt(), anyInt()))
                .thenReturn(data);

        byte[] result = exportService.exportToExcel(1L, "admin", Map.of());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(3); // header + 3 data rows
        }
    }
}
