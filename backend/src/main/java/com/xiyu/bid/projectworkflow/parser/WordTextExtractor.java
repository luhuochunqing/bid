package com.xiyu.bid.projectworkflow.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
public class WordTextExtractor {

    public String extract(InputStream inputStream, FileType fileType) throws IOException {
        return switch (fileType) {
            case DOCX -> extractDocxText(inputStream);
            case DOC -> extractDocText(inputStream);
            default -> throw new IllegalArgumentException("Word 文本抽取器仅支持 .doc/.docx 文件");
        };
    }

    private String extractDocxText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                switch (element.getElementType()) {
                    case PARAGRAPH -> appendParagraph(text, (XWPFParagraph) element);
                    case TABLE -> appendTable(text, (XWPFTable) element);
                    default -> {
                    }
                }
            }
            if (text.length() == 0) {
                try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    text.append(extractor.getText());
                }
            }
            return text.toString();
        }
    }

    private String extractDocText(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private void appendParagraph(StringBuilder text, XWPFParagraph paragraph) {
        String line = normalizeInlineText(paragraph.getText());
        if (!line.isBlank()) {
            text.append(line).append('\n');
        }
    }

    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(this::normalizeInlineText)
                    .filter(cell -> !cell.isBlank())
                    .toList();
            if (!cells.isEmpty()) {
                text.append(String.join("\u0007", cells)).append('\n');
            }
        }
    }

    private String normalizeInlineText(String text) {
        return Optional.ofNullable(text)
                .orElse("")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
