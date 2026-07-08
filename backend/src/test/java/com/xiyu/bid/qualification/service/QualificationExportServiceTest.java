package com.xiyu.bid.qualification.service;

import com.xiyu.bid.qualification.dto.QualificationAttachmentDTO;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QualificationExportService 单元测试。
 * 重点覆盖 Sentry issue 7590982843 的回归场景：
 * 多条资质的附件同名时，ZIP 打包必须去重避免 ZipException: duplicate entry。
 */
class QualificationExportServiceTest {

    @TempDir
    Path tempDir;

    private QualificationFlatQuery flatQuery;
    private QualificationExcelSupport qualificationExcelSupport;
    private QualificationExportService service;

    @BeforeEach
    void setUp() throws Exception {
        flatQuery = mock(QualificationFlatQuery.class);
        qualificationExcelSupport = mock(QualificationExcelSupport.class);
        service = new QualificationExportService(flatQuery, qualificationExcelSupport);
        // storageRoot 用反射设置（测试不依赖本地文件系统路径，fileUrl 走 URL 回退）
        var field = QualificationExportService.class.getDeclaredField("storageRoot");
        field.setAccessible(true);
        field.set(service, tempDir.toString());
    }

    /**
     * 回归测试：两条资质 name 相同、附件 fileName 也相同，
     * ZIP 打包后应自动去重（第二个 entry 加 _1 后缀），不抛 ZipException。
     *
     * Sentry: ZipException: duplicate entry at
     * QualificationExportService.writeAttachmentToZip:157
     */
    @Test
    void shouldDeduplicateEntriesWhenAttachmentNamesCollide() throws Exception {
        // 准备一个临时文件作为附件内容
        Path attachment = tempDir.resolve("license.pdf");
        Files.writeString(attachment, "dummy-content");

        QualificationDTO q1 = QualificationDTO.builder()
                .id(1L)
                .name("营业执照")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("license.pdf")
                                .fileUrl(attachment.toUri().toString())
                                .build()
                ))
                .build();
        QualificationDTO q2 = QualificationDTO.builder()
                .id(2L)
                .name("营业执照")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("license.pdf")
                                .fileUrl(attachment.toUri().toString())
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q1, q2));

        byte[] zipBytes = service.batchExportZip(List.of(1L, 2L));

        // 解压验证 entry 去重
        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        // 期望：第一个 entry 原名，第二个加 _1 后缀
        // buildEntryName 清理后为 "营业执照_license.pdf"
        assertThat(entryNames).hasSize(2);
        assertThat(entryNames).contains("营业执照_license.pdf");
        assertThat(entryNames).anyMatch(n -> n.startsWith("营业执照_license") && n.endsWith(".pdf")
                && !n.equals("营业执照_license.pdf"));
    }

    /**
     * 同一条资质的 attachment 与主 fileUrl 指向同一物理文件时，
     * CO-544 fix: 只应写入 ZIP 一次（跳过与主实体 fileUrl 相同的 attachment）。
     */
    @Test
    void shouldDeduplicateWhenSameQualificationHasSameNamedAttachments() throws Exception {
        Path attachment = tempDir.resolve("cert.docx");
        Files.writeString(attachment, "cert-content");

        QualificationDTO q = QualificationDTO.builder()
                .id(1L)
                .name("资质A")
                .fileUrl(attachment.toUri().toString()) // 主 fileUrl → extractFileName = cert.docx
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("cert.docx")
                                .fileUrl(attachment.toUri().toString())
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q));

        byte[] zipBytes = service.batchExportZip(List.of(1L));

        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        // CO-544 fix: attachment.fileUrl == 主实体 fileUrl，跳过 attachment，只写 1 个 entry
        assertThat(entryNames).hasSize(1);
        assertThat(entryNames).contains("资质A_cert.docx");
    }

    /**
     * 文件名含非法字符（如路径分隔符）时，safeFileName 应清理。
     */
    @Test
    void shouldSanitizeIllegalFileNameChars() throws Exception {
        Path attachment = tempDir.resolve("normal.pdf");
        Files.writeString(attachment, "content");

        QualificationDTO q = QualificationDTO.builder()
                .id(1L)
                .name("资质/名称")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("file:name?.pdf")
                                .fileUrl(attachment.toUri().toString())
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q));

        byte[] zipBytes = service.batchExportZip(List.of(1L));

        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        assertThat(entryNames).hasSize(1);
        // 非法字符应被替换为 _
        assertThat(entryNames.get(0)).doesNotContain("/", ":", "?");
    }

    /**
     * 空 ID 列表应抛 InvalidArgumentException。
     */
    @Test
    void shouldRejectEmptyIds() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.batchExportZip(List.of()))
                .isInstanceOf(com.xiyu.bid.exception.InvalidArgumentException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.batchExportZip(null))
                .isInstanceOf(com.xiyu.bid.exception.InvalidArgumentException.class);
    }

    /**
     * 回归测试：fileUrl 是裸文件名（DB 实际存储形态，BatchAttachmentService.setFileUrl(uniqueFilename)），
     * 且本地存储缺失时，writeAttachmentToZip 必须跳过该附件，不再生成 .txt 占位文件污染 ZIP。
     * 当全部选中资质均无有效附件时，应抛 InvalidArgumentException 给出明确提示。
     *
     * 触发场景：用户上传附件后磁盘文件丢失，或 storage-path 配置不一致。
     */
    @Test
    void shouldSkipAttachmentWhenFileUrlIsRelativeAndLocalMissing() {
        // fileUrl 是裸文件名（非绝对 URL），本地存储目录里也不存在该文件
        QualificationDTO q = QualificationDTO.builder()
                .id(99L)
                .name("测试资质")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("missing.pdf")
                                .fileUrl("missing.pdf")   // 裸文件名，DB 真实形态
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q));

        // 不应生成含 .txt 占位文件的 ZIP，而应明确告知无可下载附件
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.batchExportZip(List.of(99L)))
                .isInstanceOf(com.xiyu.bid.exception.InvalidArgumentException.class)
                .hasMessageContaining("无可下载附件");
    }

    /**
     * 混合场景：部分资质附件有效，部分附件缺失/非绝对 URL。
     * 有效附件应正常打包；缺失附件应被跳过，不得生成 .txt 占位文件。
     */
    @Test
    void shouldSkipMissingAttachmentsButKeepValidOnes() throws Exception {
        Path validAttachment = tempDir.resolve("license.pdf");
        Files.writeString(validAttachment, "valid-content");

        QualificationDTO withValid = QualificationDTO.builder()
                .id(1L)
                .name("有效资质")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("license.pdf")
                                .fileUrl(validAttachment.toUri().toString())
                                .build()
                ))
                .build();

        QualificationDTO withMissing = QualificationDTO.builder()
                .id(2L)
                .name("缺失资质")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("missing.pdf")
                                .fileUrl("missing.pdf")   // 裸文件名且本地缺失
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(withValid, withMissing));

        byte[] zipBytes = service.batchExportZip(List.of(1L, 2L));

        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        assertThat(entryNames).hasSize(1);
        assertThat(entryNames.get(0)).isEqualTo("有效资质_license.pdf");
    }

    /**
     * CO-544 回归测试：当 attachment 的 fileUrl 与主实体 fileUrl 相同（指向同一物理文件），
     * 批量下载 ZIP 不应将同一物理文件写入两次。
     *
     * 触发场景：生产 DB 中 qualification_attachments.file_url == business_qualifications.file_url，
     * 且 attachment.file_name 含历史 .pdf.pdf 双后缀。当前代码分别遍历 attachments 和主实体 fileUrl，
     * 导致同一物理文件被写入 ZIP 两次（entry 名因 file_name 不同而不同，ZipEntryDeduplicator 无法去重）。
     */
    @Test
    void shouldNotDuplicateWhenAttachmentFileUrlEqualsMainFileUrl() throws Exception {
        // 模拟生产 DB 真实形态：fileUrl 是裸文件名，本地存储文件存在
        Path attachmentDir = tempDir.resolve("21");
        Files.createDirectories(attachmentDir);
        Path attachmentFile = attachmentDir.resolve("QUAL_0512024ITSM038R0C_01_ISO20000.pdf");
        Files.writeString(attachmentFile, "pdf-content");

        QualificationDTO q = QualificationDTO.builder()
                .id(21L)
                .name("ISO20000")
                .fileUrl("QUAL_0512024ITSM038R0C_01_ISO20000.pdf")
                .attachments(List.of(
                        QualificationAttachmentDTO.builder()
                                .fileName("QUAL_0512024ITSM038R0C_01_ISO20000.pdf.pdf")
                                .fileUrl("QUAL_0512024ITSM038R0C_01_ISO20000.pdf")
                                .build()
                ))
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q));

        byte[] zipBytes = service.batchExportZip(List.of(21L));

        List<String> entryNames;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }

        // 期望：ZIP 中只有 1 个 entry（同一物理文件不应被写入两次）
        assertThat(entryNames).hasSize(1);
        assertThat(entryNames.get(0)).isEqualTo("ISO20000_QUAL_0512024ITSM038R0C_01_ISO20000.pdf");
    }

    /**
     * 回归测试：选中的资质均无可下载附件时，应抛 InvalidArgumentException，
     * 避免生成 0 字节无效 ZIP 导致前端"批量下载失败"。
     */
    @Test
    void shouldRejectWhenSelectedQualificationsHaveNoAttachments() {
        QualificationDTO q1 = QualificationDTO.builder()
                .id(1L)
                .name("无附件资质A")
                .fileUrl(null)
                .attachments(List.of())
                .build();
        QualificationDTO q2 = QualificationDTO.builder()
                .id(2L)
                .name("无附件资质B")
                .fileUrl("  ")
                .attachments(null)
                .build();

        when(flatQuery.listAll(null, null)).thenReturn(List.of(q1, q2));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.batchExportZip(List.of(1L, 2L)))
                .isInstanceOf(com.xiyu.bid.exception.InvalidArgumentException.class)
                .hasMessageContaining("无可下载附件");
    }
}
