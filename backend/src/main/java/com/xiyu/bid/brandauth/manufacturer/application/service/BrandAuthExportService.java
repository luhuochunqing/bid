package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Excel export and template generation for brand authorizations. */
@Service
@RequiredArgsConstructor
public final class BrandAuthExportService {

    /** Column headers for manufacturer authorization sheet. */
    private static final String[] MFG_HEADERS = {
            "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
            "原厂授权附件文件名", "授权开始时间", "授权结束时间",
            "备注", "补充材料附件文件名"
    };
    /** Column headers for agent authorization sheet. */
    private static final String[] AGENT_HEADERS = {
            "一级产线", "品牌ID", "品牌", "进口/国产", "品牌原厂名称",
            "授权1附件文件名", "授权1开始时间", "授权1结束时间",
            "授权1备注", "代理商名称",
            "授权2附件文件名", "授权2开始时间", "授权2结束时间",
            "授权2备注"
    };

    /** Repository for manufacturer authorizations. */
    private final ManufacturerAuthorizationRepository repository;
    /** Repository for attachments. */
    private final BrandAuthAttachmentJpaRepository attachmentRepository;

    /** Export all authorizations as a two-sheet Excel workbook. */
    public byte[] exportAll() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            List<ManufacturerAuthorization> all =
                    repository.findByStatus(null);
            List<ManufacturerAuthorization> mfg = all.stream()
                    .filter(a -> !"AGENT".equals(a.authorizationType()))
                    .toList();
            List<ManufacturerAuthorization> agt = all.stream()
                    .filter(a -> "AGENT".equals(a.authorizationType()))
                    .toList();
            writeSheet(wb, "原厂授权", MFG_HEADERS, mfg, false);
            writeSheet(wb, "代理商授权", AGENT_HEADERS, agt, true);
            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Generate an empty import template with two sheets. */
    public byte[] downloadTemplate() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            writeTemplateSheet(wb, "原厂授权", MFG_HEADERS);
            writeTemplateSheet(wb, "代理商授权", AGENT_HEADERS);
            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(final Workbook wb, final String name,
            final String[] headers,
            final List<ManufacturerAuthorization> list,
            final boolean isAgent) {
        Sheet sheet = wb.createSheet(name);
        Row headerRow = sheet.createRow(0);
        var headerStyle = wb.createCellStyle();
        var headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (var a : list) {
            Row row = sheet.createRow(rowIdx++);
            List<String> atts = attachmentRepository
                    .findByAuthorizationId(a.id()).stream()
                    .map(BrandAuthAttachmentEntity::getFileName).toList();
            int col = 0;
            row.createCell(col++).setCellValue(
                    a.productLine().getDisplayName());
            row.createCell(col++).setCellValue(a.brandId());
            row.createCell(col++).setCellValue(a.brandName());
            row.createCell(col++).setCellValue(a.importDomestic());
            row.createCell(col++).setCellValue(a.manufacturerName());
            if (isAgent) {
                row.createCell(col++).setCellValue(
                        join(filterByType(atts, "auth1")));
                row.createCell(col++).setCellValue(
                        fmt(a.auth1StartDate()));
                row.createCell(col++).setCellValue(
                        fmt(a.auth1EndDate()));
                row.createCell(col++).setCellValue(
                        emptyIfNull(a.auth1Remarks()));
                row.createCell(col++).setCellValue(
                        emptyIfNull(a.agentName()));
                row.createCell(col++).setCellValue(
                        join(filterByType(atts, "auth2")));
            } else {
                row.createCell(col++).setCellValue(
                        join(filterByType(atts, "AUTH_DOC")));
            }
            row.createCell(col++).setCellValue(fmt(a.authStartDate()));
            row.createCell(col++).setCellValue(fmt(a.authEndDate()));
            row.createCell(col++).setCellValue(
                    emptyIfNull(a.remarks()));
            if (!isAgent) {
                row.createCell(col++).setCellValue(
                        join(filterByType(atts, "SUPPLEMENTARY")));
            } else {
                row.createCell(col++).setCellValue(
                        fmt(a.auth2StartDate()));
                row.createCell(col++).setCellValue(
                        fmt(a.auth2EndDate()));
                row.createCell(col++).setCellValue(
                        emptyIfNull(a.auth2Remarks()));
            }
        }
        ExcelAutoSizeHelper.autoSizeColumns(sheet, headers.length);
    }

    private void writeTemplateSheet(final Workbook wb, final String name,
            final String[] headers) {
        Sheet sheet = wb.createSheet(name);
        Row headerRow = sheet.createRow(0);
        var style = wb.createCellStyle();
        var font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        ExcelAutoSizeHelper.autoSizeColumns(sheet, headers.length);
    }

    private static String fmt(final LocalDate d) {
        return d != null ? d.toString() : "";
    }

    private static String emptyIfNull(final String s) {
        return s != null ? s : "";
    }

    private static String join(final List<String> list) {
        return String.join(";", list);
    }

    private static List<String> filterByType(final List<String> names,
            final String type) {
        return names.stream().filter(n -> n.contains(type)).toList();
    }
}
