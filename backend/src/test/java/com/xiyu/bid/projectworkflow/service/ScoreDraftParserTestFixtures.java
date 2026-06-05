package com.xiyu.bid.projectworkflow.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

final class ScoreDraftParserTestFixtures {

    private ScoreDraftParserTestFixtures() {
    }

    static byte[] buildDocx(String content) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String line : content.split("\n")) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] buildTableDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("第三章 评审程序及办法");
            document.createParagraph().createRun().setText("3.6 商务评分标准（20分）");
            XWPFTable businessTable = document.createTable(2, 4);
            setRow(businessTable, 0, "序号", "评价项目", "评分标准", "评标分值");
            setRow(businessTable, 1, "1", "同类项目业绩", "每提供1个同类项目业绩得2分，最高6分。", "6");
            document.createParagraph().createRun().setText("3.7 技术评分标准（50分）");
            XWPFTable technicalTable = document.createTable(2, 3);
            setRow(technicalTable, 0, "评分项目", "分数", "评分因素及标准");
            setRow(technicalTable, 1, "整体方案", "15", "最大程度满足采购文件要求。");
            document.createParagraph().createRun().setText("3.8 价格评分标准（30分）");
            XWPFTable priceTable = document.createTable(2, 4);
            setRow(priceTable, 0, "序号", "评价项目", "分值", "评分细则");
            setRow(priceTable, 1, "1", "品类折扣率", "25", "接受平台的价格管控，确保用户单位享受优惠价格。");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] buildXlsx() throws Exception {
        return buildWorkbook(new XSSFWorkbook());
    }

    static byte[] buildXls() throws Exception {
        return buildWorkbook(new HSSFWorkbook());
    }

    static byte[] buildPdf() {
        return ScoreDraftSyntheticPdfFixture.build(pdfLines());
    }

    static byte[] buildSameLinePdf() {
        return ScoreDraftSyntheticPdfFixture.build(List.of(
                "3.6 商务评分标准（20分）",
                "序号 评价项目 评分标准 评标分值",
                "1 同类项目业绩 每提供1个同类项目业绩得2分，最高6分。 6",
                "3.7 技术评分标准（50分）",
                "评分项目 分数 评分因素及标准",
                "整体方案 15 最大程度满足采购文件要求。",
                "3.8 价格评分标准（30分）",
                "序号 评价项目 分值 评分细则",
                "1 品类折扣率 25 接受平台的价格管控，确保用户单位享受优惠价格。"
        ));
    }

    private static byte[] buildWorkbook(Workbook workbook) throws Exception {
        try (workbook; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("评分标准");
            createRow(sheet, 0, "第三章 评审程序及办法");
            createRow(sheet, 1, "3.6 商务评分标准（20分）");
            createRow(sheet, 2, "序号", "评价项目", "评分标准", "评标分值");
            createRow(sheet, 3, "1", "同类项目业绩", "每提供1个同类项目业绩得2分，最高6分。", "6");
            createRow(sheet, 4, "3.7 技术评分标准（50分）");
            createRow(sheet, 5, "评分项目", "分数", "评分因素及标准");
            createRow(sheet, 6, "整体方案", "15", "最大程度满足采购文件要求。");
            createRow(sheet, 7, "3.8 价格评分标准（30分）");
            createRow(sheet, 8, "序号", "评价项目", "分值", "评分细则");
            createRow(sheet, 9, "1", "品类折扣率", "25", "接受平台的价格管控，确保用户单位享受优惠价格。");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void createRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
            row.createCell(cellIndex).setCellValue(values[cellIndex]);
        }
    }

    private static void setRow(XWPFTable table, int rowIndex, String... values) {
        for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
            table.getRow(rowIndex).getCell(cellIndex).setText(values[cellIndex]);
        }
    }

    private static List<String> pdfLines() {
        return List.of(
                "3.6 商务评分标准（20分）",
                "序号", "评价项目", "评分标准", "评标分值",
                "1", "同类项目业绩", "每提供1个同类项目业绩得2分，最高6分。", "6"
        );
    }
}
