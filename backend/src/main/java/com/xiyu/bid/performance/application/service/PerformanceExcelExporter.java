package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

    /** CO-583: 列索引常量 — 与 TEMPLATE_HEADERS 顺序保持同步，消除 magic number 耦合。 */
    private static final int COL_GROUP_COMPANY = 2;
    private static final int COL_TOTAL_EXPIRY_DATE = 11;
    private static final int EXTRA_COLUMN_COUNT = 5;
    private static final int TOTAL_COLUMN_COUNT = TEMPLATE_HEADERS.length + EXTRA_COLUMN_COUNT;

    /** CO-583: 集团汇总行浅绿背景色（IndexedColors.LIGHT_GREEN = 42）。 */
    private static final short SUMMARY_FILL_COLOR = 42;

    public byte[] export(List<Long> ids, PerformanceSearchCriteria criteria) throws IOException {
        Map<String, LocalDate> groupTotalMap = repository.findGroupTotalExpiryDates();
        List<PerformanceDTO> records;
        if (ids != null && !ids.isEmpty()) {
            records = ids.stream()
                    .map(id -> mapper.toDTO(repository.findById(id).orElse(null), groupTotalMap))
                    .filter(r -> r != null).toList();
        } else {
            var config = alertConfigRepository.findActive().orElse(DEFAULT_CONFIG);
            var effectiveCriteria = criteria != null ? criteria : PerformanceSearchCriteria.empty();
            records = repository.findAll(effectiveCriteria, config)
                    .stream().map(r -> mapper.toDTO(r, groupTotalMap)).toList();
        }
        if (records.size() > EXPORT_MAX_ROWS) {
            throw new IllegalArgumentException("导出记录数超过上限 " + EXPORT_MAX_ROWS + " 条，请缩小筛选范围");
        }
        try (var wb = new XSSFWorkbook()) {
            writeExportSheet(wb, records, groupTotalMap);
            var out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * CO-583: 按集团分组导出。每组先写汇总行（浅绿加粗，集团名+总截止日期），再写明细行（总截止日期列留空）。
     * 集团间按汉字拼音排序；集团内按 signingDate 升序。
     * 空 groupCompany 的记录直接作为明细行输出，不生成汇总行。
     */
    private void writeExportSheet(XSSFWorkbook wb, List<PerformanceDTO> records,
                                  Map<String, LocalDate> groupTotalMap) {
        var sheet = wb.createSheet("业绩管理台账");
        String[] exportHeaders = new String[TOTAL_COLUMN_COUNT];
        System.arraycopy(TEMPLATE_HEADERS, 0, exportHeaders, 0, TEMPLATE_HEADERS.length);
        exportHeaders[TEMPLATE_HEADERS.length] = "状态";
        exportHeaders[TEMPLATE_HEADERS.length + 1] = "创建人";
        exportHeaders[TEMPLATE_HEADERS.length + 2] = "创建时间";
        exportHeaders[TEMPLATE_HEADERS.length + 3] = "更新人";
        exportHeaders[TEMPLATE_HEADERS.length + 4] = "更新时间";
        var headerRow = sheet.createRow(0);
        var headerStyle = wb.createCellStyle();
        var headerFont = wb.createFont(); headerFont.setBold(true); headerStyle.setFont(headerFont);
        for (int i = 0; i < exportHeaders.length; i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(exportHeaders[i]); cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 18 * 256);
        }

        CellStyle summaryStyle = createSummaryStyle(wb);
        Collator collator = Collator.getInstance(Locale.CHINA);

        // 分组：空 groupCompany 归到 NO_GROUP_KEY（不输出汇总行）
        Map<String, List<PerformanceDTO>> grouped = new TreeMap<>(collator);
        Map<String, List<PerformanceDTO>> noGroup = new TreeMap<>(collator);
        for (PerformanceDTO r : records) {
            String g = r.groupCompany();
            if (g == null || g.trim().isEmpty()) {
                noGroup.computeIfAbsent("", k -> new java.util.ArrayList<>()).add(r);
            } else {
                grouped.computeIfAbsent(g, k -> new java.util.ArrayList<>()).add(r);
            }
        }

        int rowIdx = 1;
        // 空集团记录先输出（明细行，不汇总）
        for (var entry : noGroup.entrySet()) {
            for (PerformanceDTO r : sortBySigningDate(entry.getValue())) {
                writeExportRow(sheet.createRow(rowIdx++), r);
            }
        }
        // 有集团分组：每组先汇总行后明细行
        for (var entry : grouped.entrySet()) {
            String group = entry.getKey();
            LocalDate total = groupTotalMap.get(group);
            writeSummaryRow(sheet.createRow(rowIdx++), group, total, summaryStyle);
            for (PerformanceDTO r : sortBySigningDate(entry.getValue())) {
                writeExportRow(sheet.createRow(rowIdx++), r);
            }
        }
    }

    private CellStyle createSummaryStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(SUMMARY_FILL_COLOR);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private List<PerformanceDTO> sortBySigningDate(List<PerformanceDTO> list) {
        return list.stream()
                .sorted((a, b) -> {
                    LocalDate sa = a.signingDate();
                    LocalDate sb = b.signingDate();
                    if (sa == null && sb == null) return 0;
                    if (sa == null) return 1;
                    if (sb == null) return -1;
                    return sa.compareTo(sb);
                })
                .collect(Collectors.toList());
    }

    /** CO-583: 集团汇总行，仅集团名与总截止日期有值，其余留空。 */
    private void writeSummaryRow(Row row, String groupCompany, LocalDate totalExpiry, CellStyle style) {
        for (int i = 0; i < TOTAL_COLUMN_COUNT; i++) {
            var cell = row.createCell(i);
            if (i == COL_GROUP_COMPANY) cell.setCellValue(nvl(groupCompany));
            else if (i == COL_TOTAL_EXPIRY_DATE) cell.setCellValue(totalExpiry != null ? totalExpiry.toString() : "");
            else cell.setCellValue("");
            cell.setCellStyle(style);
        }
    }

    private void writeExportRow(Row row, PerformanceDTO r) {
        row.createCell(0).setCellValue(nvl(r.contractName()));
        row.createCell(1).setCellValue(nvl(r.signingEntity()));
        row.createCell(COL_GROUP_COMPANY).setCellValue(nvl(r.groupCompany()));
        row.createCell(3).setCellValue(customerType(r.customerType() != null ? r.customerType().name() : null));
        row.createCell(4).setCellValue(nvl(r.industry()));
        row.createCell(5).setCellValue(projectType(r.projectType() != null ? r.projectType().name() : null));
        row.createCell(6).setCellValue(dockingMethod(r.dockingMethod() != null ? r.dockingMethod().name() : null));
        row.createCell(7).setCellValue(customerLevel(r.customerLevel() != null ? r.customerLevel().name() : null));
        row.createCell(8).setCellValue("");
        row.createCell(9).setCellValue(r.signingDate() != null ? r.signingDate().toString() : "");
        row.createCell(10).setCellValue(r.expiryDate() != null ? r.expiryDate().toString() : "");
        // CO-583: 明细行总截止日期列留空（聚合值由汇总行展示）
        row.createCell(COL_TOTAL_EXPIRY_DATE).setCellValue("");
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
