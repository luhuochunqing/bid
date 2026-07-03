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
     * 同一条资质有多个同名附件（attachments 列表内 + 主 fileUrl），
     * 也应去重。
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

        // 两个 entry：资质A_cert.docx 和 资质A_cert_1.docx（或类似去重后缀）
        assertThat(entryNames).hasSize(2);
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
}
