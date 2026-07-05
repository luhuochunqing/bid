package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AttachmentType;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BrandAuthZipExporter 单元测试。
 *
 * <p>核心覆盖点：ZIP 包含 _台账.xlsx + 附件 + _导出报告.txt，
 * 附件类型筛选正确，附件读取失败时不崩溃。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandAuthZipExporterTest {

    @Mock
    private ListManufacturerAuthAppService listService;
    @Mock
    private BrandAuthAttachmentJpaRepository attachmentRepository;
    @Mock
    private AttachmentUploadAppService attachmentStorageService;
    @Mock
    private BrandAuthExportService excelExporter;

    @InjectMocks
    private BrandAuthZipExporter zipExporter;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ZIP 包含 _台账.xlsx 和 _导出报告.txt")
    void exportZip_containsExcelAndReport() throws IOException {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withId(1L);
        when(listService.listAllForExport(any())).thenReturn(List.of(auth));
        when(excelExporter.exportByFilter(any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "MANUFACTURER");

        byte[] zipData = zipExporter.exportZip(filter, null);

        var entries = listZipEntries(zipData);
        assertTrue(entries.contains("_台账.xlsx"));
        assertTrue(entries.contains("_导出报告.txt"));
    }

    @Test
    @DisplayName("ZIP 包含附件文件，按品牌名分文件夹")
    void exportZip_containsAttachmentFiles() throws IOException {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withId(1L);
        Path attFile = tempDir.resolve("auth.pdf");
        Files.writeString(attFile, "pdf content");

        var attEntity = BrandAuthAttachmentEntity.builder()
                .id(1L)
                .authorizationId(1L)
                .attachmentType(AttachmentType.AUTH_DOC)
                .fileName("授权书.pdf")
                .fileUrl(attFile.toString())
                .fileSize(11L)
                .fileType("application/pdf")
                .build();

        when(listService.listAllForExport(any())).thenReturn(List.of(auth));
        when(excelExporter.exportByFilter(any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of(attEntity));
        when(attachmentStorageService.readAttachmentFile(attFile.toString()))
                .thenReturn("pdf content".getBytes());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "MANUFACTURER");

        byte[] zipData = zipExporter.exportZip(filter, null);

        var entries = listZipEntries(zipData);
        assertTrue(entries.stream().anyMatch(e -> e.contains("品牌A_1")
                && e.contains("授权书.pdf")));
    }

    @Test
    @DisplayName("attachmentTypes 筛选只打包选中类型的附件")
    void exportZip_attachmentTypeFilter() throws IOException {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withId(1L);
        Path authFile = tempDir.resolve("auth.pdf");
        Files.writeString(authFile, "auth content");
        Path suppFile = tempDir.resolve("supp.pdf");
        Files.writeString(suppFile, "supp content");

        var authAtt = BrandAuthAttachmentEntity.builder()
                .id(1L).authorizationId(1L)
                .attachmentType(AttachmentType.AUTH_DOC)
                .fileName("授权书.pdf").fileUrl(authFile.toString())
                .fileSize(11L).fileType("application/pdf").build();
        var suppAtt = BrandAuthAttachmentEntity.builder()
                .id(2L).authorizationId(1L)
                .attachmentType(AttachmentType.SUPPLEMENTARY)
                .fileName("补充材料.pdf").fileUrl(suppFile.toString())
                .fileSize(11L).fileType("application/pdf").build();

        when(listService.listAllForExport(any())).thenReturn(List.of(auth));
        when(excelExporter.exportByFilter(any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentRepository.findByAuthorizationIdIn(any()))
                .thenReturn(List.of(authAtt, suppAtt));
        when(attachmentStorageService.readAttachmentFile(authFile.toString()))
                .thenReturn("auth content".getBytes());

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "MANUFACTURER");

        // 只导出 AUTH_DOC 类型
        byte[] zipData = zipExporter.exportZip(filter, List.of("AUTH_DOC"));

        var entries = listZipEntries(zipData);
        assertTrue(entries.stream().anyMatch(e -> e.contains("授权书.pdf")));
        assertFalse(entries.stream().anyMatch(e -> e.contains("补充材料.pdf")),
                "SUPPLEMENTARY 类型不应出现在 ZIP 中");
    }

    @Test
    @DisplayName("附件文件不存在时写入错误信息而不崩溃")
    void exportZip_attachmentNotFound_writesErrorAndContinues() throws IOException {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌A", "国产", "原厂A",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withId(1L);
        var attEntity = BrandAuthAttachmentEntity.builder()
                .id(1L).authorizationId(1L)
                .attachmentType(AttachmentType.AUTH_DOC)
                .fileName("missing.pdf").fileUrl("/nonexistent/missing.pdf")
                .fileSize(0L).fileType("application/pdf").build();

        when(listService.listAllForExport(any())).thenReturn(List.of(auth));
        when(excelExporter.exportByFilter(any())).thenReturn(new byte[]{1, 2, 3});
        when(attachmentRepository.findByAuthorizationIdIn(any())).thenReturn(List.of(attEntity));
        when(attachmentStorageService.readAttachmentFile("/nonexistent/missing.pdf"))
                .thenThrow(new IOException("附件文件不存在: /nonexistent/missing.pdf"));

        var filter = new ListManufacturerAuthAppService.ListFilter(
                null, null, null, null, null,
                null, null, null, null,
                null, null, "MANUFACTURER");

        byte[] zipData = zipExporter.exportZip(filter, null);

        var entries = listZipEntries(zipData);
        assertTrue(entries.contains("_导出报告.txt"));
        assertTrue(entries.stream().anyMatch(e -> e.contains("missing.pdf")));
    }

    private static List<String> listZipEntries(byte[] zipData) throws IOException {
        var entries = new java.util.ArrayList<String>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
        return entries;
    }
}
