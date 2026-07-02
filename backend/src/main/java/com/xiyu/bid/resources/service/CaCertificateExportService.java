package com.xiyu.bid.resources.service;

import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import jakarta.persistence.criteria.Predicate;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CA 证书台账 Excel 导出服务。
 *
 * <p>支持两种导出模式：
 * <ul>
 *   <li>按筛选条件导出全部（复用 {@link CaCertificateService#list} 的 Specification 过滤逻辑）</li>
 *   <li>按选中 ID 集合导出（前端多选勾选后调用）</li>
 * </ul>
 *
 * <p>表头与 {@code CaCertificateImportPolicy.HEADERS} 对齐（便于"导出→修改→重新导入"闭环），
 * 并扩展"借用状态/证书状态"两列用于台账展示。状态值统一输出中文，与导入模板的下拉选项一致。
 *
 * <p>密码字段：导出明文（复用 {@link PasswordEncryptionUtil#decrypt}）。
 * 调用方（Controller）已通过 @PreAuthorize 限制为 ADMIN/MANAGER/bid-Team，
 * 与批量导入按钮权限对齐（用户明确需求）。
 *
 * <p>参考实现：{@link MarginExportService}（同模块，结构完全复刻）。
 */
@Service
@RequiredArgsConstructor
public class CaCertificateExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 表头：与 import 模板对齐 + 扩展状态列。顺序即列顺序。 */
    private static final String[] HEADERS = {
            "CA类型", "印章类型", "持有人", "保管员姓名",
            "有效期至", "颁发机构", "电子账号", "CA密码",
            "平台URL", "关联平台ID", "借用状态", "证书状态", "备注"
    };

    /** 导出记录上限（防止极端数据量拖垮内存，参考 CaseExportExcelAppService 的 10000 上限）。 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private static final Map<String, String> CA_TYPE_LABEL = Map.of(
            "ENTITY_CA", "实体CA", "ELECTRONIC_CA", "电子CA");
    private static final Map<String, String> SEAL_TYPE_LABEL = Map.of(
            "OFFICIAL_SEAL", "公章", "LEGAL_PERSON_SEAL", "法人章",
            "LEGAL_SIGN", "法人签字", "CONTACT_SIGN", "联系人签字");
    private static final Map<String, String> BORROW_STATUS_LABEL = Map.of(
            "IN_STOCK", "在库", "BORROWED", "已借出", "OVERDUE", "已逾期");
    private static final Map<String, String> STATUS_LABEL = Map.of(
            "ACTIVE", "有效", "EXPIRING", "即将到期", "EXPIRED", "已过期", "INACTIVE", "已下架");

    private final CaCertificateRepository certificateRepository;
    private final CaCertificatePlatformRepository platformLinkRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /**
     * 导出 CA 证书台账 Excel。
     *
     * <p>优先级：若 {@code selectedIds} 非空，按 ID 集合导出（不应用筛选条件，不排除 INACTIVE）；
     * 否则按筛选条件导出全部（复用 list 的 Specification 逻辑，排除 INACTIVE）。
     *
     * @param filters      筛选条件（与 list 端点同字段：status/borrowStatus/keyword/caType/sealType）
     * @param selectedIds  选中的 CA ID 集合（可为空）
     * @return .xlsx 文件内容
     */
    public byte[] exportToExcel(CaExportFilters filters, Set<Long> selectedIds) {
        List<CaCertificateEntity> data = loadData(filters, selectedIds);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("CA证书台账");
            CellStyle headerStyle = createHeaderStyle(wb);

            writeHeader(sheet, headerStyle);
            writeDataRows(sheet, data);
            ExcelAutoSizeHelper.autoSizeColumns(sheet, HEADERS.length);

            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate CA certificate Excel file", e);
        }
    }

    private List<CaCertificateEntity> loadData(CaExportFilters filters, Set<Long> selectedIds) {
        List<CaCertificateEntity> entities;
        if (selectedIds != null && !selectedIds.isEmpty()) {
            entities = certificateRepository.findAllById(selectedIds);
        } else {
            Specification<CaCertificateEntity> spec = buildSpecification(filters);
            entities = certificateRepository.findAll(spec);
        }
        if (entities.size() > MAX_EXPORT_ROWS) {
            throw new IllegalStateException(
                    "导出数据量超过上限(" + MAX_EXPORT_ROWS + "条)，请缩小筛选范围或减少选中项");
        }
        return entities;
    }

    /**
     * 构建 Specification（复用 CaCertificateService#list 的过滤逻辑）。
     * 包含 status <> 'INACTIVE' 的默认过滤，与列表展示口径一致。
     */
    private Specification<CaCertificateEntity> buildSpecification(CaExportFilters f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), "INACTIVE"));
            if (f != null) {
                if (f.status() != null && !f.status().isEmpty()) {
                    predicates.add(cb.equal(root.get("status"), f.status()));
                }
                if (f.borrowStatus() != null && !f.borrowStatus().isEmpty()) {
                    predicates.add(cb.equal(root.get("borrowStatus"), f.borrowStatus()));
                }
                if (f.caType() != null && !f.caType().isEmpty()) {
                    predicates.add(cb.equal(root.get("caType"), f.caType()));
                }
                if (f.sealType() != null && !f.sealType().isEmpty()) {
                    predicates.add(cb.equal(root.get("sealType"), f.sealType()));
                }
                if (f.keyword() != null && !f.keyword().isEmpty()) {
                    String pattern = "%" + f.keyword() + "%";
                    predicates.add(cb.or(
                            cb.like(root.get("holderName"), pattern),
                            cb.like(root.get("issuer"), pattern),
                            cb.like(root.get("custodianName"), pattern)
                    ));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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

    private void writeDataRows(Sheet sheet, List<CaCertificateEntity> data) {
        // 批量预加载关联平台 ID，避免 N+1 查询
        Map<Long, List<Long>> platformMap = loadPlatformMap(data);

        for (int i = 0; i < data.size(); i++) {
            CaCertificateEntity e = data.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;
            row.createCell(col++).setCellValue(label(CA_TYPE_LABEL, e.getCaType()));
            row.createCell(col++).setCellValue(label(SEAL_TYPE_LABEL, e.getSealType()));
            row.createCell(col++).setCellValue(nullSafe(e.getHolderName()));
            row.createCell(col++).setCellValue(nullSafe(e.getCustodianName()));
            row.createCell(col++).setCellValue(formatDate(e.getExpiryDate()));
            row.createCell(col++).setCellValue(nullSafe(e.getIssuer()));
            row.createCell(col++).setCellValue(nullSafe(e.getElectronicAccount()));
            row.createCell(col++).setCellValue(decryptPassword(e.getCaPassword()));
            row.createCell(col++).setCellValue(nullSafe(e.getCaPlatformUrl()));
            row.createCell(col++).setCellValue(joinPlatformIds(platformMap.get(e.getId())));
            row.createCell(col++).setCellValue(label(BORROW_STATUS_LABEL, e.getBorrowStatus()));
            row.createCell(col++).setCellValue(label(STATUS_LABEL, e.getStatus()));
            row.createCell(col).setCellValue(nullSafe(e.getRemarks()));
        }
    }

    /**
     * 批量加载所有 CA 的关联平台 ID，避免每行 N+1 查询。
     * 返回 Map<caId, List<platformAccountId>>。
     */
    private Map<Long, List<Long>> loadPlatformMap(List<CaCertificateEntity> data) {
        if (data.isEmpty()) return Collections.emptyMap();
        List<Long> caIds = data.stream().map(CaCertificateEntity::getId).toList();
        return platformLinkRepository.findByCaCertificateIdIn(caIds).stream()
                .collect(Collectors.groupingBy(
                        CaCertificatePlatformEntity::getCaCertificateId,
                        Collectors.mapping(
                                CaCertificatePlatformEntity::getPlatformAccountId,
                                Collectors.toList())));
    }

    private String decryptPassword(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        try {
            return passwordEncryptionUtil.decrypt(stored);
        } catch (RuntimeException e) {
            // 解密失败不中断整个导出，返回占位符以便定位问题数据
            return "******";
        }
    }

    private String joinPlatformIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String label(Map<String, String> map, String key) {
        if (key == null) return "";
        return map.getOrDefault(key, key);
    }

    private String formatDate(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    /** 导出筛选条件（与 list 端点字段对齐）。 */
    public record CaExportFilters(
            String status,
            String borrowStatus,
            String keyword,
            String caType,
            String sealType
    ) {}
}
