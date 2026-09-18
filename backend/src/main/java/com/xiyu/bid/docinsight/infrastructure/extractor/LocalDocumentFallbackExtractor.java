package com.xiyu.bid.docinsight.infrastructure.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Local fallback extractor using Apache POI and PDFBox when Sidecar is unavailable.
 */
@Slf4j
final class LocalDocumentFallbackExtractor {

    private LocalDocumentFallbackExtractor() {
    }

    /**
     * Extract text from .doc file using Apache POI HWPF.
     * Returns null if extraction fails.
     */
    static String extractDocWithPoi(byte[] content) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             HWPFDocument doc = new HWPFDocument(bis);
             WordExtractor extractor = new WordExtractor(doc)) {
            String text = extractor.getText();
            if (text != null) {
                return text.trim();
            }
        } catch (IOException e) {
            log.warn("POI HWPF extraction failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("POI HWPF extraction unexpected error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract text from .docx file using Apache POI XWPF.
     * Returns null if extraction fails.
     */
    static String extractDocxWithPoi(byte[] content) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(bis)) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String line = p.getText();
                    if (line != null && !line.isBlank()) {
                        text.append(line.trim()).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    boolean isFirstRow = true;
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText().trim().replace("\n", " ").replace("|", "\\|"));
                        }
                        text.append("| ").append(String.join(" | ", cells)).append(" |\n");
                        if (isFirstRow && !cells.isEmpty()) {
                            List<String> separators = new ArrayList<>(cells.size());
                            for (int i = 0; i < cells.size(); i++) {
                                separators.add("---");
                            }
                            text.append("| ").append(String.join(" | ", separators)).append(" |\n");
                            isFirstRow = false;
                        }
                    }
                    text.append('\n');
                }
            }
            if (text.length() == 0) {
                try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    String extracted = extractor.getText();
                    if (extracted != null) {
                        text.append(extracted);
                    }
                }
            }
            String result = text.toString().trim();
            return result.isBlank() ? null : result;
        } catch (IOException e) {
            log.warn("POI XWPF extraction failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("POI XWPF extraction unexpected error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract text from .pdf file using Apache PDFBox.
     * Returns null if extraction fails.
     */
    static String extractPdfWithPdfBox(byte[] content) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             PDDocument document = PDDocument.load(bis)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text != null) {
                return text.trim()
                        .replace('\u00A0', ' ')
                        .replaceAll(" {2,}", " ");
            }
        } catch (IOException e) {
            log.warn("PDFBox extraction failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("PDFBox extraction unexpected error: {}", e.getMessage());
        }
        return null;
    }
}
