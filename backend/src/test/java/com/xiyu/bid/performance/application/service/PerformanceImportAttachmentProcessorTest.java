package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.service.PerformanceRowImporter.AttachmentFileName;
import com.xiyu.bid.performance.application.service.PerformanceRowImporter.ImportRowResult;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceAttachmentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-444 业绩批量导入附件归档器测试
 */
class PerformanceImportAttachmentProcessorTest {

    private PerformanceAttachmentJpaRepository attachmentRepo;
    private PerformanceImportAttachmentProcessor processor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        attachmentRepo = mock(PerformanceAttachmentJpaRepository.class);
        processor = new PerformanceImportAttachmentProcessor(attachmentRepo);
        // 使用临时目录作为附件根目录
        ReflectionTestUtils.setField(processor, "attachmentRoot", tempDir.toString());
    }

    @Test
    void 附件包为空时返回零结果() {
        var rows = List.of(new ImportRowResult("合同A", 1L, List.of()));
        var result = processor.attachFiles(rows, List.of());

        assertThat(result.matchedCount()).isZero();
        assertThat(result.unmatched()).isEmpty();
        verify(attachmentRepo, never()).save(any());
    }

    @Test
    void 附件文件名匹配时归档成功并更新附件记录() {
        // 已有附件记录（fileUrl 为空，等待回填）
        var existingAtt = PerformanceAttachmentEntity.builder()
                .id(10L)
                .performanceId(1L)
                .fileName("合同协议.pdf")
                .fileUrl("")
                .fileType("CONTRACT_AGREEMENT")
                .build();
        when(attachmentRepo.findByPerformanceId(1L)).thenReturn(List.of(existingAtt));

        var rows = List.of(new ImportRowResult("合同A", 1L,
                List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(new PerformanceImportAttachmentProcessor.AttachmentInput(
                "合同协议.pdf", new byte[]{1, 2, 3}));

        var result = processor.attachFiles(rows, attachments);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.unmatched()).isEmpty();
        verify(attachmentRepo).save(any(PerformanceAttachmentEntity.class));
        // 验证 fileUrl 已回填
        assertThat(existingAtt.getFileUrl()).startsWith("/1/PF_1_CONTRACT_AGREEMENT_");
    }

    @Test
    void Excel中未填写该附件文件名时创建新附件记录() {
        // 附件包中存在文件，但 Excel 中未填写该文件名
        when(attachmentRepo.findByPerformanceId(1L)).thenReturn(List.of());

        var rows = List.of(new ImportRowResult("合同A", 1L, List.of()));
        var attachments = List.of(new PerformanceImportAttachmentProcessor.AttachmentInput(
                "未在Excel中填写的附件.pdf", new byte[]{1, 2, 3}));

        var result = processor.attachFiles(rows, attachments);

        // 因为 Excel 中没有该文件名，所以匹配失败
        assertThat(result.matchedCount()).isZero();
        assertThat(result.unmatched()).hasSize(1);
        assertThat(result.unmatched().get(0).filename()).isEqualTo("未在Excel中填写的附件.pdf");
        verify(attachmentRepo, never()).save(any());
    }

    @Test
    void 同名文件名匹配多个业绩时按顺序使用槽位() {
        // 两个业绩都填了相同的附件文件名
        var rows = List.of(
                new ImportRowResult("合同A", 1L,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))),
                new ImportRowResult("合同B", 2L,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT")))
        );
        when(attachmentRepo.findByPerformanceId(1L)).thenReturn(List.of());
        when(attachmentRepo.findByPerformanceId(2L)).thenReturn(List.of());

        var attachments = List.of(
                new PerformanceImportAttachmentProcessor.AttachmentInput(
                        "合同协议.pdf", new byte[]{1}),
                new PerformanceImportAttachmentProcessor.AttachmentInput(
                        "合同协议.pdf", new byte[]{2})
        );

        var result = processor.attachFiles(rows, attachments);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.unmatched()).isEmpty();
        // 两次保存（每个业绩一次）
        verify(attachmentRepo, times(2)).save(any(PerformanceAttachmentEntity.class));
    }

    @Test
    void 文件名大小写不一致时仍能匹配() {
        var existingAtt = PerformanceAttachmentEntity.builder()
                .id(10L)
                .performanceId(1L)
                .fileName("合同协议.PDF")
                .fileUrl("")
                .fileType("CONTRACT_AGREEMENT")
                .build();
        when(attachmentRepo.findByPerformanceId(1L)).thenReturn(List.of(existingAtt));

        // Excel 中填写大写，附件包中是小写
        var rows = List.of(new ImportRowResult("合同A", 1L,
                List.of(new AttachmentFileName("合同协议.PDF", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(new PerformanceImportAttachmentProcessor.AttachmentInput(
                "合同协议.pdf", new byte[]{1}));

        var result = processor.attachFiles(rows, attachments);

        assertThat(result.matchedCount()).isEqualTo(1);
    }

    @Test
    void 附件包第三个文件匹配失败时返回未匹配列表() {
        var rows = List.of(new ImportRowResult("合同A", 1L,
                List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(
                new PerformanceImportAttachmentProcessor.AttachmentInput(
                        "合同协议.pdf", new byte[]{1}),
                new PerformanceImportAttachmentProcessor.AttachmentInput(
                        "不存在.pdf", new byte[]{2})
        );

        var result = processor.attachFiles(rows, attachments);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.unmatched()).hasSize(1);
        assertThat(result.unmatched().get(0).filename()).isEqualTo("不存在.pdf");
        assertThat(result.unmatched().get(0).reason()).contains("未找到");
    }

    @Test
    void Excel声明附件在附件包中缺失时返回缺失列表() {
        var command = mock(PerformanceUpsertCommand.class);
        var rows = List.of(
                new PerformanceRowImporter.ParsedRow(2, "合同A", command,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(
                new PerformanceImportAttachmentProcessor.AttachmentInput("其他文件.pdf", new byte[]{1}));

        var missing = processor.findMissingDeclaredAttachments(rows, attachments);

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).rowNum()).isEqualTo(2);
        assertThat(missing.get(0).contractName()).isEqualTo("合同A");
        assertThat(missing.get(0).fileName()).isEqualTo("合同协议.pdf");
        assertThat(missing.get(0).fileType()).isEqualTo("CONTRACT_AGREEMENT");
    }

    @Test
    void Excel声明附件与附件包匹配时返回空列表() {
        var command = mock(PerformanceUpsertCommand.class);
        var rows = List.of(
                new PerformanceRowImporter.ParsedRow(2, "合同A", command,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(
                new PerformanceImportAttachmentProcessor.AttachmentInput("合同协议.pdf", new byte[]{1}));

        var missing = processor.findMissingDeclaredAttachments(rows, attachments);

        assertThat(missing).isEmpty();
    }

    @Test
    void 同名附件按顺序占用槽位时未用完的声明不视为缺失() {
        var command = mock(PerformanceUpsertCommand.class);
        var rows = List.of(
                new PerformanceRowImporter.ParsedRow(2, "合同A", command,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))),
                new PerformanceRowImporter.ParsedRow(3, "合同B", command,
                        List.of(new AttachmentFileName("合同协议.pdf", "CONTRACT_AGREEMENT"))));
        var attachments = List.of(
                new PerformanceImportAttachmentProcessor.AttachmentInput("合同协议.pdf", new byte[]{1}));

        var missing = processor.findMissingDeclaredAttachments(rows, attachments);

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).contractName()).isEqualTo("合同B");
    }
}
