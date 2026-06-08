package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.application.dto.CertificateDTO;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class PersonnelExcelExporter {

    private static final String SHEET_BASIC_INFO = "基础信息";
    private static final String SHEET_EDUCATION = "教育经历";
    private static final String SHEET_CERTIFICATES = "证书与职称";

    private static final String[] BASIC_INFO_HEADERS = {
            "工号", "姓名", "性别", "入职时间", "手机", "学历", "技术职称", "状态"
    };

    private static final String[] EDUCATION_HEADERS = {
            "工号", "姓名", "学校名称", "入学时间", "毕业时间", "最高学历", "学习形式", "专业"
    };

    private static final String[] CERTIFICATE_HEADERS = {
            "工号", "姓名", "证书名称", "证书编号", "证书类型", "发证日期", "有效期至", "附件文件名"
    };

    public byte[] export(List<PersonnelDTO> personnelList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet basicInfoSheet = workbook.createSheet(SHEET_BASIC_INFO);
            Sheet educationSheet = workbook.createSheet(SHEET_EDUCATION);
            Sheet certificatesSheet = workbook.createSheet(SHEET_CERTIFICATES);

            createBasicInfoSheet(basicInfoSheet, personnelList, headerStyle);
            createEducationSheet(educationSheet, personnelList, headerStyle);
            createCertificatesSheet(certificatesSheet, personnelList, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createBasicInfoSheet(Sheet sheet, List<PersonnelDTO> personnelList, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < BASIC_INFO_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(BASIC_INFO_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 18 * 256);
        }

        int rowNum = 1;
        for (PersonnelDTO p : personnelList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(nvl(p.employeeNumber()));
            row.createCell(1).setCellValue(nvl(p.name()));
            row.createCell(2).setCellValue(nvl(p.gender()));
            row.createCell(3).setCellValue(p.entryDate() != null ? p.entryDate().toString() : "");
            row.createCell(4).setCellValue(nvl(p.phone()));
            row.createCell(5).setCellValue(nvl(p.highestEducation()));
            row.createCell(6).setCellValue(nvl(p.technicalTitle()));
            row.createCell(7).setCellValue(p.status() != null ? p.status().name() : "");
        }
    }

    private void createEducationSheet(Sheet sheet, List<PersonnelDTO> personnelList, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < EDUCATION_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(EDUCATION_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 18 * 256);
        }

        int rowNum = 1;
        for (PersonnelDTO p : personnelList) {
            if (p.educations() == null || p.educations().isEmpty()) {
                continue;
            }
            for (var edu : p.educations()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nvl(p.employeeNumber()));
                row.createCell(1).setCellValue(nvl(p.name()));
                row.createCell(2).setCellValue(nvl(edu.schoolName()));
                row.createCell(3).setCellValue(edu.startDate() != null ? edu.startDate().toString() : "");
                row.createCell(4).setCellValue(edu.endDate() != null ? edu.endDate().toString() : "");
                row.createCell(5).setCellValue(nvl(edu.highestEducation()));
                row.createCell(6).setCellValue(nvl(edu.studyForm()));
                row.createCell(7).setCellValue(nvl(edu.major()));
            }
        }
    }

    private void createCertificatesSheet(Sheet sheet, List<PersonnelDTO> personnelList, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < CERTIFICATE_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(CERTIFICATE_HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 20 * 256);
        }

        int rowNum = 1;
        for (PersonnelDTO p : personnelList) {
            if (p.certificates() == null || p.certificates().isEmpty()) {
                continue;
            }
            for (CertificateDTO cert : p.certificates()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nvl(p.employeeNumber()));
                row.createCell(1).setCellValue(nvl(p.name()));
                row.createCell(2).setCellValue(nvl(cert.name()));
                row.createCell(3).setCellValue(nvl(cert.certificateNumber()));
                row.createCell(4).setCellValue(cert.type() != null ? cert.type().name() : "");
                row.createCell(5).setCellValue(cert.issueDate() != null ? cert.issueDate().toString() : "");
                row.createCell(6).setCellValue(cert.expiryDate() != null ? cert.expiryDate().toString() : "");
                row.createCell(7).setCellValue(nvl(cert.attachmentUrl()));
            }
        }
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
