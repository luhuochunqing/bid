// Input: MultipartFile（.xlsx）+ 上传用户上下文
// Output: TenderImportResultDTO（成功）/ TenderImportRollbackException（任一行不合法）
// Pos: service/标讯批量导入用例
// 维护声明: HEADERS / REGIONS / CUSTOMER_TYPES / PRIORITIES / PROJECT_TYPES 与 TenderImportTemplateBuilder 共享，调整需同步前端 BulkImportDialog 与说明文案。

package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import com.xiyu.bid.tender.dto.TenderImportResultDTO.RowError;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.core.TenderRegionCatalog;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 标讯批量导入：模板生成 + Excel 解析 + 单条入库 + 全量回滚。
 * <p>校验策略：先解析整张表 → 累计行级错误 → 错误为空时逐条 {@link TenderCommandService#createTender(com.xiyu.bid.tender.dto.TenderDTO)}；
 * 否则抛 {@link TenderImportRollbackException} 触发整批回滚。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderImportService {

    static final String[] HEADERS = {
            "项目名称*", "招标主体*", "总部所在地*",
            "报名截止时间*", "开标时间*",
            "联系人1*", "联系人1手机号", "联系人1座机", "联系人1邮箱",
            "联系人2", "联系人2手机号", "联系人2座机", "联系人2邮箱",
            "客户类型", "优先级", "项目类型", "来源平台", "标讯描述"
    };

    static final List<String> CUSTOMER_TYPES = List.of(
            "政府机关/事业单位/高校", "央企", "地方国企", "民企", "港澳台及外企");
    static final List<String> PRIORITIES = List.of("S", "A", "B", "C");
    static final List<String> REGIONS = TenderRegionCatalog.REGIONS;

    static final List<String> PROJECT_TYPES = List.of("工业品", "办公", "综合", "集采", "其他");

    private static final int MAX_ROWS = 500;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;


    private final TenderCommandService tenderCommandService;
    private final TenderMapper tenderMapper;
    private final TenderImportTemplateBuilder templateBuilder;
    private final TenderExcelCellReader cellReader;
    private final Validator validator;

    public byte[] generateTemplate() {
        return templateBuilder.build();
    }

    @Transactional
    public TenderImportResultDTO importFromExcel(MultipartFile file, Long userId) {
        validateFile(file);
        List<RowError> errors = new ArrayList<>();
        List<TenderRequest> rows = new ArrayList<>();
        int totalRows;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);
            totalRows = collectRows(sheet, rows, errors);
        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 解析失败：" + e.getMessage(), e);
        }

        if (!errors.isEmpty()) {
            log.info("标讯批量导入校验未通过 totalRows={} failureCount={}", totalRows, errors.size());
            throw new TenderImportRollbackException(TenderImportResultDTO.builder()
                    .totalRows(totalRows)
                    .successCount(0)
                    .failureCount(errors.size())
                    .errors(List.copyOf(errors))
                    .build());
        }

        rows.forEach(req -> tenderCommandService.createTender(tenderMapper.toDTO(req), userId));
        log.info("标讯批量导入完成 totalRows={}", totalRows);
        return TenderImportResultDTO.builder()
                .totalRows(totalRows)
                .successCount(totalRows)
                .failureCount(0)
                .errors(List.of())
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传导入文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("导入文件不能超过 5MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 模板，请使用下载的模板");
        }
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet == null ? null : sheet.getRow(0);
        if (header == null) {
            throw new IllegalArgumentException("模板表头不匹配，请使用最新模板");
        }
        for (int i = 0; i < HEADERS.length; i++) {
            String actual = cellReader.readString(header.getCell(i));
            if (actual == null || !normalizeHeader(HEADERS[i]).equals(normalizeHeader(actual))) {
                throw new IllegalArgumentException("模板表头不匹配，请使用最新模板");
            }
        }
    }

    /**
     * 规范化表头字符串：去空格、统一全角符号为半角、转小写、去除末尾 * 标记。
     */
    static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replace('（', '(').replace('）', ')')
             .replace('：', ':').replace('，', ',')
             .replace('、', ',').replace('；', ';')
             .replace('！', '!').replace('？', '?');
        s = s.replaceAll("\\*+$", "");
        return s.toLowerCase();
    }

    private int collectRows(Sheet sheet, List<TenderRequest> rows, List<RowError> errors) {
        int last = sheet.getLastRowNum();
        int count = 0;
        for (int i = 1; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) continue;
            count++;
            if (count > MAX_ROWS) {
                throw new IllegalArgumentException("单次导入最多 " + MAX_ROWS + " 行");
            }
            int displayRow = i + 1;
            try {
                TenderRequest request = parseRow(row);
                rows.add(request);
                runValidation(displayRow, request, errors);
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(displayRow, "row", e.getMessage()));
            }
        }
        return count;
    }

    private TenderRequest parseRow(Row row) {
        TenderRequest req = new TenderRequest();
        req.setTitle(cellReader.readString(row.getCell(0)));
        req.setTenderAgency(cellReader.readString(row.getCell(1)));
        req.setPurchaserName(cellReader.readString(row.getCell(1)));
        req.setRegion(cellReader.readString(row.getCell(2)));
        req.setRegistrationDeadline(cellReader.readDateTime(row.getCell(3), "报名截止时间"));
        req.setBidOpeningTime(cellReader.readDateTime(row.getCell(4), "开标时间"));
        req.setContactName(cellReader.readString(row.getCell(5)));
        req.setContactPhone(cellReader.readString(row.getCell(6)));
        req.setContactTel(cellReader.readString(row.getCell(7)));
        req.setContactMail(cellReader.readString(row.getCell(8)));
        req.setContactName2(cellReader.readString(row.getCell(9)));
        req.setContactPhone2(cellReader.readString(row.getCell(10)));
        req.setContactTel2(cellReader.readString(row.getCell(11)));
        req.setContactMail2(cellReader.readString(row.getCell(12)));
        req.setCustomerType(cellReader.readString(row.getCell(13)));
        req.setPriority(cellReader.readString(row.getCell(14)));
        req.setProjectType(cellReader.readString(row.getCell(15)));
        req.setSourcePlatform(cellReader.readString(row.getCell(16)));
        req.setDescription(cellReader.readString(row.getCell(17)));
        req.setSource(com.xiyu.bid.entity.Tender.SourceType.BULK_IMPORT.getLabel());
        req.setSourceType(com.xiyu.bid.entity.Tender.SourceType.BULK_IMPORT);
        req.setPublishDate(LocalDate.now());
        /* For imported tenders, registration deadline serves as the general deadline */
        if (req.getRegistrationDeadline() != null && req.getDeadline() == null) {
            req.setDeadline(req.getRegistrationDeadline());
        }
        return req;
    }

    private void runValidation(int displayRow, TenderRequest request, List<RowError> errors) {
        Set<String> explicitFields = new HashSet<>();
        /* Required field checks (per blueprint) */
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            errors.add(new RowError(displayRow, "title", "项目名称不能为空"));
            explicitFields.add("title");
        }
        if (request.getPurchaserName() == null || request.getPurchaserName().isBlank()) {
            errors.add(new RowError(displayRow, "purchaserName", "招标主体不能为空"));
            explicitFields.add("purchaserName");
        }
        if (request.getRegion() == null || request.getRegion().isBlank()) {
            errors.add(new RowError(displayRow, "region", "总部所在地不能为空"));
            explicitFields.add("region");
        }
        if (request.getRegistrationDeadline() == null) {
            errors.add(new RowError(displayRow, "registrationDeadline", "报名截止时间不能为空"));
            explicitFields.add("registrationDeadline");
        }
        if (request.getBidOpeningTime() == null) {
            errors.add(new RowError(displayRow, "bidOpeningTime", "开标时间不能为空"));
            explicitFields.add("bidOpeningTime");
        }
        if (request.getContactName() == null || request.getContactName().isBlank()) {
            errors.add(new RowError(displayRow, "contactName", "联系人1不能为空"));
            explicitFields.add("contactName");
        }

        Set<ConstraintViolation<TenderRequest>> violations = validator.validate(request);
        for (ConstraintViolation<TenderRequest> v : violations) {
            if (!explicitFields.contains(v.getPropertyPath().toString())) {
                errors.add(new RowError(displayRow, v.getPropertyPath().toString(), v.getMessage()));
            }
        }
        if (request.getRegion() != null && !TenderRegionCatalog.isValid(request.getRegion())) {
            errors.add(new RowError(displayRow, "region",
                    "总部所在地须为省+市格式（直辖市为\"市-市\"格式，如\"北京市-北京市\"；普通省为\"广东省深圳市\"）"));
        }
        if (request.getCustomerType() != null && !CUSTOMER_TYPES.contains(request.getCustomerType())) {
            errors.add(new RowError(displayRow, "customerType",
                    "客户类型必须是：" + String.join(" / ", CUSTOMER_TYPES)));
        }
        if (request.getPriority() != null && !PRIORITIES.contains(request.getPriority())) {
            errors.add(new RowError(displayRow, "priority", "优先级必须是 S/A/B/C 之一"));
        }
        if (request.getProjectType() != null && !PROJECT_TYPES.contains(request.getProjectType())) {
            errors.add(new RowError(displayRow, "projectType",
                    "项目类型必须是：" + String.join(" / ", PROJECT_TYPES)));
        }
    }

    private boolean isBlankRow(Row row) {
        if (row == null) return true;
        for (int i = 0; i < HEADERS.length; i++) {
            String value = cellReader.readString(row.getCell(i));
            if (value != null && !value.isBlank()) return false;
        }
        return true;
    }




}
