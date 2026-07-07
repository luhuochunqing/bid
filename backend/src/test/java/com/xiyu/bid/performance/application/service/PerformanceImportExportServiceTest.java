package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业绩批量导入导出编排服务测试
 */
class PerformanceImportExportServiceTest {

    @Test
    void Excel声明附件缺失时返回失败且不保存记录() throws IOException {
        var rowImporter = mock(PerformanceRowImporter.class);
        var attachmentProcessor = mock(PerformanceImportAttachmentProcessor.class);
        var service = new PerformanceImportExportService(
                null, null, null, rowImporter, attachmentProcessor);

        MultipartFile file = createExcelWithContractAgreement("合同A", "合同协议.pdf");
        var parsed = new PerformanceRowImporter.ParsedRow(
                2, "合同A", mock(PerformanceUpsertCommand.class),
                List.of(new PerformanceRowImporter.AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT")));
        when(rowImporter.parseRow(any(), anyInt())).thenReturn(parsed);
        when(attachmentProcessor.findMissingDeclaredAttachments(List.of(parsed), List.of()))
                .thenReturn(List.of(new PerformanceImportAttachmentProcessor.MissingAttachment(
                        2, "合同A", "合同协议.pdf", "CONTRACT_AGREEMENT")));

        var result = service.batchImport(file, List.of());

        assertThat(result.successCount).isZero();
        assertThat(result.failureCount).isEqualTo(1);
        assertThat(result.failures.get(0).rowNum()).isEqualTo(2);
        assertThat(result.failures.get(0).contractName()).isEqualTo("合同A");
        assertThat(result.failures.get(0).reason()).contains("附件未上传: 合同协议.pdf");
        verify(rowImporter, never()).saveParsedRow(any());
    }

    @Test
    void Excel声明附件齐全时保存记录并归档附件() throws IOException {
        var rowImporter = mock(PerformanceRowImporter.class);
        var attachmentProcessor = mock(PerformanceImportAttachmentProcessor.class);
        var service = new PerformanceImportExportService(
                null, null, null, rowImporter, attachmentProcessor);

        MultipartFile file = createExcelWithContractAgreement("合同A", "合同协议.pdf");
        var command = mock(PerformanceUpsertCommand.class);
        var parsed = new PerformanceRowImporter.ParsedRow(
                2, "合同A", command,
                List.of(new PerformanceRowImporter.AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT")));
        var saved = new PerformanceRowImporter.ImportRowResult(
                "合同A", 1L,
                List.of(new PerformanceRowImporter.AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT")));
        var attachmentInput = new PerformanceImportAttachmentProcessor.AttachmentInput(
                "合同协议.pdf", new byte[]{1});
        when(rowImporter.parseRow(any(), anyInt())).thenReturn(parsed);
        when(attachmentProcessor.findMissingDeclaredAttachments(List.of(parsed), List.of(attachmentInput)))
                .thenReturn(List.of());
        when(rowImporter.saveParsedRow(parsed)).thenReturn(saved);
        when(attachmentProcessor.attachFiles(List.of(saved), List.of(attachmentInput)))
                .thenReturn(new PerformanceImportAttachmentProcessor.AttachmentResult(
                        1, List.of()));

        var result = service.batchImport(file, List.of(attachmentInput));

        assertThat(result.successCount).isEqualTo(1);
        assertThat(result.failureCount).isZero();
        assertThat(result.attachedCount).isEqualTo(1);
        verify(rowImporter).saveParsedRow(parsed);
    }

    private MultipartFile createExcelWithContractAgreement(String contractName, String attachmentName)
            throws IOException {
        try (var wb = new XSSFWorkbook(); var baos = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("合同名称");
            header.createCell(19).setCellValue("合同协议附件文件名");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(contractName);
            row.createCell(19).setCellValue(attachmentName);
            wb.write(baos);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}
