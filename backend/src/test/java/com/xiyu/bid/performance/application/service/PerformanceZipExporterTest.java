package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.dto.PerformanceExportCriteria;
import com.xiyu.bid.performance.application.exception.PerformanceExportException;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业绩 ZIP 导出器单元测试（CO-445 + 附件类型筛选 spec 5.1）
 * 覆盖三种导出路径 + ZIP 结构校验 + 类型筛选 + 上限保护 + 报告生成。
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

    private PerformanceRecord recordWithAttachments(List<PerformanceRecord.AttachmentEntry> atts) {
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

    /** 统计 ZIP 内 entry 数量。 */
    private int countEntries(byte[] data) throws IOException {
        int count = 0;
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zis.getNextEntry() != null) {
                count++;
            }
        }
        return count;
    }

    /** 读取 ZIP 内指定 entry 的文本内容。 */
    private String readEntryText(byte[] data, String entryName) throws IOException {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(entryName)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ZIP 内未找到 entry: " + entryName);
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

    // ── 现有 7 个测试（更新签名 + entry 计数） ──────────────────────

    @Test
    void exportZip_byIds_containsExcelEntryAndUsesIds() throws Exception {
        PerformanceRecord record = sampleRecord(false);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

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

        byte[] data = zipExporter.exportZip(null, criteria, PerformanceExportCriteria.allTypes());

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

        byte[] data = zipExporter.exportZip(null, null, PerformanceExportCriteria.allTypes());

        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("_台账.xlsx");
        }
        verify(repository, times(1)).findAll(eq(PerformanceSearchCriteria.empty()), any());
    }

    @Test
    void exportZip_emptyRecords_containsExcelAndReportEntries() throws Exception {
        var config = new PerformanceAlertConfig(null, 180, 90, true);
        when(alertConfigRepository.findActive()).thenReturn(Optional.of(config));
        when(repository.findAll(eq(PerformanceSearchCriteria.empty()), any())).thenReturn(List.of());
        when(excelExporter.export(any(), any())).thenReturn(new byte[]{1, 2, 3});

        byte[] data = zipExporter.exportZip(null, null, PerformanceExportCriteria.allTypes());

        // _台账.xlsx + _导出报告.txt = 2 entries
        assertThat(countEntries(data)).isEqualTo(2);
    }

    @Test
    void exportZip_recordWithNullFileUrlAttachment_skipsAttachmentEntry() throws Exception {
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

        byte[] data = zipExporter.exportZip(null, null, PerformanceExportCriteria.allTypes());

        // _台账.xlsx + _导出报告.txt = 2（空附件被跳过）
        assertThat(countEntries(data)).isEqualTo(2);
    }

    @Test
    void exportZip_recordWithLocalAttachment_readsFromStorageService() throws Exception {
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenReturn(new byte[]{4, 5, 6});

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

        // _台账.xlsx + 合同A_1/合同协议.pdf + _导出报告.txt = 3
        assertThat(countEntries(data)).isEqualTo(3);
    }

    @Test
    void exportZip_attachmentReadFailure_writesErrorMessageInZip() throws Exception {
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenThrow(new IOException("附件文件不存在"));

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

        // _台账.xlsx + 失败附件 entry + _导出报告.txt = 3
        assertThat(countEntries(data)).isEqualTo(3);
    }

    // ── 新增 5 个测试（spec 5.1：类型筛选 + 上限保护 + 报告） ──────────

    @Test
    void exportZip_withTypeFilter_onlyPacksMatchingAttachments() throws Exception {
        // 1 条业绩，2 个附件（CONTRACT_AGREEMENT + OTHER），只筛选 CONTRACT_AGREEMENT
        PerformanceRecord record = recordWithAttachments(List.of(
                new PerformanceRecord.AttachmentEntry(1L, "合同协议.pdf", "/1/contract.pdf", "CONTRACT_AGREEMENT"),
                new PerformanceRecord.AttachmentEntry(2L, "其他.pdf", "/1/other.pdf", "OTHER")));
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/contract.pdf"))
                .thenReturn(new byte[]{4, 5, 6});

        byte[] data = zipExporter.exportZip(List.of(1L), null,
                new PerformanceExportCriteria(Set.of("CONTRACT_AGREEMENT")));

        // _台账.xlsx + 合同A_1/合同协议.pdf + _导出报告.txt = 3
        assertThat(countEntries(data)).isEqualTo(3);
        // OTHER 附件不应被读取
        verify(attachmentStorageService, never()).readAttachmentFile("/1/other.pdf");
        verify(attachmentStorageService, times(1)).readAttachmentFile("/1/contract.pdf");
    }

    @Test
    void exportZip_noFilter_packsAllAttachments() throws Exception {
        // 1 条业绩，2 个附件，不筛选（向后兼容 = 全量）
        PerformanceRecord record = recordWithAttachments(List.of(
                new PerformanceRecord.AttachmentEntry(1L, "合同协议.pdf", "/1/contract.pdf", "CONTRACT_AGREEMENT"),
                new PerformanceRecord.AttachmentEntry(2L, "中标通知书.pdf", "/1/bid.pdf", "BID_NOTICE")));
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/contract.pdf")).thenReturn(new byte[]{4});
        when(attachmentStorageService.readAttachmentFile("/1/bid.pdf")).thenReturn(new byte[]{5});

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

        // _台账.xlsx + 2 附件 + _导出报告.txt = 4
        assertThat(countEntries(data)).isEqualTo(4);
        verify(attachmentStorageService, times(1)).readAttachmentFile("/1/contract.pdf");
        verify(attachmentStorageService, times(1)).readAttachmentFile("/1/bid.pdf");
    }

    @Test
    void exportZip_exceedsMaxAttachments_throwsException() throws Exception {
        // 1 条业绩，501 个附件（CONTRACT_AGREEMENT），超过 MAX_TOTAL_ATTACHMENTS=500
        List<PerformanceRecord.AttachmentEntry> atts = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            atts.add(new PerformanceRecord.AttachmentEntry(
                    (long) i, "file_" + i + ".pdf", "/1/file_" + i + ".pdf", "CONTRACT_AGREEMENT"));
        }
        PerformanceRecord record = recordWithAttachments(atts);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> zipExporter.exportZip(List.of(1L), null,
                PerformanceExportCriteria.allTypes()))
                .isInstanceOf(PerformanceExportException.class)
                .hasMessageContaining("超过上限")
                .hasMessageContaining("500");

        // 超限时不应读取任何附件文件
        verify(attachmentStorageService, never()).readAttachmentFile(any());
    }

    @Test
    void exportZip_attachmentReadFailure_includedInReport() throws Exception {
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenThrow(new IOException("文件不存在"));

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

        String report = readEntryText(data, "_导出报告.txt");
        assertThat(report).contains("失败: 1");
        assertThat(report).contains("失败清单");
        assertThat(report).contains("业绩「合同A」");
        assertThat(report).contains("合同协议");
        assertThat(report).contains("合同协议.pdf");
        assertThat(report).contains("文件不存在");
    }

    @Test
    void exportZip_generatesReportEntry() throws Exception {
        PerformanceRecord record = sampleRecord(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(mapper.toDTO(record)).thenReturn(toDto(record));
        when(excelExporter.export(anyList(), any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentStorageService.readAttachmentFile("/1/PF_1_CONTRACT_AGREEMENT_20260101.pdf"))
                .thenReturn(new byte[]{4, 5, 6});

        byte[] data = zipExporter.exportZip(List.of(1L), null, PerformanceExportCriteria.allTypes());

        String report = readEntryText(data, "_导出报告.txt");
        assertThat(report).startsWith("导出报告");
        assertThat(report).contains("导出业绩数: 1");
        assertThat(report).contains("附件类型筛选: 全部");
        assertThat(report).contains("成功: 1");
        assertThat(report).contains("失败: 0");
    }
}
