package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.CaseExportZipEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaseZipPackager 单元测试。
 *
 * <p>无 Spring 上下文测试：直接实例化 @Component，基于 ZipOutputStream 生成 ZIP byte[]。
 */
class CaseZipPackagerTest {

    private CaseZipPackager packager;

    @BeforeEach
    void setUp() {
        packager = new CaseZipPackager();
    }

    @Test
    @DisplayName("buildCaseZipBytes: 生成有效 ZIP 并包含索引 Excel 和条目")
    void buildZipBytes_WithIndexExcel_ContainsExpectedEntries() throws Exception {
        List<CaseExportZipEntry> entries = List.of(
                new CaseExportZipEntry("项目A/评分项A/应答全文.txt",
                        "应答全文内容".getBytes(), "应答全文内容".length()),
                new CaseExportZipEntry("项目A/评分项A/案例索引信息.txt",
                        "索引信息".getBytes(), "索引信息".length())
        );
        byte[] indexExcelBytes = "fake-excel-content".getBytes();

        byte[] zipBytes = packager.buildCaseZipBytes(entries, indexExcelBytes);

        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        List<String> entryNames = readZipEntryNames(zipBytes);
        assertTrue(entryNames.contains("_案例索引.xlsx"));
        assertTrue(entryNames.contains("项目A/评分项A/应答全文.txt"));
        assertTrue(entryNames.contains("项目A/评分项A/案例索引信息.txt"));
    }

    @Test
    @DisplayName("buildCaseZipBytes: 无索引 Excel 时仅包含条目")
    void buildZipBytes_WithoutIndexExcel_OnlyContainsEntries() throws Exception {
        List<CaseExportZipEntry> entries = List.of(
                new CaseExportZipEntry("项目A/评分项A/应答全文.txt",
                        "content".getBytes(), 7)
        );

        byte[] zipBytes = packager.buildCaseZipBytes(entries, null);

        List<String> entryNames = readZipEntryNames(zipBytes);
        assertEquals(1, entryNames.size());
        assertEquals("项目A/评分项A/应答全文.txt", entryNames.get(0));
    }

    @Test
    @DisplayName("buildCaseZipBytes: 空索引 Excel 时不添加")
    void buildZipBytes_EmptyIndexExcel_Skipped() throws Exception {
        List<CaseExportZipEntry> entries = List.of(
                new CaseExportZipEntry("test.txt", new byte[]{65}, 1)
        );

        byte[] zipBytes = packager.buildCaseZipBytes(entries, new byte[0]);

        List<String> entryNames = readZipEntryNames(zipBytes);
        assertFalse(entryNames.contains("_案例索引.xlsx"));
    }

    @Test
    @DisplayName("buildCaseZipBytes: 空条目列表仍生成有效 ZIP")
    void buildZipBytes_EmptyEntries_ValidZip() throws Exception {
        byte[] zipBytes = packager.buildCaseZipBytes(List.of(), null);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
        assertTrue(readZipEntryNames(zipBytes).isEmpty());
    }

    @Test
    @DisplayName("buildCaseIndexExcel: 生成有效索引 Excel（PK 签名）")
    void buildIndexExcel_ProducesValidExcel() {
        List<CaseZipPackager.CaseIndexRow> rows = List.of(
                new CaseZipPackager.CaseIndexRow(
                        1L, "项目A", "评分项A", "技术", "国有企业",
                        "综合", "WON", "产品线A", 3, "ACTIVE", "2026-06-09")
        );

        byte[] excel = packager.buildCaseIndexExcel(rows);
        assertNotNull(excel);
        assertTrue(excel.length > 0);
        assertEquals((byte) 'P', excel[0]);
        assertEquals((byte) 'K', excel[1]);
    }

    @Test
    @DisplayName("buildCaseIndexExcel: 空行列表生成仅有表头的 Excel")
    void buildIndexExcel_EmptyRows_ProducesValidExcel() {
        byte[] excel = packager.buildCaseIndexExcel(List.of());
        assertNotNull(excel);
        assertTrue(excel.length > 0);
    }

    @Test
    @DisplayName("buildCaseIndexExcel: 多条索引行写入")
    void buildIndexExcel_MultipleRows_WritesAll() {
        List<CaseZipPackager.CaseIndexRow> rows = List.of(
                new CaseZipPackager.CaseIndexRow(
                        1L, "项目A", "评分项A", "技术", "国有企业",
                        "综合", "WON", "产品线A", 3, "ACTIVE", "2026-06-09"),
                new CaseZipPackager.CaseIndexRow(
                        2L, "项目B", "评分项B", "商务", "民营企业",
                        "工程", "LOST", "产品线B", 1, "ACTIVE", "2026-06-10")
        );

        byte[] excel = packager.buildCaseIndexExcel(rows);
        assertNotNull(excel);
        assertTrue(excel.length > 0);
    }

    // ---------------------------------------------------------------
    // 辅助方法
    // ---------------------------------------------------------------

    private List<String> readZipEntryNames(byte[] zipBytes) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }
}
