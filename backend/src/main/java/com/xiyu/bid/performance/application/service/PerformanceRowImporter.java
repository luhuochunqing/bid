package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerLevel;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseCustomerType;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseDockingMethod;
import static com.xiyu.bid.performance.application.service.PerformanceEnumLabels.parseProjectType;
import lombok.extern.slf4j.Slf4j;

/**
 * 业绩单行导入服务（独立事务，避免自调用代理陷阱）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceRowImporter {

    private final PerformanceRepository repository;
    private final CreatePerformanceAppService createService;
    private final UpdatePerformanceAppService updateService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE;

    // 列索引常量（与模板表头对齐）
    private static final int COL_CONTRACT_NAME = 0;
    private static final int COL_SIGNING_ENTITY = 1;
    private static final int COL_GROUP_COMPANY = 2;
    private static final int COL_CUSTOMER_TYPE = 3;
    private static final int COL_INDUSTRY = 4;
    private static final int COL_PROJECT_TYPE = 5;
    private static final int COL_DOCKING_METHOD = 6;
    private static final int COL_CUSTOMER_LEVEL = 7;
    private static final int COL_CONTAINS_XIYU = 8;
    private static final int COL_SIGNING_DATE = 9;
    private static final int COL_EXPIRY_DATE = 10;
    private static final int COL_TOTAL_EXPIRY_DATE = 11;
    private static final int COL_CONTACT_PERSON = 14;
    private static final int COL_CONTACT_INFO = 15;
    private static final int COL_TERRITORY = 16;
    private static final int COL_CUSTOMER_ADDRESS = 17;
    private static final int COL_XIYU_PROJECT_MANAGER = 18;
    private static final int COL_MALL_WEBSITE_URL = 20;
    private static final int COL_HAS_BID_NOTICE = 25;
    private static final int COL_REMARKS = 27;

    // 附件文件名列索引
    private static final int IDX_CONTRACT_AGREEMENT = 19;
    private static final int IDX_MALL_SCREENSHOT = 21;
    private static final int IDX_SOE_DIRECTORY = 22;
    private static final int IDX_CATEGORY_PAGE = 23;
    private static final int IDX_RELATIONSHIP_PROOF = 24;
    private static final int IDX_BID_NOTICE = 26;

    private static final String[] HEADER_NAMES = {
            "合同名称", "签约单位", "集团公司名称", "客户类型", "所属行业",
            "项目类型", "对接方式", "客户级别", "合同是否含西域",
            "签约日期", "截止日期", "总截止日期", "到期天数", "到期提醒",
            "客户联系人", "客户联系方式", "属地", "客户地址", "合同中西域项目负责人",
            "合同协议附件文件名", "客户商城网站网址", "商城对接截图附件文件名",
            "国资委央企名录截图附件文件名", "品类页附件文件名",
            "签约抬头与央企集团关系证明附件文件名",
            "是否有中标通知书", "中标通知书附件文件名", "备注"
    };

    private static String colLabel(int colIndex) {
        String colLetter = getColumnLetter(colIndex);
        String name = colIndex < HEADER_NAMES.length ? HEADER_NAMES[colIndex] : "第" + (colIndex + 1) + "列";
        return name + "（" + colLetter + "列）";
    }

    private static String getColumnLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        int n = colIndex;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    @Transactional
    public ImportRowResult importRow(Row row, int rowNum) {
        String contractName = getCellStr(row, COL_CONTRACT_NAME);
        if (contractName == null || contractName.isBlank()) {
            throw new IllegalArgumentException(colLabel(COL_CONTRACT_NAME) + "不能为空");
        }
        LocalDate signingDate = parseDate(getCellStr(row, COL_SIGNING_DATE), COL_SIGNING_DATE);
        LocalDate expiryDate = parseDate(getCellStr(row, COL_EXPIRY_DATE), COL_EXPIRY_DATE);
        if (signingDate != null && expiryDate != null && !expiryDate.isAfter(signingDate)) {
            throw new IllegalArgumentException(colLabel(COL_EXPIRY_DATE) + "须晚于" + colLabel(COL_SIGNING_DATE));
        }
        LocalDate totalExpiryDate = parseDate(getCellStr(row, COL_TOTAL_EXPIRY_DATE), COL_TOTAL_EXPIRY_DATE);
        if (expiryDate != null && totalExpiryDate != null && totalExpiryDate.isBefore(expiryDate)) {
            throw new IllegalArgumentException(colLabel(COL_TOTAL_EXPIRY_DATE) + "须晚于或等于" + colLabel(COL_EXPIRY_DATE));
        }

        var customerType = parseWithColLabel(() -> parseCustomerType(getCellStr(row, COL_CUSTOMER_TYPE)), COL_CUSTOMER_TYPE);
        var projectType = parseWithColLabel(() -> parseProjectType(getCellStr(row, COL_PROJECT_TYPE)), COL_PROJECT_TYPE);
        var dockingMethod = parseWithColLabel(() -> parseDockingMethod(getCellStr(row, COL_DOCKING_METHOD)), COL_DOCKING_METHOD);
        var customerLevel = parseWithColLabel(() -> parseCustomerLevel(getCellStr(row, COL_CUSTOMER_LEVEL)), COL_CUSTOMER_LEVEL);

        var attachments = collectAttachmentEntries(row);
        var cmd = new PerformanceUpsertCommand(
                contractName, getCellStr(row, COL_SIGNING_ENTITY), getCellStr(row, COL_GROUP_COMPANY),
                customerType, getCellStr(row, COL_INDUSTRY),
                projectType, dockingMethod, customerLevel,
                signingDate, expiryDate, totalExpiryDate,
                getCellStr(row, COL_CONTACT_PERSON), getCellStr(row, COL_CONTACT_INFO), getCellStr(row, COL_TERRITORY),
                getCellStr(row, COL_CUSTOMER_ADDRESS), getCellStr(row, COL_XIYU_PROJECT_MANAGER), getCellStr(row, COL_MALL_WEBSITE_URL),
                parseYesNo(getCellStr(row, COL_HAS_BID_NOTICE), COL_HAS_BID_NOTICE), getCellStr(row, COL_REMARKS),
                attachments);
        var existing = repository.findByContractName(contractName);
        Long performanceId;
        if (existing.isPresent()) {
            performanceId = updateService.update(existing.get().id(), cmd).id();
        } else {
            performanceId = createService.create(cmd).id();
        }
        return new ImportRowResult(contractName, performanceId, attachmentFileNames(row));
    }

    private <T> T parseWithColLabel(java.util.function.Supplier<T> parser, int colIndex) {
        try {
            return parser.get();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(colLabel(colIndex) + "：" + e.getMessage());
        }
    }

    private boolean parseYesNo(String value, int colIndex) {
        if (value == null || value.isBlank()) return false;
        if ("是".equals(value)) return true;
        if ("否".equals(value)) return false;
        throw new IllegalArgumentException(colLabel(colIndex) + "值无效：\"" + value + "\"，请填写\"是\"或\"否\"");
    }

    /**
     * 收集本行中填写的附件文件名条目（fileUrl 暂留空，由附件包归档器回填）。
     */
    private List<PerformanceUpsertCommand.AttachmentEntry> collectAttachmentEntries(Row row) {
        List<PerformanceUpsertCommand.AttachmentEntry> list = new ArrayList<>();
        addAttachmentEntry(list, getCellStr(row, IDX_CONTRACT_AGREEMENT), "CONTRACT_AGREEMENT");
        addAttachmentEntry(list, getCellStr(row, IDX_MALL_SCREENSHOT), "MALL_SCREENSHOT");
        addAttachmentEntry(list, getCellStr(row, IDX_SOE_DIRECTORY), "SOE_DIRECTORY");
        addAttachmentEntry(list, getCellStr(row, IDX_CATEGORY_PAGE), "CATEGORY_PAGE");
        addAttachmentEntry(list, getCellStr(row, IDX_RELATIONSHIP_PROOF), "RELATIONSHIP_PROOF");
        addAttachmentEntry(list, getCellStr(row, IDX_BID_NOTICE), "BID_NOTICE");
        return list;
    }

    private void addAttachmentEntry(List<PerformanceUpsertCommand.AttachmentEntry> list,
                                     String fileName, String fileType) {
        if (fileName == null || fileName.isBlank()) return;
        list.add(new PerformanceUpsertCommand.AttachmentEntry(fileName.trim(), "", fileType));
    }

    /**
     * 收集本行中所有附件文件名（供附件包归档器匹配使用）。
     */
    private List<AttachmentFileName> attachmentFileNames(Row row) {
        List<AttachmentFileName> names = new ArrayList<>();
        addFileName(names, getCellStr(row, IDX_CONTRACT_AGREEMENT), "CONTRACT_AGREEMENT");
        addFileName(names, getCellStr(row, IDX_MALL_SCREENSHOT), "MALL_SCREENSHOT");
        addFileName(names, getCellStr(row, IDX_SOE_DIRECTORY), "SOE_DIRECTORY");
        addFileName(names, getCellStr(row, IDX_CATEGORY_PAGE), "CATEGORY_PAGE");
        addFileName(names, getCellStr(row, IDX_RELATIONSHIP_PROOF), "RELATIONSHIP_PROOF");
        addFileName(names, getCellStr(row, IDX_BID_NOTICE), "BID_NOTICE");
        return names;
    }

    private void addFileName(List<AttachmentFileName> names, String fileName, String fileType) {
        if (fileName == null || fileName.isBlank()) return;
        names.add(new AttachmentFileName(fileName.trim(), fileType));
    }

    private String getCellStr(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    yield val == Math.rint(val) ? String.valueOf((long) val) : String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate parseDate(String s, int colIndex) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DTF); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(colLabel(colIndex) + "日期格式错误: \"" + s + "\"，应为 YYYY-MM-DD"); }
    }

    /** 单行导入结果：合同名、业绩 ID、本行附件文件名列表 */
    public record ImportRowResult(String contractName, Long performanceId,
                                   List<AttachmentFileName> attachmentFileNames) {}

    /** 附件文件名 + 类型 */
    public record AttachmentFileName(String fileName, String fileType) {}
}
