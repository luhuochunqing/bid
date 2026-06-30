package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandAuthImportServiceTest {

    @Mock
    private ManufacturerAuthorizationRepository repository;
    @Mock
    private BrandAuthOperationLogJpaRepository logRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BrandAuthImportService importService;

    @Captor
    private ArgumentCaptor<ManufacturerAuthorization> authCaptor;

    private byte[] manufacturerExcelBytes;
    private byte[] agentExcelBytes;
    private byte[] mixedExcelBytes;

    @BeforeEach
    void setUp() throws IOException {
        manufacturerExcelBytes = createTestExcel("原厂授权", false);
        agentExcelBytes = createTestExcel("代理商授权", true);
        mixedExcelBytes = createMixedTestExcel();

        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            ManufacturerAuthorization auth = invocation.getArgument(0);
            // Simulate saving: return a copy with an ID
            return new ManufacturerAuthorization(
                    1L, auth.authorizationType(), auth.productLine(),
                    auth.brandId(), auth.brandName(), auth.importDomestic(),
                    auth.manufacturerName(), auth.agentName(),
                    auth.authStartDate(), auth.authEndDate(),
                    auth.auth1StartDate(), auth.auth1EndDate(), auth.auth1Remarks(),
                    auth.auth2StartDate(), auth.auth2EndDate(), auth.auth2Remarks(),
                    auth.remarks(), auth.status(), auth.revokeReason(),
                    auth.createdBy(), auth.createdAt(), auth.updatedAt(), auth.version()
            );
        });

        lenient().when(userRepository.findById(1L)).thenReturn(java.util.Optional.empty());
    }

    @Test
    void shouldImportManufacturerRowsSuccessfully() {
        BrandAuthImportService.ImportResult result =
                importService.importExcel(manufacturerExcelBytes, 1L);

        assertNotNull(result);
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getTotalSuccess());
        assertEquals(0, result.getTotalFailed());
        assertEquals(1, result.getSheets().size());
        assertEquals("原厂授权", result.getSheets().get(0).getSheetName());

        verify(repository, times(2)).save(any());
    }

    @Test
    void importManufacturer_operationLogDetailsShouldBeChineseReadable() {
        importService.importExcel(manufacturerExcelBytes, 1L);

        ArgumentCaptor<BrandAuthOperationLogEntity> captor =
                ArgumentCaptor.forClass(BrandAuthOperationLogEntity.class);
        verify(logRepository, times(2)).save(captor.capture());

        for (BrandAuthOperationLogEntity log : captor.getAllValues()) {
            assertEquals("IMPORT", log.getActionType());
            String details = log.getDetails();
            assertNotNull(details);
            // details 不应是 JSON 代码格式
            assertFalse(details.startsWith("{"),
                    "导入操作日志 details 不应是 JSON 代码格式: " + details);
            assertTrue(details.contains("授权类型：原厂授权"),
                    "details 应包含中文授权类型标签: " + details);
            assertTrue(details.contains("产线："),
                    "details 应包含中文产线标签: " + details);
            assertTrue(details.contains("品牌ID："),
                    "details 应包含中文品牌ID标签: " + details);
            assertFalse(details.contains("代理商："),
                    "原厂授权导入日志不应包含代理商字段: " + details);
        }
    }

    @Test
    void importAgent_operationLogDetailsShouldIncludeAgentName() {
        importService.importExcel(agentExcelBytes, 1L);

        ArgumentCaptor<BrandAuthOperationLogEntity> captor =
                ArgumentCaptor.forClass(BrandAuthOperationLogEntity.class);
        verify(logRepository, times(2)).save(captor.capture());

        for (BrandAuthOperationLogEntity log : captor.getAllValues()) {
            String details = log.getDetails();
            assertTrue(details.contains("授权类型：代理商授权"),
                    "代理商导入日志应标注代理商授权: " + details);
            assertTrue(details.contains("代理商："),
                    "代理商导入日志应包含代理商名: " + details);
        }
    }

    @Test
    void shouldImportAgentRowsSuccessfully() {
        BrandAuthImportService.ImportResult result =
                importService.importExcel(agentExcelBytes, 1L);

        assertNotNull(result);
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getTotalSuccess());
        assertEquals(0, result.getTotalFailed());
        assertEquals(1, result.getSheets().size());
        assertEquals("代理商授权", result.getSheets().get(0).getSheetName());

        verify(repository, times(2)).save(any());
    }

    @Test
    void shouldImportMixedSheetsSuccessfully() {
        BrandAuthImportService.ImportResult result =
                importService.importExcel(mixedExcelBytes, 1L);

        assertNotNull(result);
        assertEquals(3, result.getTotalRows());
        assertEquals(3, result.getTotalSuccess());
        assertEquals(0, result.getTotalFailed());
        assertEquals(2, result.getSheets().size());
    }

    @Test
    void shouldHandleEmptySheetGracefully() throws IOException {
        byte[] emptyExcel;
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("原厂授权");
            Row headerRow = wb.getSheetAt(0).createRow(0);
            String[] headers = {
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "原厂授权附件文件名", "授权开始时间", "授权结束时间",
                    "备注", "补充材料附件文件名"
            };
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            var out = new ByteArrayOutputStream();
            wb.write(out);
            emptyExcel = out.toByteArray();
        }

        BrandAuthImportService.ImportResult result =
                importService.importExcel(emptyExcel, 1L);

        assertNotNull(result);
        assertEquals(0, result.getTotalRows());
        assertEquals(0, result.getTotalSuccess());
        assertEquals(0, result.getTotalFailed());
    }

    @Test
    void shouldReportErrorsForInvalidRows() throws IOException {
        byte[] invalidExcel;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("原厂授权");
            String[] headers = {
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "原厂授权附件文件名", "授权开始时间", "授权结束时间",
                    "备注", "补充材料附件文件名"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            // Row 1: empty - should fail validation
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(""); // empty product line

            // Row 2: invalid date
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("工具");
            row2.createCell(1).setCellValue("B001");
            row2.createCell(2).setCellValue("测试品牌");
            row2.createCell(3).setCellValue("国产");
            row2.createCell(4).setCellValue("测试原厂");
            row2.createCell(5).setCellValue("");
            row2.createCell(6).setCellValue("not-a-date"); // invalid date
            row2.createCell(7).setCellValue("2026-12-31");
            row2.createCell(8).setCellValue("");
            row2.createCell(9).setCellValue("");

            var out = new ByteArrayOutputStream();
            wb.write(out);
            invalidExcel = out.toByteArray();
        }

        BrandAuthImportService.ImportResult result =
                importService.importExcel(invalidExcel, 1L);

        assertNotNull(result);
        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getTotalSuccess());
        assertEquals(2, result.getTotalFailed());

        List<String> errors = result.getSheets().get(0).getErrors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("一级产线") || e.contains("不能为空")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("日期格式错误")));
    }

    @Test
    void shouldRejectInvalidExcelData() throws IOException {
        byte[] badExcel;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("原厂授权");
            String[] headers = {
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "原厂授权附件文件名", "授权开始时间", "授权结束时间",
                    "备注", "补充材料附件文件名"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            // Row with invalid product line
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("不存在产线");
            row.createCell(1).setCellValue("B001");
            row.createCell(2).setCellValue("测试");
            row.createCell(3).setCellValue("国产");
            row.createCell(4).setCellValue("原厂");
            row.createCell(5).setCellValue("");
            row.createCell(6).setCellValue("2026-01-01");
            row.createCell(7).setCellValue("2026-12-31");
            row.createCell(8).setCellValue("");
            row.createCell(9).setCellValue("");

            var out = new ByteArrayOutputStream();
            wb.write(out);
            badExcel = out.toByteArray();
        }

        BrandAuthImportService.ImportResult result =
                importService.importExcel(badExcel, 1L);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getTotalSuccess());
        assertEquals(1, result.getTotalFailed());
        assertTrue(result.getSheets().get(0).getErrors().get(0).contains("无效的一级产线"));
    }

    // --- Helper: create test Excel for single sheet ---
    private byte[] createTestExcel(String sheetName, boolean isAgent) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            String[] headers = isAgent ? new String[]{
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "授权1附件文件名", "授权1开始时间", "授权1结束时间",
                    "授权1备注", "代理商名称",
                    "授权2附件文件名", "授权2开始时间", "授权2结束时间",
                    "授权2备注"
            } : new String[]{
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "原厂授权附件文件名", "授权开始时间", "授权结束时间",
                    "备注", "补充材料附件文件名"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Row 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("工具");
            row1.createCell(1).setCellValue("B001");
            row1.createCell(2).setCellValue("测试品牌A");
            row1.createCell(3).setCellValue("国产");
            row1.createCell(4).setCellValue("测试原厂A");
            if (isAgent) {
                row1.createCell(5).setCellValue("附件1.pdf");
                row1.createCell(6).setCellValue("2026-01-01");
                row1.createCell(7).setCellValue("2026-12-31");
                row1.createCell(8).setCellValue("原厂授权");
                row1.createCell(9).setCellValue("测试代理A");
                row1.createCell(10).setCellValue("附件2.pdf");
                row1.createCell(11).setCellValue("2026-06-01");
                row1.createCell(12).setCellValue("2026-12-31");
                row1.createCell(13).setCellValue("转授权");
            } else {
                row1.createCell(5).setCellValue("授权书.pdf");
                row1.createCell(6).setCellValue("2026-01-01");
                row1.createCell(7).setCellValue("2026-12-31");
                row1.createCell(8).setCellValue("测试备注");
                row1.createCell(9).setCellValue("补充材料.pdf");
            }

            // Row 2
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("轴承");
            row2.createCell(1).setCellValue("B002");
            row2.createCell(2).setCellValue("测试品牌B");
            row2.createCell(3).setCellValue("进口");
            row2.createCell(4).setCellValue("测试原厂B");
            if (isAgent) {
                row2.createCell(5).setCellValue("auth1.pdf");
                row2.createCell(6).setCellValue("2026-03-01");
                row2.createCell(7).setCellValue("2026-11-30");
                row2.createCell(8).setCellValue("一级授权");
                row2.createCell(9).setCellValue("测试代理B");
                row2.createCell(10).setCellValue("auth2.pdf");
                row2.createCell(11).setCellValue("2026-06-01");
                row2.createCell(12).setCellValue("2026-11-30");
                row2.createCell(13).setCellValue("二级授权");
            } else {
                row2.createCell(5).setCellValue("doc.pdf");
                row2.createCell(6).setCellValue("2026-02-01");
                row2.createCell(7).setCellValue("2027-01-31");
                row2.createCell(8).setCellValue("");
                row2.createCell(9).setCellValue("");
            }

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createMixedTestExcel() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: 原厂授权
            Sheet mfgSheet = wb.createSheet("原厂授权");
            String[] mfgHeaders = {
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "原厂授权附件文件名", "授权开始时间", "授权结束时间",
                    "备注", "补充材料附件文件名"
            };
            Row mfgHeader = mfgSheet.createRow(0);
            for (int i = 0; i < mfgHeaders.length; i++) {
                mfgHeader.createCell(i).setCellValue(mfgHeaders[i]);
            }
            Row mfgRow = mfgSheet.createRow(1);
            mfgRow.createCell(0).setCellValue("工具");
            mfgRow.createCell(1).setCellValue("B001");
            mfgRow.createCell(2).setCellValue("品牌A");
            mfgRow.createCell(3).setCellValue("国产");
            mfgRow.createCell(4).setCellValue("原厂A");
            mfgRow.createCell(5).setCellValue("");
            mfgRow.createCell(6).setCellValue("2026-01-01");
            mfgRow.createCell(7).setCellValue("2026-12-31");
            mfgRow.createCell(8).setCellValue("");
            mfgRow.createCell(9).setCellValue("");

            // Sheet 2: 代理商授权
            Sheet agentSheet = wb.createSheet("代理商授权");
            String[] agentHeaders = {
                    "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
                    "授权1附件文件名", "授权1开始时间", "授权1结束时间",
                    "授权1备注", "代理商名称",
                    "授权2附件文件名", "授权2开始时间", "授权2结束时间",
                    "授权2备注"
            };
            Row agentHeader = agentSheet.createRow(0);
            for (int i = 0; i < agentHeaders.length; i++) {
                agentHeader.createCell(i).setCellValue(agentHeaders[i]);
            }
            // Agent rows need 4 date columns
            // Agent row 1
            Row agRow1 = agentSheet.createRow(1);
            agRow1.createCell(0).setCellValue("轴承");
            agRow1.createCell(1).setCellValue("B002");
            agRow1.createCell(2).setCellValue("品牌B");
            agRow1.createCell(3).setCellValue("进口");
            agRow1.createCell(4).setCellValue("原厂B");
            agRow1.createCell(5).setCellValue("");
            agRow1.createCell(6).setCellValue("2026-01-01");
            agRow1.createCell(7).setCellValue("2026-12-31");
            agRow1.createCell(8).setCellValue("一级");
            agRow1.createCell(9).setCellValue("代理B");
            agRow1.createCell(10).setCellValue("");
            agRow1.createCell(11).setCellValue("2026-06-01");
            agRow1.createCell(12).setCellValue("2026-12-31");
            agRow1.createCell(13).setCellValue("二级");

            Row agRow2 = agentSheet.createRow(2);
            agRow2.createCell(0).setCellValue("轴承");
            agRow2.createCell(1).setCellValue("B003");
            agRow2.createCell(2).setCellValue("品牌C");
            agRow2.createCell(3).setCellValue("国产");
            agRow2.createCell(4).setCellValue("原厂C");
            agRow2.createCell(5).setCellValue("");
            agRow2.createCell(6).setCellValue("2026-02-01");
            agRow2.createCell(7).setCellValue("2026-11-30");
            agRow2.createCell(8).setCellValue("");
            agRow2.createCell(9).setCellValue("代理C");
            agRow2.createCell(10).setCellValue("");
            agRow2.createCell(11).setCellValue("2026-05-01");
            agRow2.createCell(12).setCellValue("2026-11-30");
            agRow2.createCell(13).setCellValue("");

            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
