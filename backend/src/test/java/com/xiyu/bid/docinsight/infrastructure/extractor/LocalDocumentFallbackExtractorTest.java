package com.xiyu.bid.docinsight.infrastructure.extractor;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDocumentFallbackExtractorTest {

    @Test
    void extractDocxWithPoi_extractsParagraphsAndTables() throws Exception {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("招标文件评分标准说明");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("评分因素");
            table.getRow(0).getCell(1).setText("分值");
            table.getRow(1).getCell(0).setText("服务方案");
            table.getRow(1).getCell(1).setText("30");
            doc.write(bos);
            docxBytes = bos.toByteArray();
        }

        String text = LocalDocumentFallbackExtractor.extractDocxWithPoi(docxBytes);
        assertThat(text).isNotNull();
        assertThat(text).contains("招标文件评分标准说明");
        assertThat(text).contains("评分因素");
        assertThat(text).contains("| --- | --- |");
        assertThat(text).contains("服务方案");
        assertThat(text).contains("30");
    }

    @Test
    void extractDocxWithPoi_whenCorruptBytes_returnsNullGracefully() {
        byte[] invalidBytes = "not a valid docx byte stream".getBytes();
        String text = LocalDocumentFallbackExtractor.extractDocxWithPoi(invalidBytes);
        assertThat(text).isNull();
    }

    @Test
    void extractDocxWithPoi_escapesPipeInTableCell() throws Exception {
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("方案|标准");
            table.getRow(0).getCell(1).setText("分值");
            table.getRow(1).getCell(0).setText("ISO9001|ISO14001");
            table.getRow(1).getCell(1).setText("10");
            doc.write(bos);
            docxBytes = bos.toByteArray();
        }

        String text = LocalDocumentFallbackExtractor.extractDocxWithPoi(docxBytes);
        assertThat(text).isNotNull();
        assertThat(text).contains("方案\\|标准");
        assertThat(text).contains("ISO9001\\|ISO14001");
    }
}
