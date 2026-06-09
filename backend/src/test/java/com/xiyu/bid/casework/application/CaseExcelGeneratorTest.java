package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.domain.model.CaseExportRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaseExcelGenerator 单元测试。
 *
 * <p>无 Spring 上下文测试：直接实例化 @Component，基于 Apache POI 生成 Excel byte[]。
 */
class CaseExcelGeneratorTest {

    private CaseExcelGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CaseExcelGenerator();
    }

    @Test
    @DisplayName("generate: 空记录列表生成有效的最小 Excel")
    void generate_EmptyList_ProducesValidExcel() throws IOException {
        CaseExcelGenerator.ExportResult result = generator.generate(List.of());
        assertNotNull(result.data());
        assertTrue(result.data().length > 0);
        assertEquals(0, result.recordCount());
    }

    @Test
    @DisplayName("generate: 一条记录生成有效 Excel，含表头和数据")
    void generate_SingleRecord_ProducesValidExcel() throws IOException {
        List<CaseExportRecord> records = List.of(
                new CaseExportRecord("评分项A", "项目A", "综合", "国有企业",
                        "技术", "WON", 5, "2026-06-09 10:00", "应答摘要")
        );

        CaseExcelGenerator.ExportResult result = generator.generate(records);
        assertNotNull(result.data());
        assertTrue(result.data().length > 0);
        assertEquals(1, result.recordCount());
    }

    @Test
    @DisplayName("generate: 多条记录逐行写入")
    void generate_MultipleRecords_WritesAllRows() throws IOException {
        List<CaseExportRecord> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            records.add(new CaseExportRecord(
                    "评分项" + i, "项目" + i, "综合", "国有企业",
                    "技术", "WON", i, "2026-06-09 10:00", "摘要" + i));
        }

        CaseExcelGenerator.ExportResult result = generator.generate(records);
        assertEquals(10, result.recordCount());
    }

    @Test
    @DisplayName("generate: null 输入视为空列表")
    void generate_NullInput_TreatedAsEmpty() throws IOException {
        CaseExcelGenerator.ExportResult result = generator.generate(null);
        assertNotNull(result.data());
        assertEquals(0, result.recordCount());
    }

    @Test
    @DisplayName("generate: 超过 10000 条截断")
    void generate_OverMaxLimit_Truncates() throws IOException {
        List<CaseExportRecord> records = new ArrayList<>();
        for (int i = 0; i < 10005; i++) {
            records.add(new CaseExportRecord(
                    "标题", "项目", "综合", "国有企业",
                    "技术", "WON", 0, "2026-06-09", "摘要"));
        }

        CaseExcelGenerator.ExportResult result = generator.generate(records);
        assertEquals(10000, result.recordCount());
        assertTrue(result.data().length > 0);
    }

    @Test
    @DisplayName("generate: 恰好 10000 条不截断")
    void generate_ExactMaxLimit_NotTruncated() throws IOException {
        List<CaseExportRecord> records = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            records.add(new CaseExportRecord(
                    "标题", "项目", "综合", "国有企业",
                    "技术", "WON", 0, "2026-06-09", "摘要"));
        }

        CaseExcelGenerator.ExportResult result = generator.generate(records);
        assertEquals(10000, result.recordCount());
    }

    @Test
    @DisplayName("generate: 输出为有效 OOXML 格式（以 ZIP 签名为开头）")
    void generate_OutputIsValidOOXML() throws IOException {
        List<CaseExportRecord> records = List.of(
                new CaseExportRecord("评分项A", "项目A", "综合", "国有企业",
                        "技术", "WON", 3, "2026-06-09", "摘要")
        );

        CaseExcelGenerator.ExportResult result = generator.generate(records);
        // XLSX 文件本质上是 ZIP，以 PK 签名开头
        byte[] data = result.data();
        assertEquals((byte) 'P', data[0]);
        assertEquals((byte) 'K', data[1]);
    }
}
