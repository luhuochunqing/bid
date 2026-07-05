// Input: PlatformAccountRepository, PasswordEncryptionUtil, UserRepository
// Output: Platform account Excel export service
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 平台账户台账 Excel 导出服务。
 *
 * <p>支持两种导出模式：
 * <ul>
 *   <li>导出全部（无筛选条件时导出所有账户）</li>
 *   <li>按选中 ID 集合导出（前端多选勾选后调用）</li>
 * </ul>
 *
 * <p>密码字段：导出明文（复用 {@link PasswordEncryptionUtil#decrypt}）。
 * 调用方（Controller）已通过 @PreAuthorize 限制权限。
 *
 * <p>参考实现：{@link com.xiyu.bid.resources.service.CaCertificateExportService}。
 */
@Service
@RequiredArgsConstructor
public class PlatformAccountExportService {

    /** 表头：与列表展示列对齐 + 扩展状态列。顺序即列顺序。 */
    private static final String[] HEADERS = {
            "平台名称", "账号", "密码", "网址", "账号保管员",
            "平台类型", "是否有CA", "注册人", "注册手机", "注册邮箱",
            "账号状态", "备注"
    };

    /** 导出记录上限（防止极端数据量拖垮内存）。 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private final PlatformAccountRepository accountRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final UserRepository userRepository;

    /**
     * 导出平台账户台账 Excel。
     *
     * <p>优先级：若 {@code selectedIds} 非空，按 ID 集合导出；
     * 否则导出全部账户。
     *
     * @param selectedIds 选中的账户 ID 集合（可为空）
     * @return .xlsx 文件内容
     */
    public byte[] exportToExcel(Set<Long> selectedIds) {
        List<PlatformAccount> data = loadData(selectedIds);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("平台账户台账");
            CellStyle headerStyle = createHeaderStyle(wb);

            writeHeader(sheet, headerStyle);
            writeDataRows(sheet, data);
            ExcelAutoSizeHelper.autoSizeColumns(sheet, HEADERS.length);

            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate platform account Excel file", e);
        }
    }

    private List<PlatformAccount> loadData(Set<Long> selectedIds) {
        List<PlatformAccount> entities;
        if (selectedIds != null && !selectedIds.isEmpty()) {
            entities = accountRepository.findAllById(selectedIds);
        } else {
            entities = accountRepository.findAll();
        }
        if (entities.size() > MAX_EXPORT_ROWS) {
            throw new IllegalStateException(
                    "导出数据量超过上限(" + MAX_EXPORT_ROWS + "条)，请缩小筛选范围或减少选中项");
        }
        return entities;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRows(Sheet sheet, List<PlatformAccount> data) {
        Map<Long, String> custodianMap = loadCustodianMap(data);

        for (int i = 0; i < data.size(); i++) {
            PlatformAccount account = data.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(account.getAccountName()));
            row.createCell(col++).setCellValue(nullSafe(account.getUsername()));
            row.createCell(col++).setCellValue(decryptPassword(account.getPassword()));
            row.createCell(col++).setCellValue(nullSafe(account.getUrl()));
            row.createCell(col++).setCellValue(custodianMap.getOrDefault(account.getContactPerson(), ""));
            row.createCell(col++).setCellValue(formatPlatformType(account.getPlatformType()));
            row.createCell(col++).setCellValue(account.getHasCa() ? "是" : "否");
            row.createCell(col++).setCellValue(nullSafe(account.getRegistrant()));
            row.createCell(col++).setCellValue(nullSafe(account.getRegisterPhone()));
            row.createCell(col++).setCellValue(nullSafe(account.getRegisterEmail()));
            row.createCell(col++).setCellValue(formatStatus(account.getStatus()));
            row.createCell(col).setCellValue(nullSafe(account.getRemarks()));
        }
    }

    /**
     * 批量加载所有保管员姓名，避免每行 N+1 查询。
     * 返回 Map<userId, fullName>。
     */
    private Map<Long, String> loadCustodianMap(List<PlatformAccount> data) {
        if (data.isEmpty()) return Collections.emptyMap();
        Set<Long> userIds = data.stream()
                .map(PlatformAccount::getContactPerson)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user.getFullName() != null ? user.getFullName() : user.getUsername(),
                        (a, b) -> a
                ));
    }

    private String decryptPassword(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        try {
            return passwordEncryptionUtil.decrypt(stored);
        } catch (RuntimeException e) {
            return "******";
        }
    }

    private String formatPlatformType(PlatformAccount.PlatformType type) {
        if (type == null) return "";
        return type.getDescription();
    }

    private String formatStatus(PlatformAccount.AccountStatus status) {
        if (status == null) return "";
        return status.getDescription();
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
