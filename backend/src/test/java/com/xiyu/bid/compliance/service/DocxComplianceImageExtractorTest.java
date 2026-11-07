// Input: 合成 DOCX 字节流及候选分页内容
// Output: 验证 DocxComplianceImageExtractor 流式提取合规章节图片并绑定逻辑页码 pageHint
// Pos: compliance/service/ - DOCX 合规关键页流式抽图测试
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.AuthorizationLetterCheckPolicy;
import com.xiyu.bid.compliance.domain.policy.BidLetterCheckPolicy;
import com.xiyu.bid.compliance.domain.policy.DocumentPageVisionDetector;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocxComplianceImageExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("流式扫描 DOCX 段落与关系，跳过目录行并将授权书/投标人承诺函图片精准绑定到正文逻辑页码")
    void extractsComplianceImagesFromDocxAndBindsPageHints() throws IOException {
        Path docx = tempDir.resolve("bid.docx");
        writeSyntheticDocx(docx);

        DocumentContent content = new DocumentContent("bid.docx", "hash-1360",
                "目录\n授权委托书................2\n\n授权委托书\n【已附图片】\n投标人承诺函\n【已附图片】",
                List.of(
                        new DocumentContent.PageContent(1, "目 录\n一、授权委托书................2\n二、投标人承诺函................2", List.of()),
                        new DocumentContent.PageContent(2, "公平交易承诺函\n\n授权委托书\n\n投标人承诺函", List.of()),
                        new DocumentContent.PageContent(6, "2025年12月11日登记的最新版营业执照", List.of())
                ));

        List<PageImage> images = DocxComplianceImageExtractor.extract(docx, content, Set.of(2, 6));
        assertThat(images).hasSize(3);
        assertThat(images).extracting(PageImage::pageHint).containsExactly(2, 2, 6);
        assertThat(images).extracting(PageImage::section).containsExactly("授权委托书", "投标人承诺函", "营业执照");

        Path real1360Docx = Path.of("/tmp/xiyu-1360-diagnosis/bid.docx");
        Path real1360Text = Path.of("/tmp/xiyu-1360-diagnosis/bid-text.txt");
        if (Files.isRegularFile(real1360Docx) && Files.isRegularFile(real1360Text)) {
            String rawText = Files.readString(real1360Text);
            DocumentContent realContent = new DocumentContent(
                    "新兴际华-2026.6.24.docx", "real-1360", rawText, DocumentContent.virtualPages(rawText));
            Set<Integer> selected = com.xiyu.bid.compliance.domain.policy.DocumentEvidencePageSelector.select(realContent);
            List<PageImage> realImages = DocxComplianceImageExtractor.extract(real1360Docx, realContent, selected);
            assertThat(realImages).isNotEmpty();
            assertThat(realImages).allSatisfy(img -> {
                assertThat(img.pageHint()).isNotNull().isGreaterThanOrEqualTo(1);
                assertThat(img.base64()).isNotBlank();
            });
            assertThat(realImages).extracting(PageImage::section)
                    .contains("公平交易承诺函", "授权委托书", "投标人承诺函", "营业执照");
        }
    }

    @Test
    @DisplayName("Sidecar 跳过大体积 DOCX 抽图时，自动回退 DocxComplianceImageExtractor 并经 OCR 转写闭环核验")
    void sidecarAdapterFallsBackToDocxExtractorAndEnrichesWithOcrText() throws IOException {
        Path docx = tempDir.resolve("large-bid.docx");
        writeSyntheticDocx(docx);

        var adapter = new SidecarComplianceDocumentAdapter(null, (file, name, selected) -> List.of());
        DocumentPageVisionDetector ocrDetector = document -> List.of(
                new ScanPageTextPolicy.Transcription(2,
                        "授权委托书\n法定代表人：王立军\n委托代理人：张伟\n代理权限：全权代表本公司参加投标\n授权期限：自签署之日至投标有效期结束\n"
                                + "投标人承诺函\n致：新兴际华集团有限公司：\n我方承诺完全响应招标文件，投标有效期180日历天。\n投标人：西域智慧供应链（上海）股份公司（盖章）",
                        0.95));
        ReflectionTestUtils.setField(adapter, "documentPageVisionDetector", ocrDetector);

        DocumentContent original = new DocumentContent("large-bid.docx", "frozen-1360",
                "授权委托书\n投标人承诺函",
                List.of(
                        new DocumentContent.PageContent(1, "目 录\n一、授权委托书................2\n二、投标人承诺函................2", List.of()),
                        new DocumentContent.PageContent(2, "授权委托书\n投标人承诺函", List.of()),
                        new DocumentContent.PageContent(6, "2025年12月11日登记的最新版营业执照", List.of())
                ));

        DocumentContent enriched = adapter.withPageImages(docx, "large-bid.docx", original);
        assertThat(enriched.images()).isNotEmpty();
        assertThat(AuthorizationLetterCheckPolicy.check(enriched).status())
                .isEqualTo(com.xiyu.bid.compliance.domain.QualityCheckStatus.PASS);
        assertThat(BidLetterCheckPolicy.check(enriched).status())
                .isEqualTo(com.xiyu.bid.compliance.domain.QualityCheckStatus.PASS);
    }

    private static void writeSyntheticDocx(final Path target) throws IOException {
        String relsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image2.png"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image3.jpeg"/>
                </Relationships>
                """;
        String docXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <w:body>
                    <w:p><w:r><w:t>一、授权委托书 .................... 2</w:t></w:r></w:p>
                    <w:p><w:r><w:t>授权委托书</w:t></w:r></w:p>
                    <w:p><w:r><a:blip r:embed="rId1"/></w:r></w:p>
                    <w:p><w:r><w:t>投标人承诺函</w:t></w:r></w:p>
                    <w:p><w:r><a:blip r:embed="rId2"/></w:r></w:p>
                    <w:p><w:r><w:t>2025年12月11日登记的最新版营业执照</w:t></w:r></w:p>
                    <w:p><w:r><a:blip r:embed="rId3"/></w:r></w:p>
                  </w:body>
                </w:document>
                """;
        byte[] dummyPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            writeZipEntry(zos, "word/_rels/document.xml.rels", relsXml.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "word/document.xml", docXml.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "word/media/image1.png", dummyPng);
            writeZipEntry(zos, "word/media/image2.png", dummyPng);
            writeZipEntry(zos, "word/media/image3.jpeg", dummyPng);
        }
    }

    private static void writeZipEntry(final ZipOutputStream zos, final String name, final byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(data.length);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

