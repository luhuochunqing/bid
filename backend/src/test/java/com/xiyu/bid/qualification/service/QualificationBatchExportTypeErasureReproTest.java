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
 * CO-471 类型擦除复现测试。
 *
 * 模拟 Spring MVC @RequestBody Map<String, List<Long>> 因 Jackson 类型擦除
 * 实际产生 List<Integer> 的真实场景，验证 Service 层修复后能否正确导出数据。
 *
 * 修复前：Integer.contains(Long) 返回 false，Excel 只有表头
 * 修复后：Service 层统一转为 Set<Long>，Excel 有数据行
 */
class QualificationBatchExportTypeErasureReproTest {

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
    @DisplayName("复现 Spring MVC 类型擦除：传入 List<Integer> 时修复后 Excel 应有数据行")
    void batchExportExcel_WithIntegerList_ShouldProduceDataRows_AfterFix() throws IOException {
        // 准备测试数据
        List<QualificationDTO> allData = List.of(
                buildDTO(1L, "ISO9001质量管理体系认证", "FIRST", "中国计量认证中心", "CERT-001"),
                buildDTO(2L, "ISO14001环境管理体系认证", "FIRST", "中国计量认证中心", "CERT-002"),
                buildDTO(3L, "OHSAS18001职业健康安全管理体系认证", "FIRST", "中国计量认证中心", "CERT-003")
        );
        when(mockFlatQuery.listAll(null, null)).thenReturn(allData);

        // 模拟 Spring MVC @RequestBody Map<String, List<Long>> 实际反序列化结果：
        // Jackson 类型擦除导致 JSON 小数字被解析为 Integer 而非 Long
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Long> idsFromSpringMvc = (List) List.of(1, 3); // 实际元素是 Integer

        // 验证元素确实是 Integer（复现根因）—— 用 raw type 访问避免 cast 到 Long
        Object firstElement = ((List) idsFromSpringMvc).get(0);
        assertEquals(Integer.class, firstElement.getClass(),
                "复现前提：Spring MVC 反序列化产生的是 Integer，不是 Long");

        // 执行：修复后的 batchExportExcel 应该正确处理 Integer 元素
        byte[] excelBytes = exportService.batchExportExcel(idsFromSpringMvc);

        // 验证 Excel 内容
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            System.out.println("=== 类型擦除复现测试结果 ===");
            System.out.println("传入 ids 元素类型: " + firstElement.getClass().getName());
            System.out.println("Excel lastRowNum: " + lastRowNum);
            for (int i = 0; i <= lastRowNum; i++) {
                var row = sheet.getRow(i);
                if (row != null) {
                    System.out.println("Row " + i + ": " + row.getCell(0).getStringCellValue());
                }
            }

            // 修复前：lastRowNum=0（只有表头）
            // 修复后：lastRowNum=2（1 行表头 + 2 行数据）
            assertEquals(2, lastRowNum,
                    "修复后应该有 2 行数据（ID 1 和 3），但实际 lastRowNum=" + lastRowNum);

            // 验证数据行内容
            assertEquals("ISO9001质量管理体系认证", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("OHSAS18001职业健康安全管理体系认证", sheet.getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("对照：传入正确 List<Long> 时 Excel 应有数据行（保证修复不破坏正常路径）")
    void batchExportExcel_WithCorrectLongList_ShouldStillWork() throws IOException {
        List<QualificationDTO> allData = List.of(
                buildDTO(1L, "证书A", "FIRST", "机构A", "CERT-001"),
                buildDTO(2L, "证书B", "FIRST", "机构B", "CERT-002")
        );
        when(mockFlatQuery.listAll(null, null)).thenReturn(allData);

        // 传入真正的 List<Long>
        List<Long> ids = List.of(1L, 2L);
        byte[] excelBytes = exportService.batchExportExcel(ids);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals(2, sheet.getLastRowNum(), "应该有 2 行数据");
            assertEquals("证书A", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("证书B", sheet.getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("复现：未修复的过滤逻辑（Integer.contains(Long)）会丢失所有数据")
    void demonstrateBug_UnfixedFilteringLosesAllData() {
        // 这个测试用未修复的逻辑直接演示 bug，作为对照证据
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Long> idsFromSpringMvc = (List) List.of(1, 3); // Integer 元素

        // 模拟修复前的过滤逻辑：直接用 ids.contains(q.getId())
        Long dataId1 = 1L;
        Long dataId2 = 2L;
        Long dataId3 = 3L;

        System.out.println("=== 未修复逻辑演示 ===");
        // 用 raw type 访问避免 cast 到 Long
        System.out.println("ids 元素类型: " + ((List) idsFromSpringMvc).get(0).getClass().getName());
        System.out.println("ids.contains(1L) = " + idsFromSpringMvc.contains(dataId1));
        System.out.println("ids.contains(2L) = " + idsFromSpringMvc.contains(dataId2));
        System.out.println("ids.contains(3L) = " + idsFromSpringMvc.contains(dataId3));

        // 修复前的行为：全部返回 false
        assertFalse(idsFromSpringMvc.contains(dataId1),
                "修复前：Integer(1).equals(Long(1L)) = false，数据被过滤掉");
        assertFalse(idsFromSpringMvc.contains(dataId3),
                "修复前：Integer(3).equals(Long(3L)) = false，数据被过滤掉");

        // 模拟修复后的过滤逻辑：用 raw type + Number.longValue()（与 Service 层 toLongSet 一致）
        java.util.Set<Long> idSet = new java.util.HashSet<>();
        for (Object id : (java.util.List) idsFromSpringMvc) {
            idSet.add(((Number) id).longValue());
        }

        System.out.println("=== 修复后逻辑演示 ===");
        System.out.println("idSet: " + idSet);
        System.out.println("idSet.contains(1L) = " + idSet.contains(dataId1));
        System.out.println("idSet.contains(3L) = " + idSet.contains(dataId3));

        assertTrue(idSet.contains(dataId1), "修复后：idSet.contains(1L) = true");
        assertTrue(idSet.contains(dataId3), "修复后：idSet.contains(3L) = true");
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
