package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业绩 ZIP 导出器单元测试（CO-445）
 * 覆盖三种导出路径 + ZIP 结构校验
 */
class PerformanceZipExporterTest {

    private PerformanceRepository repository;
    private PerformanceMapper mapper;
    private PerformanceAlertConfigRepository alertConfigRepository;
    private PerformanceExcelExporter excelExporter;
    private PerformanceAttachmentStorageAppService attachmentStorageService;
    private PerformanceZipExporter zipExporter;

    private PerformanceRecord sampleRecord(boolean withAttachment) {
        List<PerformanceRecord.AttachmentEntry> atts = withAttachment
                ? List.of(new PerformanceRecord.AttachmentEntry(
                        1L, "合同协议.pdf", "/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf", "CONTRACT_AGREEMENT"))
                : List.of();
        return new PerformanceRecord(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A",
                null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                atts, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private PerformanceDTO toDto(PerformanceRecord r) {
        var atts = r.attachments().stream()
                .map(a -> new PerformanceDTO.AttachmentDTO(a.id(), a.fileName(), a.fileUrl(), a.fileType()))
                .toList();
        return new PerformanceDTO(
                r.id(), r.contractName(), r.signingEntity(), r.groupCompany(),
                r.customerType(), r.industry(),
                r.projectType(), r.dockingMethod(), r.customerLevel(),
                r.signingDate(), r.expiryDate(), r.totalExpiryDate(),
                0, "", null,
                r.contactPerson(), r.contactInfo(), r.territory(),
                r.customerAddress(), r.xiyuProjectManager(),
                r.mallWebsiteUrl(), r.hasBidNotice(), r.remarks(),
                atts, r.createdAt(), r.updatedAt()
        );
    }

    @BeforeEach
    void setUp() {
        repository = mock(PerformanceRepository.class);
        mapper = mock(PerformanceMapper.class);
        alertConfigRepository = mock(PerformanceAlertConfigRepository.class);
        excelExporter = mock(PerformanceExcelExporter.class);
        attachmentStorageService = mock(PerformanceAttachmentStorageAppService.class);
        zipExporter = new PerformanceZipExporter(repository, mapper, alertConfigRepository, excelExporter, attachmentStorageService);
    }

    @Test
    void exportZip_byIds_containsExcelEntryAndUsesIds() throws Exception {
        PerformanceRecord record = sampleRecord(false);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(List.of(1L), null);

        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("_台账.xlsx");
        }
        verify(repository, times(1)).findById(1L);
        verify(repository, never()).findAll(any(), any());
    }

    @Test
    void exportZip_byCriteria_usesCriteria() throws Exception {
        PerformanceRecord record = sampleRecord(false);
        var criteria = PerformanceSearchCriteria.empty();
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(null, criteria);

        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("_台账.xlsx");
        }
        verify(repository, times(1)).findAll(eq(criteria), any());
        verify(repository, never()).findById(any());
    }

    @Test
    void exportZip_nullIdsAndNullCriteria_fallsBackToAll() throws Exception {
        PerformanceRecord record = sampleRecord(false);
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(null, null);

        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("_台账.xlsx");
        }
        verify(repository, times(1)).findAll(eq(PerformanceSearchCriteria.empty()), any());
    }

    @Test
    void exportZip_emptyRecords_containsOnlyExcelEntry() throws Exception {
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of());
        when(excelExporter.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(null, null);

        int entryCount = 0;
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
        }
        assertThat(entryCount).isEqualTo(1);
    }

    @Test
    void exportZip_recordWithNullFileUrlAttachment_skipsAttachmentEntry() throws Exception {
        // 附件 fileUrl 为 null，应跳过（不会调用 storageService）
        PerformanceRecord record = new PerformanceRecord(
                1L, "合同A", "签约单位A", "集团A",
                null, "行业A",
                null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31),
                "联系人A", "13800000000", "属地A", "地址A", "项目负责人A",
                "http://mall.com", true, "备注A",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "空附件.pdf", null, "CONTRACT_AGREEMENT")),
                LocalDateTime.now(), LocalDateTime.now()
        );
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(null, null);

        // 只应有 _台账.xlsx 一个 entry（空附件被跳过）
        int entryCount = 0;
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
        }
        assertThat(entryCount).isEqualTo(1);
    }

    @Test
    void exportZip_recordWithLocalAttachment_readsFromStorageService() throws Exception {
        // 带本地附件的记录，应通过 storageService.readAttachmentFile 读取
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenReturn(new byte[]{4, 5, 6});

        byte[] data = zipExporter.exportZip(List.of(1L), null);

        // 应有 2 个 entry：_台账.xlsx + 合同A_1/合同协议.pdf
        int entryCount = 0;
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
        }
        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void exportZip_attachmentReadFailure_writesErrorMessageInZip() throws Exception {
        // 附件读取失败，应写入错误信息而非中断整个 ZIP
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenThrow(new IOException("附件文件不存在"));

        byte[] data = zipExporter.exportZip(List.of(1L), null);

        // 仍有 2 个 entry（失败附件也占一个 entry，内容为错误信息）
        int entryCount = 0;
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                entryCount++;
            }
        }
        assertThat(entryCount).isEqualTo(2);
    }
}
