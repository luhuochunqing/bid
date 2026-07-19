// Output: Excel export file data and filename for project list downloads
// Pos: Service/导出专用

package com.xiyu.bid.project.service;

import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import com.xiyu.bid.project.dto.ProjectDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 项目列表 Excel 导出服务。
 *
 * CO-553: 数据源复用 {@link ProjectQueryService#getAllProjects()}，与列表接口
 * GET /api/projects 同源，确保导出字段与系统显示完全一致。枚举字段（项目状态、
 * 项目类型、客户类型、优先级、项目阶段、来源平台）的中文映射与前端
 * src/views/Project/utils/projectListFormatters.js 及 project-utils.js 同源。
 */
@Service
@RequiredArgsConstructor
public class ProjectExportService {

    private final ProjectQueryService projectQueryService;

    private static final int MAX_EXPORT_ROWS = 5000;

    // ── 枚举中文映射（与前端 projectListFormatters.js / project-utils.js 同源）──
    // 未知值 fallback 显示原值，避免丢失数据（与前端 map[v] || v 行为一致）。

    /** 项目状态 bidStatus → 中文（同 project-utils.js PROJECT_STATUS_TEXT，与 Project.Status.displayName 同源）。 */
    private static final Map<String, String> BID_STATUS_LABELS = Map.of(
            "PENDING_INITIATION", "待立项",
            "INITIATED", "已立项",
            "BIDDING", "投标中",
            "EVALUATING", "评标中",
            "WON", "已中标",
            "LOST", "未中标",
            "FAILED", "已流标",
            "ABANDONED", "已放弃");

    /** 项目类型 projectType → 中文（同 projectListFormatters.js PROJECT_TYPE_LABELS）。 */
    private static final Map<String, String> PROJECT_TYPE_LABELS = Map.of(
            "OFFICE", "办公",
            "COMPREHENSIVE", "综合",
            "COLLECTIVE", "集采",
            "INDUSTRIAL", "工业品",
            "OTHER", "其他");

    /** 客户类型 customerType → 中文（同 projectListFormatters.js CUSTOMER_TYPE_LABELS）。 */
    private static final Map<String, String> CUSTOMER_TYPE_LABELS = Map.of(
            "GOVERNMENT", "政府机关/事业单位/高校",
            "CENTRAL_SOE", "央企",
            "LOCAL_SOE", "地方国企",
            "PRIVATE", "民企",
            "FOREIGN", "港澳台及外企",
            "OTHER", "其他");

    /** 项目阶段 stage → 中文（同 projectListFormatters.js stageText）。 */
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "INITIATED", "项目立项",
            "DRAFTING", "标书制作",
            "EVALUATING", "评标中",
            "RESULT_PENDING", "结果确认",
            "RETROSPECTIVE", "项目复盘",
            "CLOSED", "项目结项");

    /** 来源平台 sourceModule → 中文（同 projectListFormatters.js sourceText）。 */
    private static final Map<String, String> SOURCE_LABELS = Map.ofEntries(
            Map.entry("CRM_OPPORTUNITY", "CRM创建"),
            Map.entry("EXTERNAL_PLATFORM", "第三方平台"),
            Map.entry("MANUAL_SINGLE", "人工录入"),
            // BULK_IMPORT 前端归入"人工录入"（与 sourceText 一致）
            Map.entry("BULK_IMPORT", "人工录入"),
            // 历史数据兼容：后端已存储的中文标签直接透传
            Map.entry("CRM创建", "CRM创建"),
            Map.entry("第三方平台", "第三方平台"),
            Map.entry("人工录入", "人工录入"),
            Map.entry("批量导入", "人工录入"),
            // 历史数据兼容：旧版带空格标签
            Map.entry("CRM 创建", "CRM创建"));

    /** 优先级 priority → 中文（同 projectListFormatters.js priorityLabel，后端已归一化为 S/A/B/C）。 */
    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "S", "S级",
            "A", "A级",
            "B", "B级",
            "C", "C级");

    /** CO-591: 评标结果 evaluationSubStage → 中文（同前端 EvaluationStatusPanel.vue SUB_STAGE_LABELS）。 */
    private static final Map<String, String> EVALUATION_SUB_STAGE_LABELS = Map.of(
            "IN_PROGRESS", "评标中",
            "AWAITING_BOARD", "评标结果已出，待上会",
            "RESULT_OUT", "评标结果已出",
            "ANNOUNCED", "评标结果公示");

    public ExportResult exportProjectsAsExcel(
            List<Long> ids, String status, String name, String ownerUnit, String projectType,
            String customerType, String priority, String sourceModule, String bidStatus,
            String stage, Long projectLeaderId, Long biddingLeaderId,
            String projectLeaderName, String biddingLeaderName, String leaderDepartment,
            String region, String biddingPlatform, String bidMonth) {

        // CO-553: 复用列表接口数据源（ProjectQueryService.getAllProjects），
        // 确保导出字段与 GET /api/projects 返回完全一致（含 secondaryBiddingLeaderName、
        // revenue、shortlistedCount、priority、stage 等），同时复用 access scope 过滤。
        List<ProjectDTO> all = projectQueryService.getAllProjects();

        // CO-563: 选中数据时仅导出对应 ID；未传 ids 时导出全量（与列表过滤行为一致）。
        if (ids != null && !ids.isEmpty()) {
            all = all.stream().filter(p -> ids.contains(p.getId())).toList();
        }

        // ── 内存过滤（与 ProjectController.getAllProjects 过滤逻辑保持一致）──
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(p -> status.equalsIgnoreCase(p.getStage())).toList();
        }
        if (name != null && !name.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getName(), name)
                    || containsIgnoreCase(p.getCustomer(), name)).toList();
        }
        if (ownerUnit != null && !ownerUnit.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getOwnerUnit(), ownerUnit)).toList();
        }
        if (projectType != null && !projectType.isBlank()) {
            all = all.stream().filter(p -> projectType.equals(p.getProjectType())).toList();
        }
        if (customerType != null && !customerType.isBlank()) {
            all = all.stream().filter(p -> customerType.equals(p.getCustomerType())).toList();
        }
        if (priority != null && !priority.isBlank()) {
            all = all.stream().filter(p -> priority.equals(p.getPriority())).toList();
        }
        if (sourceModule != null && !sourceModule.isBlank()) {
            all = all.stream().filter(p -> sourceModule.equals(p.getSourceModule())).toList();
        }
        if (bidStatus != null && !bidStatus.isBlank()) {
            all = all.stream().filter(p -> bidStatus.equals(p.getBidStatus())).toList();
        }
        if (stage != null && !stage.isBlank()) {
            all = all.stream().filter(p -> stage.equals(p.getStage())).toList();
        }
        if (projectLeaderId != null) {
            all = all.stream().filter(p -> projectLeaderId.equals(p.getProjectLeaderId())).toList();
        }
        if (biddingLeaderId != null) {
            all = all.stream().filter(p -> biddingLeaderId.equals(p.getBiddingLeaderId())).toList();
        }
        if (projectLeaderName != null && !projectLeaderName.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getProjectLeaderName(), projectLeaderName)).toList();
        }
        if (biddingLeaderName != null && !biddingLeaderName.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getBiddingLeaderName(), biddingLeaderName)).toList();
        }
        if (leaderDepartment != null && !leaderDepartment.isBlank()) {
            all = all.stream().filter(p -> leaderDepartment.equals(p.getLeaderDepartment())).toList();
        }
        if (region != null && !region.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getRegion(), region)).toList();
        }
        if (biddingPlatform != null && !biddingPlatform.isBlank()) {
            all = all.stream().filter(p -> containsIgnoreCase(p.getBiddingPlatform(), biddingPlatform)).toList();
        }
        if (bidMonth != null && !bidMonth.isBlank()) {
            all = all.stream().filter(p -> bidMonth.equals(p.getBidMonth())).toList();
        }

        if (all.size() > MAX_EXPORT_ROWS) {
            all = all.subList(0, MAX_EXPORT_ROWS);
        }

        // ── 列定义：与前端 List.vue 表格列完全对齐（23 列，不含序号/选择列）──
        // CO-591: 新增 4 列（项目服务周期（年）、服务周期截止时间、标书审核人、评标结果）
        String[] cols = {
                "项目名称", "项目状态", "来源平台", "招标主体", "计划入围供应商数量",
                "创建时间", "开标时间", "投标月份",
                "项目服务周期（年）", "服务周期截止时间",
                "项目类型", "客户营收（亿）",
                "客户类型", "优先级", "总部所在地", "项目负责人", "项目负责人部门",
                "投标负责人", "投标辅助人员", "标书审核人",
                "项目阶段", "评标结果", "投标平台"
        };

        var wb = new XSSFWorkbook();
        var sheet = wb.createSheet("投标项目列表");
        var header = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

        var df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int r = 1;
        for (ProjectDTO p : all) {
            var row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(coalesce(p.getName()));
            row.createCell(c++).setCellValue(mapOrOriginal(BID_STATUS_LABELS, p.getBidStatus()));
            row.createCell(c++).setCellValue(mapOrOriginal(SOURCE_LABELS, p.getSourceModule()));
            row.createCell(c++).setCellValue(coalesce(p.getOwnerUnit()));
            row.createCell(c++).setCellValue(p.getShortlistedCount() != null ? p.getShortlistedCount() : 0);
            row.createCell(c++).setCellValue(p.getCreatedAt() != null ? p.getCreatedAt().format(df) : "");
            row.createCell(c++).setCellValue(p.getBidOpenTime() != null ? p.getBidOpenTime().format(df) : "");
            row.createCell(c++).setCellValue(coalesce(p.getBidMonth()));
            row.createCell(c++).setCellValue(p.getServicePeriodYears() != null ? p.getServicePeriodYears().toPlainString() : "");
            row.createCell(c++).setCellValue(p.getServicePeriodEndDate() != null ? p.getServicePeriodEndDate().toString() : "");
            row.createCell(c++).setCellValue(mapOrOriginal(PROJECT_TYPE_LABELS, p.getProjectType()));
            row.createCell(c++).setCellValue(formatRevenue(p.getRevenue()));
            row.createCell(c++).setCellValue(mapOrOriginal(CUSTOMER_TYPE_LABELS, p.getCustomerType()));
            row.createCell(c++).setCellValue(mapOrOriginal(PRIORITY_LABELS, p.getPriority()));
            row.createCell(c++).setCellValue(coalesce(p.getRegion()));
            row.createCell(c++).setCellValue(coalesce(p.getProjectLeaderName()));
            row.createCell(c++).setCellValue(coalesce(p.getLeaderDepartment()));
            row.createCell(c++).setCellValue(coalesce(p.getBiddingLeaderName()));
            row.createCell(c++).setCellValue(coalesce(p.getSecondaryBiddingLeaderName()));
            row.createCell(c++).setCellValue(coalesce(p.getBidReviewers()));
            row.createCell(c++).setCellValue(mapOrOriginal(STAGE_LABELS, p.getStage()));
            row.createCell(c++).setCellValue(mapOrOriginal(EVALUATION_SUB_STAGE_LABELS, p.getEvaluationSubStage()));
            row.createCell(c).setCellValue(coalesce(p.getBiddingPlatform()));
        }

        ExcelAutoSizeHelper.autoSizeColumns(wb.getSheetAt(0), cols.length);
        try (var out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            var now = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            return new ExportResult(new ByteArrayInputStream(out.toByteArray()), now + ".xlsx");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to generate export Excel", e);
        }
    }

    public record ExportResult(java.io.InputStream data, String filename) {}

    private static String coalesce(String v) { return v != null ? v : ""; }

    private static boolean containsIgnoreCase(String source, String needle) {
        return source != null && needle != null && source.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * 枚举值 → 中文映射；未命中时 fallback 显示原值（与前端 map[v] || v 行为一致），
     * 避免历史/未知数据丢失。null/blank 返回空串。
     */
    private static String mapOrOriginal(Map<String, String> map, String value) {
        if (value == null || value.isBlank()) return "";
        String mapped = map.get(value);
        return mapped != null ? mapped : value;
    }

    /** revenue 格式化为 2 位小数（与前端 Number(revenue).toFixed(2) 一致）。 */
    private static String formatRevenue(BigDecimal revenue) {
        if (revenue == null) return "";
        return revenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
