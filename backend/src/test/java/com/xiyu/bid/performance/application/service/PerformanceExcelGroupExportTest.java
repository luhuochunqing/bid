package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-583 业绩 Excel 分组导出测试
 * 验证按 groupCompany 分组、拼音排序、汇总行样式等聚合导出行为。
 * 从 PerformanceExcelExporterTest 拆分出来，避免单文件超过 300 行硬上限。
 */
class PerformanceExcelGroupExportTest {

    private PerformanceRepository repository;
    private PerformanceMapper mapper;
    private PerformanceAlertConfigRepository alertConfigRepository;
    private PerformanceExcelExporter exporter;

    @BeforeEach
    void setUp() {
        repository = mock(PerformanceRepository.class);
        mapper = mock(PerformanceMapper.class);
        alertConfigRepository = mock(PerformanceAlertConfigRepository.class);
        exporter = new PerformanceExcelExporter(repository, mapper, alertConfigRepository);
    }

    @Test
    void export_groupsByGroupCompany_withSummaryRowForEachGroup() throws Exception {
        // 场景：2 个集团，集团A 有 2 份合同，集团B 有 1 份合同
        // 期望导出结构：表头 + 集团A汇总行 + 2明细行 + 集团B汇总行 + 1明细行 = 6 行
        var r1 = sampleRecordWithGroup(1L, "集团A", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
        var r2 = sampleRecordWithGroup(2L, "集团A", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 9, 24));
        var r3 = sampleRecordWithGroup(3L, "集团B", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 1));
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any()))
                .thenReturn(List.of(r1, r2, r3));
        when(repository.findGroupTotalExpiryDates()).thenReturn(Map.of(
                "集团A", LocalDate.of(2025, 9, 24),
                "集团B", LocalDate.of(2025, 6, 1)));
        when(mapper.toDTO(eq(r1), any(Map.class))).thenReturn(dtoFor(r1));
        when(mapper.toDTO(eq(r2), any(Map.class))).thenReturn(dtoFor(r2));
        when(mapper.toDTO(eq(r3), any(Map.class))).thenReturn(dtoFor(r3));

        byte[] data = exporter.export(null, null);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            assertThat(sheet).isNotNull();
            // 表头(0) + 集团A汇总(1) + 集团A明细2行(2,3) + 集团B汇总(4) + 集团B明细(5) = 6 行
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(6);
            // 集团A 汇总行：集团名(列2) = "集团A"，总截止日期(列11) = "2025-09-24"
            Row summaryA = sheet.getRow(1);
            assertThat(summaryA.getCell(2).getStringCellValue()).isEqualTo("集团A");
            assertThat(summaryA.getCell(11).getStringCellValue()).isEqualTo("2025-09-24");
            // 集团A 第一个明细行：总截止日期列应为空
            Row detail1 = sheet.getRow(2);
            assertThat(detail1.getCell(11).getStringCellValue()).isEmpty();
            // 集团B 汇总行
            Row summaryB = sheet.getRow(4);
            assertThat(summaryB.getCell(2).getStringCellValue()).isEqualTo("集团B");
            assertThat(summaryB.getCell(11).getStringCellValue()).isEqualTo("2025-06-01");
        }
        verify(repository, times(1)).findGroupTotalExpiryDates();
    }

    @Test
    void export_summaryRowHasBoldFontAndGreenFill() throws Exception {
        var r1 = sampleRecordWithGroup(1L, "集团A", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of(r1));
        when(repository.findGroupTotalExpiryDates())
                .thenReturn(Map.of("集团A", LocalDate.of(2025, 1, 1)));
        when(mapper.toDTO(eq(r1), any(Map.class))).thenReturn(dtoFor(r1));

        byte[] data = exporter.export(null, null);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            Row summaryRow = sheet.getRow(1);
            var cell = summaryRow.getCell(2);
            var style = cell.getCellStyle();
            assertThat(style.getFillForegroundColor()).isNotNull();
            // 字体加粗
            var font = wb.getFontAt(style.getFontIndex());
            assertThat(font.getBold()).isTrue();
        }
    }

    @Test
    void export_groupsOrderedByPinyinOfGroupCompany() throws Exception {
        // 集团名"中核"vs"山东"，拼音排序：山东(S) < 中核(Z)
        var r1 = sampleRecordWithGroup(1L, "中核集团", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        var r2 = sampleRecordWithGroup(2L, "山东能源", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any()))
                .thenReturn(List.of(r1, r2));
        when(repository.findGroupTotalExpiryDates()).thenReturn(Map.of(
                "中核集团", LocalDate.of(2025, 1, 1),
                "山东能源", LocalDate.of(2025, 1, 1)));
        when(mapper.toDTO(eq(r1), any(Map.class))).thenReturn(dtoFor(r1));
        when(mapper.toDTO(eq(r2), any(Map.class))).thenReturn(dtoFor(r2));

        byte[] data = exporter.export(null, null);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            // 第一个汇总行应该是"山东能源"（拼音 S 排在 Z 前面）
            Row firstSummary = sheet.getRow(1);
            assertThat(firstSummary.getCell(2).getStringCellValue()).isEqualTo("山东能源");
            Row secondSummary = sheet.getRow(3);
            assertThat(secondSummary.getCell(2).getStringCellValue()).isEqualTo("中核集团");
        }
    }

    @Test
    void export_recordsWithinGroupOrderedBySigningDateAsc() throws Exception {
        // 同集团内按签约日期升序
        var r1 = sampleRecordWithGroup(1L, "集团A", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 9, 1));
        var r2 = sampleRecordWithGroup(2L, "集团A", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any()))
                .thenReturn(List.of(r1, r2)); // 故意反序输入
        when(repository.findGroupTotalExpiryDates())
                .thenReturn(Map.of("集团A", LocalDate.of(2025, 9, 1)));
        when(mapper.toDTO(eq(r1), any(Map.class))).thenReturn(dtoFor(r1));
        when(mapper.toDTO(eq(r2), any(Map.class))).thenReturn(dtoFor(r2));

        byte[] data = exporter.export(null, null);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            // 汇总行(1) + 明细1(2) + 明细2(3)
            Row detail1 = sheet.getRow(2);
            Row detail2 = sheet.getRow(3);
            // 签约日期升序：r2(2023-01-01) 在前，r1(2024-09-01) 在后
            assertThat(detail1.getCell(9).getStringCellValue()).isEqualTo("2023-01-01");
            assertThat(detail2.getCell(9).getStringCellValue()).isEqualTo("2024-09-01");
        }
    }

    private PerformanceRecord sampleRecordWithGroup(Long id, String group,
                                                     LocalDate signing, LocalDate expiry) {
        return new PerformanceRecord(
                id, "合同" + id, "签约单位" + id, group,
                null, "行业" + id, null, null, null,
                signing, expiry, null,
                "联系人" + id, "13800000000", "属地" + id, "地址" + id, "负责人" + id,
                "http://mall.com", false, "备注" + id,
                List.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private PerformanceDTO dtoFor(PerformanceRecord r) {
        return new PerformanceDTO(
                r.id(), r.contractName(), r.signingEntity(), r.groupCompany(),
                null, r.industry(), null, null, null,
                r.signingDate(), r.expiryDate(), r.totalExpiryDate(),
                null, // groupTotalExpiryDate 由 exporter 注入
                0L, "", null,
                r.contactPerson(), r.contactInfo(), r.territory(),
                r.customerAddress(), r.xiyuProjectManager(),
                r.mallWebsiteUrl(), r.hasBidNotice(), r.remarks(),
                List.of(), r.createdAt(), r.updatedAt()
        );
    }
}
