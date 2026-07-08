// Input: MultipartFile（.xlsx）+ 上传用户上下文
// Output: TenderImportResultDTO（成功）/ TenderImportRollbackException（任一行不合法）
// Pos: service/标讯批量导入用例
// 维护声明: HEADERS / REGIONS / CUSTOMER_TYPES / PRIORITIES / PROJECT_TYPES 真相源已移至 {@link TenderExcelParser}，本类仅 re-export 兼容现有调用方。

package com.xiyu.bid.tender.service;

import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import com.xiyu.bid.tender.dto.TenderImportResultDTO.RowError;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.core.TenderDeduplicationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 标讯批量导入：模板生成 + Excel 解析委托 + 单条入库 + 全量回滚。
 *
 * <p>校验策略：先解析整张表 → 累计行级错误 → 错误为空时逐条 {@link TenderCommandService#createTender(com.xiyu.bid.tender.dto.TenderDTO)}；
 * 否则抛 {@link TenderImportRollbackException} 触发整批回滚。
 *
 * <p>CO-508 line-budget 治理：Excel 解析逻辑已拆分到 {@link TenderExcelParser}，本类仅保留同步入库编排。
 * 常量（HEADERS/REGIONS/CUSTOMER_TYPES/PRIORITIES/PROJECT_TYPES）和 {@link #normalizeHeader} 的真相源
 * 也已移至 {@link TenderExcelParser}，本类通过 static 字段 re-export 以兼容现有调用方
 * （{@link TenderImportTemplateBuilder}、TenderImportServiceTest），后续可逐步迁移。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderImportService {

    /** @deprecated 改用 {@link TenderExcelParser#HEADERS} */
    @Deprecated
    static final String[] HEADERS = TenderExcelParser.HEADERS;
    /** @deprecated 改用 {@link TenderExcelParser#CUSTOMER_TYPES} */
    @Deprecated
    static final List<String> CUSTOMER_TYPES = TenderExcelParser.CUSTOMER_TYPES;
    /** @deprecated 改用 {@link TenderExcelParser#PRIORITIES} */
    @Deprecated
    static final List<String> PRIORITIES = TenderExcelParser.PRIORITIES;
    /** @deprecated 改用 {@link TenderExcelParser#REGIONS} */
    @Deprecated
    static final List<String> REGIONS = TenderExcelParser.REGIONS;
    /** @deprecated 改用 {@link TenderExcelParser#PROJECT_TYPES} */
    @Deprecated
    static final List<String> PROJECT_TYPES = TenderExcelParser.PROJECT_TYPES;

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final TenderCommandService tenderCommandService;
    private final TenderMapper tenderMapper;
    private final TenderImportTemplateBuilder templateBuilder;
    private final TenderExcelParser excelParser;

    public byte[] generateTemplate() {
        return templateBuilder.build();
    }

    @Transactional
    public TenderImportResultDTO importFromExcel(MultipartFile file, Long userId) {
        validateFile(file);
        TenderExcelParser.ParsedExcel parsed;
        try {
            parsed = excelParser.parseExcel(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 解析失败：" + e.getMessage(), e);
        }

        if (!parsed.errors().isEmpty()) {
            log.info("标讯批量导入校验未通过 totalRows={} failureCount={}", parsed.totalRows(), parsed.errors().size());
            throw new TenderImportRollbackException(TenderImportResultDTO.builder()
                    .totalRows(parsed.totalRows())
                    .successCount(0)
                    .failureCount(parsed.errors().size())
                    .errors(List.copyOf(parsed.errors()))
                    .build());
        }

        List<RowError> importErrors = new ArrayList<>();
        for (int i = 0; i < parsed.rows().size(); i++) {
            TenderRequest req = parsed.rows().get(i);
            int displayRow = i + 2;
            try {
                tenderCommandService.createTender(tenderMapper.toDTO(req), userId);
            } catch (TenderDuplicateException e) {
                var existing = (e.getDuplicates() == null || e.getDuplicates().isEmpty())
                        ? null : e.getDuplicates().get(0);
                importErrors.add(new RowError(displayRow, "duplicate",
                        TenderDeduplicationPolicy.formatImportDuplicateMessage(
                                existing,
                                req.getPurchaserName())));
            } catch (IllegalArgumentException e) {
                importErrors.add(new RowError(displayRow, "row", e.getMessage()));
            } catch (RuntimeException e) {
                importErrors.add(new RowError(displayRow, "row", "导入失败：" + e.getMessage()));
            }
        }

        if (!importErrors.isEmpty()) {
            log.info("标讯批量导入执行失败 totalRows={} failureCount={}", parsed.totalRows(), importErrors.size());
            throw new TenderImportRollbackException(TenderImportResultDTO.builder()
                    .totalRows(parsed.totalRows())
                    .successCount(0)
                    .failureCount(importErrors.size())
                    .errors(List.copyOf(importErrors))
                    .build());
        }

        log.info("标讯批量导入完成 totalRows={}", parsed.totalRows());
        return TenderImportResultDTO.builder()
                .totalRows(parsed.totalRows())
                .successCount(parsed.totalRows())
                .failureCount(0)
                .errors(List.of())
                .build();
    }

    public void validateFile(MultipartFile file) {
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

    /** @deprecated 改用 {@link TenderExcelParser#normalizeHeader(String)} */
    @Deprecated
    static String normalizeHeader(String raw) {
        return TenderExcelParser.normalizeHeader(raw);
    }
}
