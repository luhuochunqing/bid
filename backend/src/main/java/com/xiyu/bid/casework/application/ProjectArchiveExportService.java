package com.xiyu.bid.casework.application;
import lombok.extern.slf4j.Slf4j;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 归档项目台账导出服务，归属 casework 模块，避免与 export 模块形成循环依赖。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectArchiveExportService {

    private final ProjectArchiveRepository projectArchiveRepository;
    private final ArchiveFileRepository archiveFileRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ExportConfig exportConfig;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;

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
                List<ProjectArchive> pageContent = page.getContent();

                // CO-421: 按页批量预加载，消除 N+1 查询
                // 改造前：每条 archive 调 4~6 次 findById（project/tender/lead/user/files）
                // 改造后：每页 4 次批量查询，循环内只做内存 Map 查找
                List<ProjectArchive> exportableArchives = pageContent.stream()
                        .filter(a -> canExportArchive(a, exportableProjectIds))
                        .toList();
                PagePrefetch prefetch = prefetchPageData(exportableArchives);

                for (ProjectArchive archive : exportableArchives) {
                    if (recordCount >= exportConfig.getMaxRecords()) {
                        break;
                    }

                    EnrichedFields fields = enrichArchive(archive, prefetch);

                    List<ArchiveFile> files = archiveFileRepository.findByArchiveId(archive.getId());
                    int fileCount = files.size();

                    Row row = mainSheet.createRow(mainRowNum++);
                    row.createCell(0).setCellValue(safeString(archive.getProjectName()));
                    row.createCell(1).setCellValue(fields.tenderAgency);
                    row.createCell(2).setCellValue(fields.projectType);
                    row.createCell(3).setCellValue(safeString(fields.projectStatus));
                    row.createCell(4).setCellValue(fields.bidResult);
                    row.createCell(5).setCellValue(fields.projectManager);
                    row.createCell(6).setCellValue(fields.bidManager);
                    row.createCell(7).setCellValue(fields.initiatedAtStr);
                    row.createCell(8).setCellValue(fields.bidSubmissionAtStr);
                    row.createCell(9).setCellValue(fields.bidOpeningAtStr);
                    row.createCell(10).setCellValue(fields.closedAtStr);
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

            ExcelAutoSizeHelper.autoSizeColumns(mainSheet, mainHeaders.length);
            ExcelAutoSizeHelper.autoSizeColumns(detailSheet, detailHeaders.length);
            workbook.write(out);
            return new ArchiveExportResult(out.toByteArray(), recordCount);
        }
    }

    private boolean canExportArchive(ProjectArchive archive, Set<Long> exportableProjectIds) {
        return exportableProjectIds == null
                || archive != null && archive.getProjectId() != null && exportableProjectIds.contains(archive.getProjectId());
    }

    /** 按页批量预加载 Project / Tender / ProjectInitiationDetails，避免循环内 N+1 */
    private PagePrefetch prefetchPageData(List<ProjectArchive> exportableArchives) {
        if (exportableArchives.isEmpty()) {
            return PagePrefetch.empty();
        }
        List<Long> projectIds = exportableArchives.stream()
                .map(ProjectArchive::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Project> projectMap = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        List<Long> tenderIds = projectMap.values().stream()
                .map(Project::getTenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Tender> tenderMap = tenderIds.isEmpty()
                ? Collections.emptyMap()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(Collectors.toMap(Tender::getId, t -> t));

        // CO-421: 投标负责人姓名读 ProjectInitiationDetails.biddingLeaderName（批量查询）
        Map<Long, String> bidManagerNameByProjectId = initiationDetailsRepository
                .findByProjectIdIn(projectIds).stream()
                .filter(d -> d.getBiddingLeaderName() != null && !d.getBiddingLeaderName().isBlank())
                .collect(Collectors.toMap(ProjectInitiationDetails::getProjectId,
                        ProjectInitiationDetails::getBiddingLeaderName, (a, b) -> a));

        return new PagePrefetch(projectMap, tenderMap, bidManagerNameByProjectId);
    }

    private EnrichedFields enrichArchive(ProjectArchive archive, PagePrefetch prefetch) {
        EnrichedFields f = new EnrichedFields();
        Project p = prefetch.projectMap.get(archive.getProjectId());
        if (p == null) {
            return f;
        }
        try {
            f.projectStatus = p.getStatus().name();
            f.bidResult = switch (p.getStatus()) {
                case WON -> "已中标";
                case LOST -> "未中标";
                case FAILED -> "已流标";
                case ABANDONED -> "已放弃";
                default -> "进行中";
            };
            Tender t = prefetch.tenderMap.get(p.getTenderId());
            if (t != null) {
                f.projectType = safeString(t.getProjectType());
                f.projectManager = safeString(t.getProjectManagerName());
                f.tenderAgency = safeString(t.getPurchaserName());
                if (t.getCreatedAt() != null) f.initiatedAtStr = t.getCreatedAt().format(DATE_FORMATTER);
                if (t.getBidOpeningTime() != null) f.bidOpeningAtStr = t.getBidOpeningTime().format(DATE_FORMATTER);
            }
            // CO-421: 投标负责人从 ProjectInitiationDetails.biddingLeaderName 取（批量预加载）
            String name = prefetch.bidManagerNameByProjectId.get(p.getId());
            f.bidManager = name != null ? name : "";
        } catch (RuntimeException ignored) {
            log.debug("Export enrich failed for archive {}", archive.getId(), ignored);
        }
        return f;
    }

    /** 单页预加载数据容器 */
    private record PagePrefetch(
            Map<Long, Project> projectMap,
            Map<Long, Tender> tenderMap,
            Map<Long, String> bidManagerNameByProjectId
    ) {
        static PagePrefetch empty() {
            return new PagePrefetch(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    /** 单条 archive enrich 后的字段容器，避免多参数传递 */
    private static class EnrichedFields {
        String projectType = "综合";
        String projectStatus = "PENDING_INITIATION";
        String bidResult = "其他";
        String projectManager = "-";
        String bidManager = "-";
        String tenderAgency = "-";
        String initiatedAtStr = "";
        String bidSubmissionAtStr = "";
        String bidOpeningAtStr = "";
        String closedAtStr = "";
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
