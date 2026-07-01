package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.customerLevel;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.customerType;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.dockingMethod;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.projectType;

/**
 * 业绩 Excel 导出服务
 */
@Service
@RequiredArgsConstructor
public class PerformanceExcelExporter {

    private final PerformanceRepository repository;
    private final PerformanceMapper mapper;
    private final PerformanceAlertConfigRepository alertConfigRepository;

    private static final int EXPORT_MAX_ROWS = 10000;
    private static final PerformanceAlertConfig DEFAULT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);

    private static final String[] TEMPLATE_HEADERS = {
            "合同名称", "签约单位", "集团公司名称", "客户类型", "所属行业",
            "项目类型", "对接方式", "客户级别", "合同是否含西域",
            "签约日期", "截止日期", "总截止日期", "到期天数", "到期提醒",
            "客户联系人", "客户联系方式", "属地", "客户地址", "合同中西域项目负责人",
            "合同协议附件文件名", "客户商城网站网址", "商城对接截图附件文件名",
            "国资委央企名录截图附件文件名", "品类页附件文件名",
            "签约抬头与央企集团关系证明附件文件名",
            "是否有中标通知书", "中标通知书附件文件名", "备注"
    };

    public byte[] export(List<Long> ids, PerformanceSearchCriteria criteria) throws IOException {
        List<PerformanceDTO> records;
        if (ids != null && !ids.isEmpty()) {
            records = ids.stream()
                    .map(id -> mapper.toDTO(repository.findById(id).orElse(null)))
                    .filter(r -> r != null).toList();
        } else {
            var config = alertConfigRepository.findActive().orElse(DEFAULT_CONFIG);
            var effectiveCriteria = criteria != null ? criteria : PerformanceSearchCriteria.empty();
            records = repository.findAll(effectiveCriteria, config)
                    .stream().map(mapper::toDTO).toList();
        }
        if (records.size() > EXPORT_MAX_ROWS) {
            throw new IllegalArgumentException("导出记录数超过上限 " + EXPORT_MAX_ROWS + " 条，请缩小筛选范围");
        }
        try (var wb = new XSSFWorkbook()) {
            writeExportSheet(wb, records);
            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeExportSheet(XSSFWorkbook wb, List<PerformanceDTO> records) {
        var sheet = wb.createSheet("业绩管理台账");
        String[] exportHeaders = new String[TEMPLATE_HEADERS.length + 5];
        System.arraycopy(TEMPLATE_HEADERS, 0, exportHeaders, 0, TEMPLATE_HEADERS.length);
        exportHeaders[28] = "状态"; exportHeaders[29] = "创建人";
        exportHeaders[30] = "创建时间"; exportHeaders[31] = "更新人"; exportHeaders[32] = "更新时间";
        var headerRow = sheet.createRow(0);
        var headerStyle = wb.createCellStyle();
        var font = wb.createFont(); font.setBold(true); headerStyle.setFont(font);
        for (int i = 0; i < exportHeaders.length; i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(exportHeaders[i]); cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 18 * 256);
        }
        for (int i = 0; i < records.size(); i++) {
            writeExportRow(sheet.createRow(i + 1), records.get(i));
        }
    }

    private void writeExportRow(Row row, PerformanceDTO r) {
        row.createCell(0).setCellValue(nvl(r.contractName()));
        row.createCell(1).setCellValue(nvl(r.signingEntity()));
        row.createCell(2).setCellValue(nvl(r.groupCompany()));
        row.createCell(3).setCellValue(customerType(r.customerType() != null ? r.customerType().name() : null));
        row.createCell(4).setCellValue(nvl(r.industry()));
        row.createCell(5).setCellValue(projectType(r.projectType() != null ? r.projectType().name() : null));
        row.createCell(6).setCellValue(dockingMethod(r.dockingMethod() != null ? r.dockingMethod().name() : null));
        row.createCell(7).setCellValue(customerLevel(r.customerLevel() != null ? r.customerLevel().name() : null));
        row.createCell(8).setCellValue("");
        row.createCell(9).setCellValue(r.signingDate() != null ? r.signingDate().toString() : "");
        row.createCell(10).setCellValue(r.expiryDate() != null ? r.expiryDate().toString() : "");
        row.createCell(11).setCellValue(r.totalExpiryDate() != null ? r.totalExpiryDate().toString() : "");
        row.createCell(12).setCellValue(r.daysRemaining());
        row.createCell(13).setCellValue("");
        row.createCell(14).setCellValue(nvl(r.contactPerson()));
        row.createCell(15).setCellValue(nvl(r.contactInfo()));
        row.createCell(16).setCellValue(nvl(r.territory()));
        row.createCell(17).setCellValue(nvl(r.customerAddress()));
        row.createCell(18).setCellValue(nvl(r.xiyuProjectManager()));
        row.createCell(19).setCellValue("");
        row.createCell(20).setCellValue(nvl(r.mallWebsiteUrl()));
        row.createCell(21).setCellValue("");
        row.createCell(22).setCellValue("");
        row.createCell(23).setCellValue("");
        row.createCell(24).setCellValue("");
        row.createCell(25).setCellValue(r.hasBidNotice() ? "是" : "否");
        row.createCell(26).setCellValue("");
        row.createCell(27).setCellValue(nvl(r.remarks()));
        row.createCell(28).setCellValue(r.status() != null ? r.status().name() : "");
        row.createCell(29).setCellValue("");
        row.createCell(30).setCellValue(r.createdAt() != null ? r.createdAt().toString() : "");
        row.createCell(31).setCellValue("");
        row.createCell(32).setCellValue(r.updatedAt() != null ? r.updatedAt().toString() : "");
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
