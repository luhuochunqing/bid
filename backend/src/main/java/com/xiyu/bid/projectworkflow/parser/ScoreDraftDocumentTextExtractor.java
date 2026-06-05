package com.xiyu.bid.projectworkflow.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScoreDraftDocumentTextExtractor {

    private static final char TABLE_CELL_SEPARATOR = '\u0007';

    private final WordTextExtractor wordTextExtractor;

    public ScoreDraftDocumentTextExtractor(WordTextExtractor wordTextExtractor) {
        this.wordTextExtractor = wordTextExtractor;
    }

    public String extract(InputStream inputStream, FileType fileType) throws IOException {
        return switch (fileType) {
            case DOCX, DOC -> wordTextExtractor.extract(inputStream, fileType);
            case XLSX, XLS -> extractWorkbookText(inputStream);
            case PDF -> extractPdfText(inputStream);
        };
    }

    private String extractWorkbookText(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder text = new StringBuilder();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex)) {
                    continue;
                }
                appendSheet(text, sheet, formatter, evaluator);
            }
            return text.toString();
        }
    }

    private void appendSheet(StringBuilder text, Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (sheet.getSheetName() != null && !sheet.getSheetName().isBlank()) {
            text.append(sheet.getSheetName().trim()).append('\n');
        }
        for (Row row : sheet) {
            List<String> cells = extractRowCells(row, formatter, evaluator);
            if (!cells.isEmpty()) {
                text.append(String.join(String.valueOf(TABLE_CELL_SEPARATOR), cells)).append('\n');
            }
        }
    }

    private List<String> extractRowCells(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> cells = new ArrayList<>();
        short firstCell = row.getFirstCellNum();
        short lastCell = row.getLastCellNum();
        if (firstCell < 0 || lastCell < 0) {
            return cells;
        }
        for (int cellIndex = firstCell; cellIndex < lastCell; cellIndex++) {
            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
            if (!value.isBlank()) {
                cells.add(value);
            }
        }
        return cells;
    }

    private String extractPdfText(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document)
                    .replace('\u00A0', ' ')
                    .replaceAll(" {2,}", "\n");
        }
    }
}
