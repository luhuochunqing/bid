package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 归档项目台账导出服务，归属 casework 模块，避免与 export 模块形成循环依赖。
 */
@Service
@RequiredArgsConstructor
public class ProjectArchiveExportService {

    private final ProjectArchiveRepository projectArchiveRepository;
    private final ArchiveFileRepository archiveFileRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ExportConfig exportConfig;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 1000;

    public record ArchiveExportResult(byte[] data, int recordCount) {}

    public ArchiveExportResult exportProjectArchivesByIds(Set<Long> projectIds) throws IOException {
        return exportProjectArchives(projectIds);
    }

    public Set<Long> resolveExportableProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        return projectAccessScopeService.filterAccessibleProjects(projectRepository.findAll()).stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public ArchiveExportResult exportProjectArchives(Set<Long> exportableProjectIds) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet mainSheet = workbook.createSheet("项目基本信息");
            Sheet detailSheet = workbook.createSheet("文件清单");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row mainHeaderRow = mainSheet.createRow(0);
            String[] mainHeaders = {"项目名称", "招标主体", "项目类型", "项目状态", "中标结果", "项目负责人", "投标负责人", "立项日期", "标书提交日期", "开标日期", "结项日期", "归档文件数"};
            for (int i = 0; i < mainHeaders.length; i++) {
                Cell cell = mainHeaderRow.createCell(i);
                cell.setCellValue(mainHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            Row detailHeaderRow = detailSheet.createRow(0);
            String[] detailHeaders = {"项目名", "文档分类", "文件名", "上传人", "上传时间", "文件大小"};
            for (int i = 0; i < detailHeaders.length; i++) {
                Cell cell = detailHeaderRow.createCell(i);
                cell.setCellValue(detailHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int mainRowNum = 1;
            int detailRowNum = 1;
            int recordCount = 0;
            int pageNumber = 0;
            boolean hasMoreData = true;

            while (hasMoreData && recordCount < exportConfig.getMaxRecords()) {
                Pageable pageable = PageRequest.of(pageNumber, DEFAULT_PAGE_SIZE);
                Page<ProjectArchive> page = projectArchiveRepository.findAll(pageable);

                for (ProjectArchive archive : page.getContent()) {
                    if (recordCount >= exportConfig.getMaxRecords()) {
                        break;
                    }
                    if (!canExportArchive(archive, exportableProjectIds)) {
                        continue;
                    }

                    // Enrich from Project/Tender for blueprint "基本信息" fields (similar to detail)
                    String projectType = "综合";
                    String bidResult = "其他";
                    String projectManager = "-";
                    String bidManager = "-";
                    String tenderAgency = "-";
                    String initiatedAtStr = "";
                    String bidSubmissionAtStr = "";
                    String bidOpeningAtStr = "";
                    String closedAtStr = "";
                    try {
                        Optional<Project> pOpt = projectRepository.findById(archive.getProjectId());
                        if (pOpt.isPresent()) {
                            Optional<Tender> tOpt = tenderRepository.findById(pOpt.get().getTenderId());
                            if (tOpt.isPresent()) {
                                Tender t = tOpt.get();
                                projectType = safeString(t.getProjectType());
                                if (Tender.Status.WON == t.getStatus()) bidResult = "中标";
                                else if (Tender.Status.LOST == t.getStatus()) bidResult = "未中标";
                                else bidResult = safeString(t.getStatus() != null ? t.getStatus().name() : "");
                                projectManager = safeString(t.getProjectManagerName());
                                bidManager = safeString(t.getBiddingPersonName());
                                tenderAgency = safeString(t.getTenderAgency());
                                if (t.getCreatedAt() != null) initiatedAtStr = t.getCreatedAt().format(DATE_FORMATTER);
                                if (t.getBidOpeningTime() != null) bidOpeningAtStr = t.getBidOpeningTime().format(DATE_FORMATTER);
                            }
                        }
                    } catch (Exception ignored) {}

                    List<ArchiveFile> files = archiveFileRepository.findByArchiveId(archive.getId());
                    int fileCount = files.size();

                    Row row = mainSheet.createRow(mainRowNum++);
                    row.createCell(0).setCellValue(safeString(archive.getProjectName()));
                    row.createCell(1).setCellValue(tenderAgency);
                    row.createCell(2).setCellValue(projectType);
                    row.createCell(3).setCellValue(safeString(archive.getArchiveStatus()));
                    row.createCell(4).setCellValue(bidResult);
                    row.createCell(5).setCellValue(projectManager);
                    row.createCell(6).setCellValue(bidManager);
                    row.createCell(7).setCellValue(initiatedAtStr);
                    row.createCell(8).setCellValue(bidSubmissionAtStr);
                    row.createCell(9).setCellValue(bidOpeningAtStr);
                    row.createCell(10).setCellValue(closedAtStr);
                    row.createCell(11).setCellValue(fileCount);

                    for (ArchiveFile file : files) {
                        String catLabel = getCategoryLabelForExport(file.getDocumentCategory());
                        Row dRow = detailSheet.createRow(detailRowNum++);
                        dRow.createCell(0).setCellValue(safeString(archive.getProjectName()));
                        dRow.createCell(1).setCellValue(catLabel);
                        dRow.createCell(2).setCellValue(safeString(file.getFileName()));
                        dRow.createCell(3).setCellValue(safeString(file.getUploadUserName()));
                        dRow.createCell(4).setCellValue(formatDateTime(file.getCreatedAt()));
                        dRow.createCell(5).setCellValue(file.getFileSize() != null ? file.getFileSize() : 0);
                    }

                    recordCount++;
                }

                hasMoreData = page.hasNext();
                pageNumber++;
            }

            autoSizeColumns(mainSheet, mainHeaders.length);
            autoSizeColumns(detailSheet, detailHeaders.length);
            workbook.write(out);
            return new ArchiveExportResult(out.toByteArray(), recordCount);
        }
    }

    private boolean canExportArchive(ProjectArchive archive, Set<Long> exportableProjectIds) {
        return exportableProjectIds == null
                || archive != null && archive.getProjectId() != null && exportableProjectIds.contains(archive.getProjectId());
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 2000) {
                sheet.setColumnWidth(i, 2000);
            }
            if (sheet.getColumnWidth(i) > 8000) {
                sheet.setColumnWidth(i, 8000);
            }
        }
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private String formatDateTime(LocalDateTime date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    private String getCategoryLabelForExport(String cat) {
        if (cat == null) return "其他";
        return switch (cat) {
            case "TENDER" -> "招标文件";
            case "BID" -> "标书文件";
            case "OPEN_LIST" -> "开标一览表";
            case "WIN_NOTICE" -> "中标通知书";
            case "DEPOSIT_RECEIPT" -> "保证金银行回单";
            default -> "其他";
        };
    }
}
