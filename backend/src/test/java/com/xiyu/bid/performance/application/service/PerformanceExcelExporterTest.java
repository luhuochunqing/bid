package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业绩 Excel 导出器单元测试（CO-445）
 * 覆盖三种导出路径：按 ids、按 criteria、全量
 */
class PerformanceExcelExporterTest {

    private PerformanceRepository repository;
    private PerformanceMapper mapper;
    private PerformanceAlertConfigRepository alertConfigRepository;
    private PerformanceExcelExporter exporter;

    private PerformanceRecord sampleRecord() {
        return new PerformanceRecord(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A",
                null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                List.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @BeforeEach
    void setUp() {
        repository = mock(PerformanceRepository.class);
        mapper = mock(PerformanceMapper.class);
        alertConfigRepository = mock(PerformanceAlertConfigRepository.class);
        exporter = new PerformanceExcelExporter(repository, mapper, alertConfigRepository);
    }

    @Test
    void export_byIds_returnsExcelWithRecords() throws Exception {
        PerformanceRecord record = sampleRecord();
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(new PerformanceDTO(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                0, "", null,
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                List.of(), null, null
        ));

        byte[] data = exporter.export(List.of(1L), null);

        assertThat(data).isNotEmpty();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            assertThat(sheet).isNotNull();
            // 表头 + 1 数据行
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("合同名称");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("合同A");
        }
        verify(repository, times(1)).findById(1L);
        verify(repository, never()).findAll(any(), any());
    }

    @Test
    void export_byCriteria_usesCriteriaInsteadOfAll() throws Exception {
        PerformanceRecord record = sampleRecord();
        var criteria = new PerformanceSearchCriteria(
                "合同A", null, null, null, null,
                null, null, null, null, null, null, null);
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(new PerformanceDTO(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                0, "", null,
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                List.of(), null, null
        ));

        byte[] data = exporter.export(null, criteria);

        assertThat(data).isNotEmpty();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
        }
        verify(repository, times(1)).findAll(eq(criteria), any());
        verify(repository, never()).findById(any());
    }

    @Test
    void export_emptyIdsAndNullCriteria_fallsBackToAll() throws Exception {
        PerformanceRecord record = sampleRecord();
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(new PerformanceDTO(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                0, "", null,
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                List.of(), null, null
        ));

        byte[] data = exporter.export(null, null);

        assertThat(data).isNotEmpty();
        verify(repository, times(1)).findAll(eq(PerformanceSearchCriteria.empty()), any());
    }

    @Test
    void export_emptyResult_returnsOnlyHeaderRow() throws Exception {
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of());

        byte[] data = exporter.export(null, null);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = wb.getSheet("业绩管理台账");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }
}
