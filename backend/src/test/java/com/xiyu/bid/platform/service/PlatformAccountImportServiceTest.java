// Input: PlatformAccountService mock, InputStream/Workbook helpers
// Output: PlatformAccountImportService unit tests — template generation, Excel parsing
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAccountImportServiceTest {

    @Mock
    private PlatformAccountService platformAccountService;

    @Captor
    private ArgumentCaptor<PlatformAccountCreateRequest> requestCaptor;

    private PlatformAccountImportService importService;

    private static final User USER = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        importService = new PlatformAccountImportService(platformAccountService);
    }

    @Test
    @DisplayName("生成导入模板 — 返回有效 xlsx 字节")
    void generateTemplate_returnsValidBytes() throws Exception {
        byte[] template = importService.generateTemplate();
        assertThat(template).isNotEmpty();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("平台账号导入模板");
            assertThat(wb.getSheetAt(0).getRow(0).getPhysicalNumberOfCells()).isEqualTo(9);
        }
    }

    @Test
    @DisplayName("导入有效 Excel — 逐行调用 createAccount")
    void importFromExcel_validData_callsCreateAccount() throws Exception {
        byte[] excelBytes = createTestExcel(
            new String[]{"测试平台A", "政府采购网", "userA", "pwdA", "", "", "", "", ""},
            new String[]{"测试平台B", "招投标平台", "userB", "pwdB", "http://example.com", "", "", "", ""}
        );

        when(platformAccountService.createAccount(any(), any())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = importService.importFromExcel(new ByteArrayInputStream(excelBytes), USER);

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("success")).isEqualTo(2);
        verify(platformAccountService, times(2)).createAccount(requestCaptor.capture(), eq(USER));

        PlatformAccountCreateRequest first = requestCaptor.getAllValues().get(0);
        assertThat(first.getAccountName()).isEqualTo("测试平台A");
        assertThat(first.getPlatformType().name()).isEqualTo("GOV_PROCUREMENT");

        PlatformAccountCreateRequest second = requestCaptor.getAllValues().get(1);
        assertThat(second.getAccountName()).isEqualTo("测试平台B");
        assertThat(second.getPlatformType().name()).isEqualTo("BIDDING_PLATFORM");
    }

    @Test
    @DisplayName("导入包含无效行的 Excel — 有效行成功、无效行计入失败")
    void importFromExcel_partialFailure_reportsErrors() throws Exception {
        byte[] excelBytes = createTestExcel(
            new String[]{"", "政府采购网", "userA", "pwdA", "", "", "", "", ""},          // 缺少平台名称
            new String[]{"测试平台B", "招投标平台", "userB", "pwdB", "", "", "", "", ""}   // 有效
        );

        when(platformAccountService.createAccount(any(), any())).thenReturn(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = importService.importFromExcel(new ByteArrayInputStream(excelBytes), USER);

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(1);
        verify(platformAccountService, times(1)).createAccount(any(), eq(USER));
    }

    // ── helpers ──

    private static byte[] createTestExcel(String[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            // header row
            var header = sheet.createRow(0);
            for (int i = 0; i < 9; i++) header.createCell(i).setCellValue("H" + i);
            // data rows
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
