// Input: list 资质 + ids filter
// Output: Excel 工作簿（OutputStream）
// Pos: Service/工具 - 资质台账导出 + 模板生成（从 QualificationService 拆分以保 line-budget）
// 维护声明: 列定义与 4.1.3.4 批量导出保持一致；模板示例数据为标准合规示例.
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.qualification.dto.QualificationDTO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * §4.1.3.4 蓝图：资质台账导出 + 模板生成（从 QualificationService 拆出以保 line-budget）。
 * <p>
 * 16 列 = 11 字段 + 状态 + 创建人/时间 + 更新人/时间。idsCsv 提供时按 ID 过滤。
 */
@Component
public class QualificationExcelSupport {

    private static final String[] LEDGER_COLUMNS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理联系方式", "认证范围", "证书审核提醒", "附件文件名",
            "状态", "创建人", "创建时间", "更新人", "更新时间"
    };

    private static final String[] TEMPLATE_COLUMNS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理联系方式", "认证范围", "证书审核提醒", "附件文件名"
    };

    public void writeLedger(List<QualificationDTO> source, String idsCsv, OutputStream out) throws IOException {
        List<QualificationDTO> filtered = filterByIds(source, idsCsv);
        try (var wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("资质证书台账");
            var hr = sh.createRow(0);
            for (int i = 0; i < LEDGER_COLUMNS.length; i++) hr.createCell(i).setCellValue(LEDGER_COLUMNS[i]);
            int r = 1;
            for (var q : filtered) writeLedgerRow(sh, q, r++);
            wb.write(out);
        }
    }

    public void writeTemplate(OutputStream out) throws IOException {
        try (var wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("资质证书");
            var hr = sh.createRow(0);
            for (int i = 0; i < TEMPLATE_COLUMNS.length; i++) hr.createCell(i).setCellValue(TEMPLATE_COLUMNS[i]);
            String[] example = {
                    "示例：ISO9001 质量管理体系认证", "FIRST", "中国计量认证中心",
                    "EXAMPLE-2024-001", "2024-01-15", "2027-12-31",
                    "示例代理认证公司", "13800138000",
                    "示例：覆盖产品设计、生产、销售", "每年 3 月年审",
                    "QUAL_EXAMPLE-2024-001_01_示例证书.pdf"
            };
            var exRow = sh.createRow(1);
            for (int i = 0; i < example.length; i++) exRow.createCell(i).setCellValue(example[i]);
            wb.write(out);
        }
    }

    private void writeLedgerRow(org.apache.poi.ss.usermodel.Sheet sh, QualificationDTO q, int rowIdx) {
        var row = sh.createRow(rowIdx);
        row.createCell(0).setCellValue(nullToEmpty(q.getName()));
        row.createCell(1).setCellValue(q.getLevel() != null ? q.getLevel().name() : "");
        row.createCell(2).setCellValue(nullToEmpty(q.getIssuer()));
        row.createCell(3).setCellValue(nullToEmpty(q.getCertificateNo()));
        row.createCell(4).setCellValue(q.getIssueDate() != null ? q.getIssueDate().toString() : "");
        row.createCell(5).setCellValue(q.getExpiryDate() != null ? q.getExpiryDate().toString() : "");
        row.createCell(6).setCellValue(nullToEmpty(q.getAgency()));
        row.createCell(7).setCellValue(nullToEmpty(q.getAgencyContact()));
        row.createCell(8).setCellValue(nullToEmpty(q.getCertScope()));
        row.createCell(9).setCellValue(nullToEmpty(q.getCertReviewNote()));
        row.createCell(10).setCellValue(nullToEmpty(q.getFileUrl()));
        row.createCell(11).setCellValue(q.getStatus() != null ? statusLabel(q.getStatus()) : "");
        row.createCell(12).setCellValue(nullToEmpty(q.getHolderName()));
        row.createCell(13).setCellValue(q.getCreatedAt() != null ? q.getCreatedAt().toString() : "");
        row.createCell(14).setCellValue(""); // 更新人：schema 未保留，留空
        row.createCell(15).setCellValue(q.getUpdatedAt() != null ? q.getUpdatedAt().toString() : "");
    }

    private static List<QualificationDTO> filterByIds(List<QualificationDTO> source, String idsCsv) {
        if (idsCsv == null || idsCsv.isBlank()) return source;
        Set<Long> idSet = Arrays.stream(idsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        return source.stream().filter(q -> idSet.contains(q.getId())).toList();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status.toLowerCase()) {
            case "valid", "in_stock" -> "有效";
            case "expiring" -> "即将到期";
            case "expired" -> "已过期";
            case "retired" -> "已下架";
            default -> status;
        };
    }
}
